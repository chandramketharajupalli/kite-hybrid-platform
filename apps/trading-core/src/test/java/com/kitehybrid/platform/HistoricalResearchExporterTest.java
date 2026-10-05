package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.*;
import com.kitehybrid.platform.historical.application.HistoricalBarRepository;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.historical.infrastructure.HistoricalResearchExporter;
import com.kitehybrid.platform.instrument.domain.*;
import com.networknt.schema.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class HistoricalResearchExporterTest {
    static final Instrument SBIN=Instrument.create(new BrokerInstrumentId("SYNTHETIC","123"),"SBIN","NSE","CASH",
            InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    static final Instant START=Instant.parse("2026-07-30T03:45:00Z"), CUTOFF=Instant.parse("2026-08-01T00:00:00Z");
    static final UUID CHUNK=UUID.fromString("00000000-0000-0000-0000-000000000001");
    final ObjectMapper mapper=new ObjectMapper();
    final HistoricalBarRepository repository=mock(HistoricalBarRepository.class);
    final HistoricalResearchExporter exporter=new HistoricalResearchExporter(repository);
    final TradingCalendar calendar=new TradingCalendar("synthetic-v1","Synthetic five-minute session; not exchange evidence",
            Map.of(LocalDate.parse("2026-07-30"), new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
                    List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,20))))));
    final HistoricalWindow window=new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plusSeconds(300));
    final HistoricalBarRepository.Query query=new HistoricalBarRepository.Query(window,window.to(),CUTOFF);
    List<HistoricalBar> bars;
    HistoricalBarRepository.Dataset pinned;

    @BeforeEach void setup() {
        bars=new ArrayList<>();
        for(int i=0;i<5;i++) {
            var open=BigDecimal.valueOf(100+2L*i);
            bars.add(new HistoricalBar(SBIN.id(),START.plusSeconds(60L*i),BarInterval.MINUTE,open,
                    open.add(new BigDecimal("2")),open.subtract(BigDecimal.ONE),open.add(BigDecimal.ONE),1000+i,Optional.empty()));
        }
        pinned=new HistoricalBarRepository.Dataset(query,bars,List.of(CHUNK),HistoricalFingerprint.bars(bars));
        when(repository.replay(any())).thenCallRealMethod();
        when(repository.query(query)).thenReturn(pinned);
        when(repository.evidence(CHUNK)).thenReturn(Optional.of(new HistoricalBarRepository.Evidence(CHUNK,CHUNK,
                window,window,"SYNTHETIC","fixture-v1",CUTOFF,CUTOFF,window.to(),"a".repeat(64),calendar.fingerprint(),
                pinned.contentHash(),5,5)));
    }

    @Test void exporterMatchesPythonSharedFixtureAndSchemaWithoutWrites() throws Exception {
        var contracts=Path.of(System.getProperty("contracts.dir"));
        var expected=mapper.readTree(contracts.resolve("fixtures/v1/historical-research.synthetic.json").toFile());
        var actual=mapper.readTree(exporter.export(pinned,calendar,SBIN));
        assertEquals(expected,actual);
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(mapper.readTree(contracts.resolve("schemas/v1/HistoricalResearchDataset.v1.schema.json").toFile()));
        assertTrue(schema.validate(actual).isEmpty());
        verify(repository).replay(pinned); verify(repository).query(query); verify(repository).evidence(CHUNK);
        verifyNoMoreInteractions(repository);
    }

    @Test void jvmTimezoneDoesNotAffectExportOrHistoricalFingerprint() {
        var original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var utc=exporter.export(pinned,calendar,SBIN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals(utc,exporter.export(pinned,calendar,SBIN));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            assertEquals(utc,exporter.export(pinned,calendar,SBIN));
        } finally {TimeZone.setDefault(original);}
    }

    @Test void changedManifestAndMissingProvenanceFailClosed() {
        var bad=new HistoricalBarRepository.Dataset(query,bars,List.of(CHUNK),"0".repeat(64));
        assertEquals(HistoricalDataException.Reason.CONFLICT,
                assertThrows(HistoricalDataException.class,()->exporter.export(bad,calendar,SBIN)).reason());
        when(repository.evidence(CHUNK)).thenReturn(Optional.empty());
        assertThrows(HistoricalDataException.class,()->exporter.export(pinned,calendar,SBIN));
    }

    @Test void unorderedOrIncompleteBarsCannotBeExportedAsBacktestData() {
        for(var invalid:List.of(bars.reversed(),bars.subList(0,4))) {
            var bad=new HistoricalBarRepository.Dataset(query,invalid,List.of(CHUNK),HistoricalFingerprint.bars(invalid));
            when(repository.query(query)).thenReturn(bad);
            assertThrows(HistoricalDataException.class,()->exporter.export(bad,calendar,SBIN));
        }
    }
}
