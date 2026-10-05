package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KiteHistoricalAdapterTest {
    final Instant start=Instant.parse("2026-10-05T03:45:00Z"), now=start.plusSeconds(600);
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    final Instrument instrument=Instrument.create(new BrokerInstrumentId("KITE","123"),"SBIN","NSE","CASH",InstrumentType.CASH,
            Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    final HistoricalWindow window=new HistoricalWindow(instrument.id(),BarInterval.MINUTE,start,start.plusSeconds(60));
    final KiteSession session=new KiteSession(new KiteProperties("syntheticHistoricalKey","","",true),clock);
    final List<String> paths=new ArrayList<>();
    final RestClient.Builder builder=RestClient.builder().baseUrl("http://127.0.0.1").requestInterceptor((request,body,next)->{
        assertEquals("127.0.0.1",request.getURI().getHost()); assertEquals(HttpMethod.GET,request.getMethod());
        paths.add(request.getURI().getPath());return next.execute(request,body);
    });
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final InstrumentRegistry registry=mock(InstrumentRegistry.class);
    static final String BODY="""
        {"status":"success","data":{"candles":[["2026-10-05T09:15:00+0530",100.123456789,101,99,100.5,9],
        ["2026-10-05T09:16:00+0530",100,101,99,100,10]]}}
        """;
    KiteHistoricalAdapter adapter() {
        session.install(new KiteAccessToken("syntheticHistoricalToken",now,now.plusSeconds(3600))); session.profileValidated();
        when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(instrument),1,now));
        when(registry.findById(instrument.id())).thenReturn(Optional.of(instrument));
        return new KiteHistoricalAdapter(new KiteRestTransport(builder.build(),session),registry,clock,true,()->{});
    }
    @Test void onlyFixedReadRouteExactDecimalsOffsetAndHalfOpenBoundary() {
        var halt=new com.kitehybrid.platform.shared.application.RuntimeTradingHalt(()->true);
        var epoch=halt.epoch();
        var adapter=adapter();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/instruments/historical/123/minute?")))
                .andExpect(request->assertEquals("from=2026-10-05 09:15:00&to=2026-10-05 09:16:00&continuous=0&oi=0",request.getURI().getQuery()))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(BODY,MediaType.APPLICATION_JSON));
        var batch=adapter.fetch(window); assertEquals(1,batch.bars().size()); assertEquals(start,batch.bars().getFirst().startTime());
        assertEquals(new BigDecimal("100.123456789"),batch.bars().getFirst().open());
        assertEquals(List.of("/instruments/historical/123/minute"),paths);server.verify();
        assertTrue(halt.getAsBoolean());assertSame(epoch,halt.epoch());
    }
    @Test void productionSemanticsGateHasZeroHttpAndNoSessionMutation() {
        adapter();var identity=session.executionIdentity();
        var safe=KiteHistoricalAdapter.production(session,registry,clock);
        assertEquals(HistoricalDataException.Reason.TIMESTAMP_SEMANTICS_UNVERIFIED,
                assertThrows(HistoricalDataException.class,()->safe.fetch(window)).reason());
        assertTrue(paths.isEmpty());assertEquals(identity,session.executionIdentity());
    }
    @ParameterizedTest @ValueSource(strings={"auth","rate","timeout","server","malformed","duplicate-field","negative","volume","redirect"})
    void boundedFailuresNeverRetryOrMutateOrdersOrToken(String mode) {
        var adapter=adapter();var identity=session.executionIdentity();
        var expected=server.expect(anything()).andExpect(method(HttpMethod.GET));
        switch(mode) {
            case "auth" -> expected.andRespond(withStatus(HttpStatus.FORBIDDEN).body("NeverExpose"));
            case "rate" -> expected.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
            case "timeout" -> expected.andRespond(withException(new java.net.SocketTimeoutException("NeverExpose")));
            case "server" -> expected.andRespond(withServerError());
            case "redirect" -> expected.andRespond(withStatus(HttpStatus.FOUND).header("Location","https://api.kite.trade/orders"));
            default -> expected.andRespond(withSuccess(switch(mode) {
                case "negative" -> BODY.replace("100.123456789","-1");
                case "volume" -> BODY.replace(",9]",",9.5]");
                case "duplicate-field" -> BODY.replace("\"status\":", "\"status\":\"success\",\"status\":");
                default -> "NeverExpose";
            },MediaType.APPLICATION_JSON));
        }
        var failure=assertThrows(HistoricalDataException.class,()->adapter.fetch(window));
        assertFalse(failure.getMessage().contains("NeverExpose"));assertNull(failure.getCause());
        assertEquals(1,paths.size());assertEquals(identity,session.executionIdentity());server.verify();
        if(mode.equals("auth")) assertEquals(HistoricalDataException.Reason.AUTHENTICATION,failure.reason());
        if(mode.equals("rate")) assertEquals(HistoricalDataException.Reason.RATE_LIMITED,failure.reason());
    }
    @Test void missingAuthenticationMakesNoHttpCall() {
        var adapter=adapter();session.clear();
        assertEquals(HistoricalDataException.Reason.AUTHENTICATION,assertThrows(HistoricalDataException.class,()->adapter.fetch(window)).reason());
        assertTrue(paths.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"calendar-date","trailing-json"})
    void invalidCalendarDateOrTrailingDocumentMustNotBeSilentlyNormalized(String mode) {
        var adapter=adapter();
        var february=Instant.parse("2026-02-28T03:45:00Z");
        var request=mode.equals("calendar-date")
                ?new HistoricalWindow(instrument.id(),BarInterval.MINUTE,february,february.plusSeconds(60)):window;
        var body=mode.equals("calendar-date")?BODY.replace("2026-10-05","2026-02-30"):BODY+"{}";
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals(HistoricalDataException.Reason.INVALID_RESPONSE,
                assertThrows(HistoricalDataException.class,()->adapter.fetch(request)).reason());
        assertEquals(1,paths.size());server.verify();
    }
}
