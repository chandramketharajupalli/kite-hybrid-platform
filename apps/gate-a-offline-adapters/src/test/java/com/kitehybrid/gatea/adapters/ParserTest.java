package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.platform.instrument.application.OfflineUniverseResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ParserTest {
    @ParameterizedTest @ValueSource(strings={"BSE,BSE,EQ","NSE,INDICES,EQ","NFO,NFO-FUT,FUT","NSE,NFO,EQ"})
    void wrongExchangeSegmentType(String fields) {
        String csv=CSV.replace("NSE,NSE,EQ",fields);assertFalse(parser(csv).valid(csv.getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","4294967296","1.1","x",""})
    void invalidTokens(String token) {
        String csv=CSV.replace("101,",token+",");assertFalse(parser(csv).valid(csv.getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(A1)","-SUM(A1)","@SUM(A1)"})
    void formulaSymbolsRejectedWithoutRewriting(String symbol) {
        String csv=CSV.replace("SYNTH&A",symbol);assertFalse(parser(csv).valid(csv.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void duplicatesAndTokenCollisionsRejectEntireWindow() {
        for(String row:List.of("101,SYNTH&A,NSE,NSE,EQ,,0,0.05,1\n","101,OTHER,NSE,NSE,EQ,,0,0.05,1\n")) {
            String csv=CSV+row;var p=parser(csv);assertFalse(p.valid(csv.getBytes(StandardCharsets.UTF_8)));assertTrue(p.rows().isEmpty());
        }
    }
    @Test void ambiguousSymbolRemainsUnresolvedAndDeterministic() {
        String csv=CSV+"102,SYNTH&A,NSE,NSE,EQ,,0,0.05,1\n";var p=parser(csv);
        assertTrue(p.valid(csv.getBytes(StandardCharsets.UTF_8)));var first=p.rows();
        assertEquals(OfflineUniverseResolution.Status.AMBIGUOUS,first.getFirst().status());assertTrue(first.getFirst().instrument().isEmpty());
        assertTrue(p.valid(csv.getBytes(StandardCharsets.UTF_8)));assertEquals(first,p.rows());
    }
    @ParameterizedTest @ValueSource(strings={"missing_header","duplicate_header","truncated","unclosed_quote","bom","nul"})
    void malformedCsv(String kind) {
        String csv=switch(kind) {
            case "missing_header" -> CSV.replace("instrument_token","wrong");
            case "duplicate_header" -> CSV.replace("tradingsymbol","instrument_token");
            case "truncated" -> HEADER+"101,SYNTH&A\n";
            case "unclosed_quote" -> HEADER+"101,\"unclosed\n";
            case "bom" -> "\ufeff"+CSV;
            default -> CSV+"\0";
        };
        assertFalse(parser(csv).valid(csv.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void strictEncodingHashSizeAndStaleResultReset() {
        byte[] invalid={(byte)0xc3,(byte)0x28};assertFalse(parser(invalid).valid(invalid));
        var p=parser(CSV);assertTrue(p.valid(CSV.getBytes(StandardCharsets.UTF_8)));
        assertFalse(p.valid(CSV.replace("101","102").getBytes(StandardCharsets.UTF_8)));assertTrue(p.rows().isEmpty());
        assertFalse(p.valid(new byte[GateAOfflinePolicy.LIMIT+1]));
        assertThrows(IllegalStateException.class,()->new GateAOfflinePolicy.FixtureResponse(new byte[GateAOfflinePolicy.LIMIT+1],GateAOfflinePolicy.FixtureOutcome.OK));
    }
    @Test void productionTransportAndTradingRuntimeAreAbsent() {
        for(String name:List.of("com.kitehybrid.platform.broker.infrastructure.kite.KiteRestTransport",
                "org.springframework.boot.SpringApplication","com.kitehybrid.synthetic.SyntheticOwner",
                "org.postgresql.Driver","com.kitehybrid.platform.broker.infrastructure.kite.KiteHistoricalAdapter")) {
            assertThrows(ClassNotFoundException.class,()->Class.forName(name));
        }
    }
    @Test void custodyHasNoSecretAccessorOrImplementation() {
        assertEquals(List.of("provenance"),Arrays.stream(SessionCustody.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList());
        assertFalse(java.io.Serializable.class.isAssignableFrom(SessionCustody.Provenance.class));
    }
}
