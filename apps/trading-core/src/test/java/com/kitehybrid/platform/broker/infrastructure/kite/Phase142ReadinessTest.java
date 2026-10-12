package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class Phase142ReadinessTest {
    static final Instant START = Instant.parse("2026-10-12T03:45:00Z"), NOW = START.plusSeconds(3600);
    static final String PIN = "a".repeat(64);
    final Instrument stock = Instrument.create(new BrokerInstrumentId("ZERODHA","101"),"SYNTHETIC-A","NSE","CASH",
            InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    final OfflineDataVerification service = new OfflineDataVerification();
    HistoricalBar bar(Instant time) {
        return new HistoricalBar(stock.id(),time,BarInterval.FIVE_MINUTE,BigDecimal.TEN,new BigDecimal("11"),
                new BigDecimal("9"),BigDecimal.TEN,0,Optional.empty());
    }
    TradingCalendar calendar() {
        return new TradingCalendar("synthetic142","synthetic:early-close",Map.of(LocalDate.parse("2026-10-12"),
                new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
                        List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,25))))));
    }
    OfflineDataVerification.Input input(String mode) {
        var bars = switch(mode) {
            case "gap" -> List.of(bar(START));
            case "duplicate" -> List.of(bar(START),bar(START));
            case "unordered" -> List.of(bar(START.plusSeconds(300)),bar(START));
            case "empty" -> List.<HistoricalBar>of();
            default -> List.of(bar(START),bar(START.plusSeconds(300)));
        };
        var cal = mode.equals("unknown") ? new TradingCalendar("unknown","synthetic",Map.of())
                : mode.equals("holiday") ? new TradingCalendar("holiday","synthetic",Map.of(LocalDate.parse("2026-10-12"),
                    new TradingCalendar.Day(TradingCalendar.Status.NON_TRADING_DAY,List.of()))) : calendar();
        var window = new HistoricalWindow(stock.id(),BarInterval.FIVE_MINUTE,START,START.plusSeconds(600));
        return new OfflineDataVerification.Input(window,mode.equals("partial") ? START.plusSeconds(300) : NOW,
                mode.equals("future") ? NOW.minusSeconds(1) : NOW,bars,cal,mode.equals("calendarHash") ? PIN : cal.fingerprint(),
                mode.equals("replay") ? PIN : HistoricalFingerprint.bars(bars),mode.equals("raw") ? null : PIN,
                mode.equals("master") ? "invalid" : PIN,mode.equals("corporate") ? null : PIN,
                mode.equals("timezone") ? ZoneId.of("America/New_York") : TradingCalendar.NSE_ZONE,true);
    }
    @ParameterizedTest @ValueSource(strings={"gap","duplicate","unordered","empty","unknown","holiday","partial","future","calendarHash","replay","raw","master","corporate","timezone"})
    void incompleteOrUntrustedSessionsAreQuarantinedWithoutPartialAcceptance(String mode) {
        var input = input(mode); var report = service.review(Optional.of(input));
        assertEquals(OfflineDataVerification.QualityStatus.QUARANTINED,report.status());
        assertEquals(0,report.accepted()); assertTrue(report.synthetic()); assertFalse(report.flags().isEmpty());
        assertEquals(report,service.review(Optional.of(input)));
    }
    @Test void actualMissingCandlesHaveNoSyntheticOrRealPerformanceClaim() {
        var r = service.review(Optional.empty());
        assertEquals(OfflineDataVerification.QualityStatus.NOT_EVALUATED,r.status());
        assertFalse(r.synthetic()); assertTrue(r.contentHash().isEmpty()); assertEquals(0,r.accepted());
    }
    @ParameterizedTest @ValueSource(strings={"UTC","America/New_York","Europe/London","Asia/Kolkata"})
    void verifiedShortSyntheticSessionIsIndependentOfHostTimezone(String zone) {
        var old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var r = service.review(Optional.of(input("valid")));
            assertEquals(OfflineDataVerification.QualityStatus.OFFLINE_CHECKS_PASSED,r.status());
            assertEquals(2,r.accepted()); assertTrue(r.synthetic()); assertTrue(r.flags().isEmpty());
            assertEquals(ZoneOffset.ofHoursMinutes(5,30),START.atZone(TradingCalendar.NSE_ZONE).getOffset());
        } finally { TimeZone.setDefault(old); }
    }
    @ParameterizedTest @ValueSource(strings={"negativePrice","zeroPrice","negativeVolume","high","low","unaligned","nan","infinity"})
    void malformedRawValuesCannotEnterValidatedDomain(String mode) {
        assertThrows(RuntimeException.class, () -> new HistoricalBar(stock.id(),mode.equals("unaligned") ? START.plusSeconds(60) : START,
                BarInterval.FIVE_MINUTE,new BigDecimal(switch(mode) {case "negativePrice" -> "-1";case "zeroPrice" -> "0";case "nan" -> "NaN";case "infinity" -> "Infinity";default -> "10";}),
                new BigDecimal(mode.equals("high") ? "8" : "11"),new BigDecimal(mode.equals("low") ? "12" : "9"),
                BigDecimal.TEN,mode.equals("negativeVolume") ? -1 : 0,Optional.empty()));
    }
    DatedInstrumentMaster imported() throws Exception {
        String text = "instrument_token,exchange_token,tradingsymbol,exchange,segment,instrument_type,expiry,strike,tick_size,lot_size\n"
                + "101,999,SYNTHETIC-A,NSE,NSE,EQ,,0,0.05,1\n"
                + "102,999,SYNTHETIC-INDEX,NSE,INDICES,EQ,,0,0,0\n"
                + "103,999,SYNTHETIC-DUP,NSE,NSE,EQ,,0,0.05,1\n"
                + "104,999,SYNTHETIC-DUP,NSE,NSE,EQ,,0,0.05,1\n";
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new KiteOfflineInstrumentMaster().importBytes(bytes,new DatedInstrumentMaster.Provenance("synthetic:phase142",
                "fixtures/phase142",NOW,Optional.empty(),hash,bytes.length,"kite-instruments-csv-v3",true));
    }
    @Test void ledgerVocabularyRetainsReasonsCandidatesAndOriginalNamespace() throws Exception {
        var names=List.of("SYNTHETIC-A","SYNTHETIC-INDEX","SYNTHETIC-DUP","MISSING-SYNTHETIC");
        var source=names.stream().map(s->new OfflineUniverseResolution.Source("NSE",s,true)).toList();
        var rows=service.resolve(source,Optional.of(imported()),NOW,Optional.empty());
        assertEquals(List.of(OfflineDataVerification.ResolutionStatus.RESOLVED,OfflineDataVerification.ResolutionStatus.INELIGIBLE,
                OfflineDataVerification.ResolutionStatus.AMBIGUOUS,OfflineDataVerification.ResolutionStatus.MISSING),rows.stream().map(r->r.status()).toList());
        assertEquals("ZERODHA",rows.getFirst().instrument().orElseThrow().brokerId().broker());
        assertEquals("101",rows.getFirst().instrument().orElseThrow().brokerId().value());
        assertEquals(2,rows.get(2).candidates().size());
        assertEquals(OfflineDataVerification.ResolutionStatus.MASTER_UNAVAILABLE,
                service.resolve(source,Optional.of(imported()),NOW.plusSeconds(86401),Optional.empty()).getFirst().status());
    }
    @Test void producesFullActualLedgerAndSeparateSyntheticQualityEvidence() throws Exception {
        Path root=Path.of(System.getProperty("contracts.dir")).toAbsolutePath().getParent();
        var source=Files.readAllLines(root.resolve("config/universes/phase14_unique_universe.csv")).stream().skip(1)
                .map(s->s.split(",",-1)).map(r->new OfflineUniverseResolution.Source(r[1],r[0],r[2].equals("TRUE"))).toList();
        var rows=service.resolve(source,Optional.empty(),NOW,Optional.empty());assertEquals(1115,rows.size());
        assertTrue(rows.stream().allMatch(r->r.status()==OfflineDataVerification.ResolutionStatus.MASTER_UNAVAILABLE));
        var ledger=rows.stream().map(r->{var m=new TreeMap<String,Object>();m.put("source_record",r.sourceRecord());m.put("exchange",r.source().exchange());
            m.put("symbol",r.source().symbol());m.put("enabled",true);m.put("status",r.status().name());m.put("reason",r.reason());
            m.put("instrument_token",null);m.put("instrument_id",null);m.put("master_provenance",null);m.put("candidates",List.of());return m;}).toList();
        var json=new ObjectMapper().writerWithDefaultPrettyPrinter();Path dir=Path.of("target/phase142");Files.createDirectories(dir);
        Files.writeString(dir.resolve("resolution-ledger.json"),json.writeValueAsString(ledger)+"\n");
        var examples=new TreeMap<String,Object>();
        for(String mode:List.of("valid","gap","duplicate","unordered","empty","unknown","holiday","partial","future","calendarHash","replay","raw","master","corporate","timezone")) {
            var r=service.review(Optional.of(input(mode)));
            examples.put(mode,Map.of("status",r.status().name(),"synthetic",true,"received",r.received(),"accepted",r.accepted(),"flags",r.flags(),"content_hash",r.contentHash().orElseThrow()));
        }
        Files.writeString(dir.resolve("synthetic-quality.json"),json.writeValueAsString(examples)+"\n");
    }
    @Test void noRealDispatchCredentialOrOrderCapabilityAdded() {
        assertThrows(IllegalStateException.class,()->new OfflineHistoricalPilot().requireRealDispatch());
        var classes=new ClassFileImporter().importClasses(OfflineDataVerification.class);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net..","java.sql..","org.springframework..","..broker..","..order..","..risk..").check(classes);
    }
}
