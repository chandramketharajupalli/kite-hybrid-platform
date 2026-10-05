package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.OrderMarginEstimator;
import com.kitehybrid.platform.broker.domain.read.OrderMarginQuote;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** Single calculation-only POST, no mutation gateway, retries, raw response logs, or collateral inference. */
final class KiteOrderMarginAdapter implements OrderMarginEstimator {
    private static final JsonMapper JSON=JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final KiteRestTransport transport; private final KiteSession session; private final Clock clock;
    KiteOrderMarginAdapter(KiteRestTransport transport,KiteSession session,Clock clock) {
        this.transport=transport;this.session=session;this.clock=clock;
    }
    @Override public OrderMarginQuote estimate(OrderMarginQuote.Request request) {
        synchronized(session) {
            if(!session.authenticated()) throw new BrokerReadException(AUTHENTICATION);
            try {
                var body=JSON.writeValueAsString(List.of(Map.of("exchange",request.exchange(),"tradingsymbol",request.symbol(),
                        "transaction_type","BUY","variety","regular","product","MIS","order_type","MARKET",
                        "quantity",request.quantity(),"price",BigDecimal.ZERO,"trigger_price",BigDecimal.ZERO)));
                var root=JSON.readTree(transport.calculateOrderMargin(body));
                if(root==null || !"success".equals(root.path("status").textValue()) || !root.path("data").isArray()
                        || root.path("data").size()!=1) throw new IllegalArgumentException();
                var row=root.path("data").get(0);
                if(!request.exchange().equals(row.path("exchange").textValue()) || !request.symbol().equals(row.path("tradingsymbol").textValue())
                        || !"equity".equals(row.path("type").textValue())) throw new IllegalArgumentException();
                // No credit/offset or derivative margin assumptions for this single cash-equity BUY.
                for(var name:List.of("span","exposure","option_premium","additional","bo","cash"))
                    if(number(row,name).signum()!=0) throw new IllegalArgumentException();
                if(number(row.path("pnl"),"realised").signum()!=0 || number(row.path("pnl"),"unrealised").signum()!=0)
                    throw new IllegalArgumentException();
                var required=number(row,"total");
                if(required.compareTo(number(row,"var"))!=0) throw new IllegalArgumentException();
                // The documented response does not establish eligible collateral/cash-component terms.
                return new OrderMarginQuote(request,required,number(row.path("charges"),"total"),Optional.empty(),clock.instant());
            } catch(BrokerReadException safe) { throw safe; }
            catch(Exception invalid) { throw new BrokerReadException(INVALID_RESPONSE); }
        }
    }
    private static BigDecimal number(JsonNode row,String name) {
        var n=row.get(name); if(n==null || !n.isNumber()) throw new IllegalArgumentException();
        var value=n.decimalValue();
        if(value.precision()>36 || Math.abs((long)value.scale())>18 || value.abs().compareTo(new BigDecimal("1e18"))>=0)
            throw new IllegalArgumentException();
        return value;
    }
}
