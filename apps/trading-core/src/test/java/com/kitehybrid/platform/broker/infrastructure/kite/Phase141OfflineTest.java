package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class Phase141OfflineTest {
    static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    static final String HEADER = "instrument_token,exchange_token,tradingsymbol,exchange,segment,instrument_type,expiry,strike,tick_size,lot_size,listing_status\n";
    static String cash(String token, String symbol) { return token + ",999," + symbol + ",NSE,NSE,EQ,,0,0.05,1,\n"; }
    static String hash(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    DatedInstrumentMaster master(String rows, Instant observed, Optional<LocalDate> publisher) throws Exception {
        byte[] bytes = (HEADER + rows).getBytes(StandardCharsets.UTF_8);
        return new KiteOfflineInstrumentMaster().importBytes(bytes, provenance(bytes, observed, publisher));
    }
    DatedInstrumentMaster.Provenance provenance(byte[] bytes, Instant observed, Optional<LocalDate> publisher) throws Exception {
        return new DatedInstrumentMaster.Provenance("synthetic:phase141", "fixtures/phase141-master.csv", observed,
                publisher, hash(bytes), bytes.length, "kite-instruments-csv-v3", true);
    }
    List<OfflineUniverseResolution.Row> resolve(String symbol, DatedInstrumentMaster m) {
        return new OfflineUniverseResolution().resolve(List.of(new OfflineUniverseResolution.Source("NSE", symbol, true)),
                Optional.of(m), NOW, Optional.empty());
    }
    @Test void validMasterPreservesObservationProvenanceAndUsesInstrumentNotExchangeToken() throws Exception {
        var m = master(cash("101", "M&M"), NOW, Optional.empty());
        var row = resolve(" M&M ", m).getFirst();
        assertEquals(OfflineUniverseResolution.Status.RESOLVED, row.status());
        assertEquals("101", row.instrument().orElseThrow().brokerId().value());
        assertEquals("ZERODHA", row.instrument().orElseThrow().brokerId().broker());
        assertEquals(" M&M ", row.source().symbol());
        assertTrue(m.provenance().publisherAsOf().isEmpty());
        assertEquals(1, row.candidates().getFirst().sourceRecord());
        assertEquals(row, resolve(" M&M ", m).getFirst());
        assertThrows(UnsupportedOperationException.class, () -> m.candidates().clear());
    }
    @ParameterizedTest @ValueSource(strings={"duplicateRow","duplicateToken","duplicateSymbol"})
    void ambiguityNeverDisappearsByDeduplication(String mode) throws Exception {
        String other = mode.equals("duplicateRow") ? cash("101", "ALPHA")
                : mode.equals("duplicateToken") ? cash("101", "BETA") : cash("102", "ALPHA");
        var row = resolve("ALPHA", master(cash("101", "ALPHA") + other, NOW, Optional.empty())).getFirst();
        assertEquals(OfflineUniverseResolution.Status.AMBIGUOUS, row.status());
        assertTrue(row.instrument().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"alias","otherExchange","index","derivative","inactive","absent"})
    void noAliasOrNonEquityInference(String mode) throws Exception {
        String text = switch(mode) {
            case "alias" -> cash("101", "ALPHA-OLD");
            case "otherExchange" -> cash("101", "ALPHA").replace("NSE", "BSE");
            case "index" -> "101,999,ALPHA,NSE,INDICES,EQ,,0,0,0,\n";
            case "derivative" -> "101,999,ALPHA,NFO,NFO-FUT,FUT,2025-01-01,0,0.05,1,\n";
            case "inactive" -> cash("101", "ALPHA").replace(",1,\n", ",1,INACTIVE\n");
            default -> cash("101", "BETA");
        };
        var row = resolve("ALPHA", master(text, NOW, Optional.empty())).getFirst();
        assertEquals(OfflineUniverseResolution.Status.NOT_FOUND, row.status());
        assertTrue(row.instrument().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"stale","future","oldPublisher"})
    void staleMasterCannotResolve(String mode) throws Exception {
        Instant time = mode.equals("future") ? NOW.plusSeconds(1) : mode.equals("stale") ? NOW.minusSeconds(86401) : NOW;
        var publisher = mode.equals("oldPublisher") ? Optional.of(LocalDate.parse("2026-10-01")) : Optional.<LocalDate>empty();
        assertEquals(OfflineUniverseResolution.Status.STALE_MASTER,
                resolve("ALPHA", master(cash("101", "ALPHA"), time, publisher)).getFirst().status());
    }
    @Test void previousMasterProtectsAgainstRenameTokenReuseAndTokenChange() throws Exception {
        var old = master(cash("101", "OLD") + cash("202", "ALPHA"), NOW.minusSeconds(60), Optional.empty());
        var current = master(cash("101", "ALPHA"), NOW, Optional.empty());
        var result = new OfflineUniverseResolution().resolve(List.of(new OfflineUniverseResolution.Source("NSE", "ALPHA", true)),
                Optional.of(current), NOW, Optional.of(old));
        assertEquals(OfflineUniverseResolution.Status.AMBIGUOUS, result.getFirst().status());
    }
    @ParameterizedTest @ValueSource(strings={"segment","expiry","zeroToken","exchangeTokenOnly","duplicateHeader","missingColumn","empty","listing","negativeTick","badQuote","bom","utf16","invalidUtf8"})
    void malformedSnapshotFailsClosed(String mode) throws Exception {
        String text = HEADER + cash("101", "ALPHA");
        text = switch(mode) {
            case "segment" -> text.replace("NSE,NSE,EQ", "NSE,NFO-FUT,EQ");
            case "expiry" -> text.replace("EQ,,", "EQ,2026-10-01,");
            case "zeroToken" -> text.replace("101,", "0,");
            case "exchangeTokenOnly" -> text.replace("instrument_token", "other");
            case "duplicateHeader" -> text.replace("exchange_token", "instrument_token");
            case "missingColumn" -> HEADER + "101,999,ALPHA\n";
            case "empty" -> HEADER;
            case "listing" -> text.replace(",1,\n", ",1,UNKNOWN\n");
            case "negativeTick" -> text.replace("0.05", "-0.05");
            case "badQuote" -> text.replace("ALPHA", "\"ALPHA");
            case "bom" -> "\ufeff" + text;
            default -> text;
        };
        byte[] bytes = mode.equals("utf16") ? text.getBytes(StandardCharsets.UTF_16)
                : mode.equals("invalidUtf8") ? new byte[]{(byte)0xc3, (byte)0x28} : text.getBytes(StandardCharsets.UTF_8);
        var p = provenance(bytes, NOW, Optional.empty());
        assertThrows(RuntimeException.class, () -> new KiteOfflineInstrumentMaster().importBytes(bytes, p));
    }
    @Test void hashSizeFuturePublisherAndCredentialBearingReferencesRejected() throws Exception {
        byte[] b = (HEADER + cash("101", "ALPHA")).getBytes(StandardCharsets.UTF_8);
        var p = provenance(b, NOW, Optional.empty()); b[b.length-2] = 'X';
        assertThrows(IllegalArgumentException.class, () -> new KiteOfflineInstrumentMaster().importBytes(b, p));
        assertThrows(IllegalArgumentException.class, () -> new KiteOfflineInstrumentMaster().importBytes(new byte[]{1}, p));
        assertThrows(IllegalArgumentException.class, () -> provenance(b, NOW, Optional.of(LocalDate.parse("2026-10-13"))));
        assertThrows(IllegalArgumentException.class, () -> new DatedInstrumentMaster.Provenance("https://name:secret@host", "fixture", NOW,
                Optional.empty(), "a".repeat(64), 10, "kite-instruments-csv-v3", true));
    }
    @Test void invalidAndDuplicateSourcesAreRetainedAsRows() {
        var input = List.of(new OfflineUniverseResolution.Source("NSE", "ALPHA", true),
                new OfflineUniverseResolution.Source(" nse ", " alpha ", true),
                new OfflineUniverseResolution.Source("BSE", "ALPHA", true),
                new OfflineUniverseResolution.Source("NSE", "", true),
                new OfflineUniverseResolution.Source("NSE", "DISABLED", false));
        var rows = new OfflineUniverseResolution().resolve(input, Optional.empty(), NOW, Optional.empty());
        assertEquals(5, rows.size());
        assertEquals(OfflineUniverseResolution.Status.MASTER_UNAVAILABLE, rows.getFirst().status());
        assertTrue(rows.subList(1,5).stream().allMatch(r -> r.status()==OfflineUniverseResolution.Status.INVALID_SOURCE));
    }
    TradingCalendar calendar() {
        return new TradingCalendar("synthetic141", "synthetic:partial-session", Map.of(LocalDate.parse("2026-10-12"),
                new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
                        List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,25))))));
    }
    @Test void boundedProposalReusesPlanAndCannotDispatch() throws Exception {
        var m = master(cash("101", "ALPHA") + cash("102", "BETA"), NOW, Optional.empty());
        var selected = List.of(new OfflineUniverseResolution.Source("NSE", "ALPHA", true),new OfflineUniverseResolution.Source("NSE", "BETA", true));
        var pilot = new OfflineHistoricalPilot();
        var p = pilot.plan(selected, m, Optional.empty(), NOW, LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-13"), calendar());
        assertEquals(2, p.requests().size()); assertEquals(1,p.concurrency()); assertEquals(0,p.retryBudget()); assertFalse(p.redirects());
        assertEquals(Duration.ofSeconds(1),p.minimumPacedTime()); assertEquals("UNAPPROVED_OFFLINE_ONLY",p.authorizationStatus());
        assertTrue(p.requests().stream().allMatch(r -> r.route().endsWith("/5minute")));
        assertThrows(IllegalStateException.class,pilot::requireRealDispatch);
        assertThrows(IllegalArgumentException.class, () -> pilot.plan(selected.subList(0,1),m,Optional.empty(),NOW,
                LocalDate.parse("2026-10-12"),LocalDate.parse("2026-10-13"),calendar()));
        assertThrows(IllegalArgumentException.class, () -> pilot.plan(selected,m,Optional.empty(),NOW,
                LocalDate.parse("2026-10-12"),LocalDate.parse("2026-10-18"),calendar()));
        assertThrows(IllegalArgumentException.class, () -> pilot.plan(selected,m,Optional.empty(),NOW,
                LocalDate.parse("2026-10-11"),LocalDate.parse("2026-10-13"),calendar()));
    }
    @ParameterizedTest @ValueSource(strings={"valid","gap","duplicate","unordered","hash","incomplete","unknownCalendar","outsideSession"})
    void qualityRejectsUntrustedOrIncompleteWindows(String mode) throws Exception {
        var id = master(cash("101", "ALPHA"),NOW,Optional.empty()).candidates().getFirst().instrument().id();
        Instant start = Instant.parse("2026-10-12T03:45:00Z");
        var a = new HistoricalBar(id,start,BarInterval.FIVE_MINUTE,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN,1,Optional.empty());
        var b = new HistoricalBar(id,start.plusSeconds(300),BarInterval.FIVE_MINUTE,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN,1,Optional.empty());
        var bars = switch(mode) {case "gap" -> List.of(a);case "duplicate" -> List.of(a,a);case "unordered" -> List.of(b,a);default -> List.of(a,b);};
        var window = new HistoricalWindow(id,BarInterval.FIVE_MINUTE,start,start.plusSeconds(600));
        var cal = mode.equals("unknownCalendar") ? new TradingCalendar("missing","synthetic",Map.of())
                : mode.equals("outsideSession") ? new TradingCalendar("holiday","synthetic",Map.of(LocalDate.parse("2026-10-12"),new TradingCalendar.Day(TradingCalendar.Status.NON_TRADING_DAY,List.of()))) : calendar();
        var cutoff = mode.equals("incomplete") ? start.plusSeconds(300) : NOW;
        String hash = mode.equals("hash") ? "b".repeat(64) : HistoricalFingerprint.bars(bars);
        if(mode.equals("valid")) assertTrue(new OfflineHistoricalPilot().validate(window,cutoff,bars,cal,hash).complete());
        else assertThrows(IllegalArgumentException.class, () -> new OfflineHistoricalPilot().validate(window,cutoff,bars,cal,hash));
    }
    @Test void fullUniverseProducesActualUnavailableLedgerWithoutSyntheticTokens() throws Exception {
        var root = Path.of(System.getProperty("contracts.dir")).toAbsolutePath().getParent();
        var input = Files.readAllLines(root.resolve("config/universes/phase14_unique_universe.csv"));
        var source = input.stream().skip(1).map(s -> s.split(",",-1)).map(r -> new OfflineUniverseResolution.Source(r[1],r[0],r[2].equals("TRUE"))).toList();
        var rows = new OfflineUniverseResolution().resolve(source,Optional.empty(),NOW,Optional.empty());
        assertEquals(1115,rows.size()); assertEquals(1115,rows.stream().map(r -> r.source().symbol()).distinct().count());
        assertTrue(rows.stream().allMatch(r -> r.status()==OfflineUniverseResolution.Status.MASTER_UNAVAILABLE && r.instrument().isEmpty()));
        assertEquals(rows,new OfflineUniverseResolution().resolve(source,Optional.empty(),NOW,Optional.empty()));
        var ledger = rows.stream().map(r -> {
            var entry = new TreeMap<String,Object>();
            entry.put("source_record",r.sourceRecord());entry.put("exchange",r.source().exchange());entry.put("symbol",r.source().symbol());
            entry.put("enabled",true);entry.put("status",r.status().name());entry.put("reason",r.reason());entry.put("instrument_token",null);
            entry.put("instrument_id",null);entry.put("master_provenance",null);entry.put("candidates",List.of());return entry;
        }).toList();
        var output=Path.of("target/phase141-ledger.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(ledger)+"\n");
    }
    @Test void offlineAdditionsHaveNoTransportCredentialOrderOrNetworkDependency() {
        var classes = new ClassFileImporter().importClasses(KiteOfflineInstrumentMaster.class,DatedInstrumentMaster.class,
                OfflineUniverseResolution.class,OfflineHistoricalPilot.class);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net..","org.springframework..","..order..","..risk..","..broker.application.auth..").check(classes);
        for(var c:classes) for(var d:c.getDirectDependenciesFromSelf())
            assertFalse(Set.of("KiteRestTransport","KiteSession","RuntimeTradingHalt").contains(d.getTargetClass().getSimpleName()));
    }
}
