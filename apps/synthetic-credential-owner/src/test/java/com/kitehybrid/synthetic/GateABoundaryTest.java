package com.kitehybrid.synthetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GateABoundaryTest {
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final byte[] CSV = ("instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange\n"
            + "123,9,M&M,synthetic,0,,,0.05,1,EQ,NSE,NSE\n").getBytes(StandardCharsets.UTF_8);
    AttemptJournal journal;
    GateABoundary boundary;
    GateABoundary.Approval approval;
    @BeforeEach void setup() throws Exception {
        journal = new AttemptJournal(UUID.randomUUID().toString()); journal.initialize();
        boundary = new GateABoundary(journal, CLOCK);
        approval = approval(UUID.randomUUID());
    }
    @AfterEach void cleanup() throws Exception { journal.destroyFixture(); }
    static GateABoundary.Approval approval(UUID id) {
        return new GateABoundary.Approval(id,"fixture-owner",NOW,NOW.plusSeconds(60),true,true,"A");
    }
    static GateABoundary.Response response() {
        return new GateABoundary.Response(CSV,"text/csv; charset=utf-8",GateABoundary.Outcome.OK);
    }
    GateABoundary.Observation acquire(GateABoundary.Approval a) {
        return boundary.acquire("GET",GateABoundary.URL,null,a,"fixture-owner",response());
    }
    @Test void validResponseHasImmutableBytesHashAndSyntheticProvenance() {
        var out=acquire(approval);assertArrayEquals(CSV,out.csv());assertEquals(NOW,out.observedAt());
        assertTrue(out.synthetic());assertEquals(64,out.rawSha256().length());out.csv()[0]=0;
        assertArrayEquals(CSV,out.csv());assertEquals(1,boundary.fixtureDispatches());assertEquals(0,boundary.realRequests());
    }
    @ParameterizedTest @ValueSource(strings={"/instruments/NSE","https://api.kite.trade/instruments","https://api.kite.trade/instruments/NFO",
            "https://api.kite.trade/instruments/NSE/","https://api.kite.trade/instruments/historical/1/5minute","https://api.kite.trade/orders",
            "http://api.kite.trade/instruments/NSE","https://other.invalid/instruments/NSE","https://u@api.kite.trade/instruments/NSE",
            "https://api.kite.trade:443/instruments/NSE","https://api.kite.trade/instruments/NSE?","https://api.kite.trade/instruments/NSE#x",
            "https://api.kite.trade/instruments/./NSE","https://api.kite.trade/instruments/%4eSE","https://API.KITE.TRADE/instruments/NSE",
            "https://api.kite.trade./instruments/NSE","https://api.kite.trade/instruments/NSE%2f.."," https://api.kite.trade/instruments/NSE"})
    void alternateUrlsNeverReachFixture(String url) {
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",url,null,approval,"fixture-owner",response()));
        assertEquals(0,boundary.fixtureDispatches());
    }
    @ParameterizedTest @ValueSource(strings={"POST","PUT","DELETE","HEAD","get","GET ","CONNECT"})
    void methodsDenied(String method) {
        assertThrows(IllegalStateException.class,()->boundary.acquire(method,GateABoundary.URL,null,approval,"fixture-owner",response()));
        assertEquals(0,boundary.fixtureDispatches());
    }
    @ParameterizedTest @ValueSource(strings={"", "http://127.0.0.1:8080", "DIRECT", "socks://example.invalid"})
    void allProxyConfigurationDenied(String proxy) {
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,proxy,approval,"fixture-owner",response()));
        assertEquals(0,boundary.fixtureDispatches());
    }
    @ParameterizedTest @ValueSource(strings={"missing","expired","future","owner","real","storage","gateB","long"})
    void unsatisfiedApprovalAndCustodyDenied(String mode) {
        var a=new GateABoundary.Approval(approval.id(),mode.equals("owner")?null:"fixture-owner",
                mode.equals("future")?NOW.plusSeconds(1):NOW,
                mode.equals("expired")?NOW:NOW.plusSeconds(mode.equals("long")?301:60),
                !mode.equals("real"),!mode.equals("storage"),mode.equals("gateB")?"B":"A");
        assertThrows(IllegalStateException.class,()->acquire(mode.equals("missing")?null:a));assertEquals(0,boundary.fixtureDispatches());
    }
    @ParameterizedTest @ValueSource(strings={"TIMEOUT","UNCERTAIN","REDIRECT","SERVER_ERROR"})
    void uncertainOutcomeNeverRefundsOrRetries(String outcome) {
        var r=new GateABoundary.Response(CSV,"text/csv; charset=utf-8",GateABoundary.Outcome.valueOf(outcome));
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,null,approval,"fixture-owner",r));
        assertThrows(IllegalStateException.class,()->acquire(approval));assertEquals(1,boundary.fixtureDispatches());
        var restarted=new GateABoundary(journal,CLOCK);
        assertThrows(IllegalStateException.class,()->restarted.acquire("GET",GateABoundary.URL,null,approval,"fixture-owner",response()));
    }
    @Test void concurrentOwnersHaveExactlyOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);Callable<Boolean> task=()->{start.await();try { acquire(approval);return true; } catch(IllegalStateException e){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertNotEquals(a.get(),b.get());
        }
        assertEquals(1,boundary.fixtureDispatches());
    }
    @Test void partialReservationDeniesWithoutDispatch() throws Exception {
        Files.createFile(journal.path(approval.id()));assertThrows(IllegalStateException.class,()->acquire(approval));
        assertEquals(0,boundary.fixtureDispatches());
    }
    @Test void unavailableJournalFailsClosed() {
        var absent=new GateABoundary(new AttemptJournal(UUID.randomUUID().toString()),CLOCK);
        assertThrows(IllegalStateException.class,()->absent.acquire("GET",GateABoundary.URL,null,approval,"fixture-owner",response()));
        assertEquals(0,absent.fixtureDispatches());
    }
    @Test void oversizedBodyRejectedBeforeClone() {
        assertThrows(IllegalStateException.class,()->new GateABoundary.Response(new byte[GateABoundary.LIMIT+1],"text/csv; charset=utf-8",GateABoundary.Outcome.OK));
    }
    @ParameterizedTest @ValueSource(strings={"badUtf8","bom","nul","empty","contentType"})
    void invalidEnvelopeConsumesWithoutLeaking(String mode) {
        byte[] body=switch(mode){case "badUtf8"->new byte[]{(byte)0xff};case "bom"->"\ufeffx".getBytes(StandardCharsets.UTF_8);case "nul"->new byte[]{0};case "empty"->new byte[0];default->CSV;};
        var r=new GateABoundary.Response(body,mode.equals("contentType")?"text/html":"text/csv; charset=utf-8",GateABoundary.Outcome.OK);
        var failure=assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,null,approval,"fixture-owner",r));
        assertEquals("GATE_A_OFFLINE_DENIED",failure.getMessage());assertNull(failure.getCause());
        assertThrows(IllegalStateException.class,()->acquire(approval));assertEquals(1,boundary.fixtureDispatches());
    }
    @Test void dependencyBytesHaveNoNetworkTradingOrLoggingCapability() throws Exception {
        for(var type:List.of(GateABoundary.class,GateABoundary.Response.class,GateABoundary.Observation.class,GateABoundary.Approval.class)) {
            var bytes=type.getResourceAsStream(type.getSimpleName().contains("$")?"unused":type.getName().substring(type.getName().lastIndexOf('.')+1)+".class");
            assertNotNull(bytes);String content;try(bytes){content=new String(bytes.readAllBytes(),StandardCharsets.ISO_8859_1);}
            for(String denied:List.of("java/net/","java/sql/","com/kitehybrid/platform","java/lang/System","org/slf4j","java/util/logging"))assertFalse(content.contains(denied),denied);
        }
        assertEquals("SYNTHETIC_APPROVAL_REDACTED",approval.toString());assertEquals("SYNTHETIC_RESPONSE_REDACTED",response().toString());
    }
    @Test void diagnosticsNeverWriteBodyOrOwnerToOutput() {
        var captured=new java.io.ByteArrayOutputStream();var out=System.out;var err=System.err;
        try(var sink=new java.io.PrintStream(captured)) {
            System.setOut(sink);System.setErr(sink);
            var observation=acquire(approval);
            assertEquals("SYNTHETIC_OBSERVATION_REDACTED",observation.toString());
            assertThrows(IllegalStateException.class,()->acquire(approval));
        } finally { System.setOut(out);System.setErr(err); }
        assertEquals(0,captured.size());
    }
    @Test void csvInjectionIsNeverEvaluatedOrSilentlyRewritten() {
        byte[] formula="symbol\n=1+1\n".getBytes(StandardCharsets.UTF_8);
        var observation=boundary.acquire("GET",GateABoundary.URL,null,approval,"fixture-owner",
                new GateABoundary.Response(formula,"text/csv; charset=utf-8",GateABoundary.Outcome.OK));
        assertArrayEquals(formula,observation.csv()); // Untrusted bytes, not a parsed/approved instrument.
    }
}
