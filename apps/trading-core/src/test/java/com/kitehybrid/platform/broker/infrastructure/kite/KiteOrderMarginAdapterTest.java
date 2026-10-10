package com.kitehybrid.platform.broker.infrastructure.kite;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class KiteOrderMarginAdapterTest {
    static final Instant NOW=Instant.parse("2026-10-05T06:00:00Z");
    static final String BASE="http://127.0.0.1";
    static final String BODY="""
        {"status":"success","data":[{"type":"equity","exchange":"NSE","tradingsymbol":"SBIN",
        "span":0,"exposure":0,"option_premium":0,"additional":0,"bo":0,"cash":0,"var":2000.125,
        "pnl":{"realised":0,"unrealised":0},"charges":{"total":1.25},"total":2000.125}]}
        """;
    final List<String> requests=new ArrayList<>();
    final RestClient.Builder builder=RestClient.builder().baseUrl(BASE).requestInterceptor((request,body,next)->{
        requests.add(request.getMethod().name()+" "+request.getURI().getPath());
        return next.execute(request,body);
    });
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final Clock clock=Clock.fixed(NOW,ZoneOffset.UTC);
    final KiteSession session=new KiteSession(new KiteProperties("syntheticMarginKey","","",true),clock);
    final OrderMarginQuote.Request request=new OrderMarginQuote.Request(new InstrumentId(UUID.randomUUID()),"NSE","SBIN",
            TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.INTRADAY,
            TradingReadTypes.Validity.DAY,TradingReadTypes.Variety.REGULAR,10);
    void assertCalculationOnly() {
        assertEquals(List.of("POST /margins/orders"),requests);
        for(String method:List.of("POST","PUT","DELETE"))
            assertEquals(0,requests.stream().filter(r->r.startsWith(method+" /orders")).count());
    }
    KiteOrderMarginAdapter adapter() {
        session.install(new KiteAccessToken("syntheticMarginToken",NOW,NOW.plusSeconds(3600)));session.profileValidated();
        return new KiteOrderMarginAdapter(new KiteRestTransport(builder.build(),session),session,clock);
    }
    @Test void calculationOnlyEndpointUsesExactMisRequestWithoutOrderTag() {
        var adapter=adapter();
        server.expect(requestTo(BASE+"/margins/orders")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                [{"exchange":"NSE","tradingsymbol":"SBIN","transaction_type":"BUY","variety":"regular","product":"MIS",
                  "order_type":"MARKET","quantity":10,"price":0,"trigger_price":0}]
                """,true)).andRespond(withSuccess(BODY,MediaType.APPLICATION_JSON));
        var q=adapter.estimate(request);assertEquals(request,q.request());assertEquals(new BigDecimal("2000.125"),q.requiredMargin());
        assertTrue(q.collateralTerms().isEmpty());assertEquals(NOW,q.receivedAt());server.verify();assertCalculationOnly();
    }
    @Test void authenticationLostBeforeCalculationProducesNoRequest() {
        var adapter=adapter();session.invalidate();
        var failure=assertThrows(BrokerReadException.class,()->adapter.estimate(request));
        assertEquals(BrokerReadException.Category.AUTHENTICATION,failure.category());
        assertTrue(requests.isEmpty());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"missing","string","negative","zero","huge","identity","duplicate","credit","pnl","offset","array","error","redirect","timeout","auth","trailing","nan","infinite","charges-negative","segment"})
    void unsupportedEvidenceFailsBoundedlyWithoutRetries(String change) {
        var adapter=adapter();var expected=server.expect(requestTo(BASE+"/margins/orders")).andExpect(method(HttpMethod.POST));
        if(change.equals("error")) expected.andRespond(withServerError().body("syntheticNeverExpose"));
        else if(change.equals("redirect")) expected.andRespond(withStatus(HttpStatus.FOUND).header("Location","https://api.kite.trade/orders/regular"));
        else if(change.equals("timeout")) expected.andRespond(withException(new java.net.SocketTimeoutException("syntheticNeverExpose")));
        else if(change.equals("auth")) expected.andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        else {
            String body=switch(change) {
                case "missing"->BODY.replace("\"charges\":{\"total\":1.25},","");
                case "string"->BODY.replace("2000.125","\"2000.125\"");
                case "negative"->BODY.replace("2000.125","-1");
                case "zero"->BODY.replace("2000.125","0");
                case "huge"->BODY.replace("2000.125","1e100");
                case "identity"->BODY.replace("SBIN","OTHER");
                case "duplicate"->BODY.replace("\"span\":0","\"span\":0,\"span\":0");
                case "credit"->BODY.replace("\"cash\":0","\"cash\":100");
                case "pnl"->BODY.replace("\"realised\":0","\"realised\":100");
                case "offset"->BODY.replace("\"var\":2000.125","\"var\":3000");
                case "trailing"->BODY+" {}";
                case "nan"->BODY.replace("2000.125","NaN");
                case "infinite"->BODY.replace("2000.125","Infinity");
                case "charges-negative"->BODY.replace("1.25","-1.25");
                case "segment"->BODY.replace("equity","commodity");
                default->"{\"status\":\"success\",\"data\":[]}";
            };expected.andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        }
        var failure=assertThrows(BrokerReadException.class,()->adapter.estimate(request));
        assertFalse(failure.getMessage().contains("syntheticNeverExpose"));server.verify();assertCalculationOnly();
    }
    @ParameterizedTest @ValueSource(ints={401,403,429,500,503})
    void calculatorErrorsAreSingleReadFailuresAndOnlyAuthenticationRejectionsInvalidateMemory(int status) {
        var adapter=adapter();
        server.expect(requestTo(BASE+"/margins/orders")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("syntheticNeverExpose"));
        var failure=assertThrows(BrokerReadException.class,()->adapter.estimate(request));
        assertEquals(status==401 || status==403 ? BrokerReadException.Category.AUTHENTICATION : BrokerReadException.Category.BROKER_API,
                failure.category());
        assertEquals(status!=401 && status!=403,session.authenticated());
        assertFalse(failure.getMessage().contains("syntheticNeverExpose"));
        server.verify();assertCalculationOnly();
    }
}
