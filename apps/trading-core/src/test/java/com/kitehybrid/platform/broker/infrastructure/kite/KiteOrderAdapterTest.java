package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Adapter tests use an in-process HTTP server and never contact Kite. */
class KiteOrderAdapterTest {
    private static final String LOCAL_BASE = "http://127.0.0.1";
    private static final String KEY = "syntheticKey";
    private static final String TOKEN = "syntheticToken";
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");

    @Test
    void placeUsesBrokerRepresentationAndParsesOnlyBoundedOrderId() {
        var fixture = fixture(true);
        fixture.server.expect(requestTo(LOCAL_BASE + "/orders/regular"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token " + KEY + ":" + TOKEN))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("exchange=NSE"),
                        org.hamcrest.Matchers.containsString("tradingsymbol=ABC"),
                        org.hamcrest.Matchers.containsString("transaction_type=BUY"),
                        org.hamcrest.Matchers.containsString("order_type=LIMIT"),
                        org.hamcrest.Matchers.containsString("quantity=1"),
                        org.hamcrest.Matchers.containsString("product=CNC"),
                        org.hamcrest.Matchers.containsString("price=10.25"))))
                .andRespond(withSuccess("{\"status\":\"success\",\"data\":{\"order_id\":\"broker-1\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.adapter.place(record(limit()))).isEqualTo("broker-1");
        fixture.server.verify();
    }

    @Test
    void brokerRejectionIsSafeAndMalformedSuccessIsRejected() {
        var rejected = fixture(true);
        rejected.server.expect(requestTo(LOCAL_BASE + "/orders/regular"))
                .andRespond(withBadRequest().body("sensitive broker detail"));
        var rejection = assertThrows(OrderExecutionException.class, () -> rejected.adapter.place(record(market())));
        assertThat(rejection.category()).isEqualTo(OrderExecutionException.Category.BROKER_REJECTED);
        assertThat(rejection.getMessage()).doesNotContain("sensitive");

        var malformed = fixture(true);
        malformed.server.expect(requestTo(LOCAL_BASE + "/orders/regular"))
                .andRespond(withSuccess("{\"status\":\"success\",\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThat(assertThrows(OrderExecutionException.class, () -> malformed.adapter.place(record(market())))
                .category()).isEqualTo(OrderExecutionException.Category.MALFORMED_RESPONSE);
    }

    @Test
    void serverFailureIsAmbiguousAndAuthenticationFailureIsSafe() {
        var serverFailure = fixture(true);
        serverFailure.server.expect(requestTo(LOCAL_BASE + "/orders/regular"))
                .andRespond(withServerError().body("sensitive"));
        assertThat(assertThrows(OrderExecutionException.class, () -> serverFailure.adapter.place(record(market())))
                .category()).isEqualTo(OrderExecutionException.Category.AMBIGUOUS);

        var authFailure = fixture(true);
        authFailure.server.expect(requestTo(LOCAL_BASE + "/orders/regular"))
                .andRespond(withUnauthorizedRequest().body("token secret"));
        assertThat(assertThrows(OrderExecutionException.class, () -> authFailure.adapter.place(record(market())))
                .category()).isEqualTo(OrderExecutionException.Category.AUTHENTICATION);
    }

    @Test
    void modifyAndCancelUseOnlyInternalBrokerIdentity() {
        var fixture = fixture(true);
        fixture.server.expect(requestTo(LOCAL_BASE + "/orders/regular/broker-1"))
                .andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("{\"status\":\"success\",\"data\":{\"order_id\":\"broker-1\"}}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(LOCAL_BASE + "/orders/regular/broker-1"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess("{\"status\":\"success\",\"data\":{\"order_id\":\"broker-1\"}}", MediaType.APPLICATION_JSON));
        var modifyRecord = recordWithBroker(limit(), "broker-1");
        fixture.adapter.modify(modifyRecord, new ModifyOrder("modify", modifyRecord.id(), OrderType.LIMIT, 1,
                Optional.of(new BigDecimal("11")), Optional.empty(), 0, OrderValidity.DAY));
        var cancelRecord = recordWithBroker(limit(), "broker-1");
        fixture.adapter.cancel(cancelRecord, new CancelOrder("cancel", cancelRecord.id()));
        fixture.server.verify();
    }

    @Test
    void disabledAdapterPerformsNoRequest() {
        var fixture = fixture(false);
        assertThat(assertThrows(OrderExecutionException.class, () -> fixture.adapter.place(record(market())))
                .category()).isEqualTo(OrderExecutionException.Category.DISABLED);
        fixture.server.verify();
    }

    private static PlaceOrder market() { return command(OrderType.MARKET, Optional.empty()); }
    private static PlaceOrder limit() { return command(OrderType.LIMIT, Optional.of(new BigDecimal("10.25"))); }
    private static PlaceOrder command(OrderType type, Optional<BigDecimal> price) {
        return new PlaceOrder("key-" + type, fixtureInstrument().id(), OrderSide.BUY, 1, type,
                OrderProduct.DELIVERY, OrderValidity.DAY, price, Optional.empty(), 0, OrderVariety.REGULAR);
    }
    private static OrderRecord record(PlaceOrder command) { return recordWithBroker(command, null); }
    private static OrderRecord recordWithBroker(PlaceOrder command, String brokerId) {
        return new OrderRecord(new OrderId(java.util.UUID.randomUUID()), command, OrderState.SUBMITTED,
                Optional.ofNullable(brokerId), Optional.empty(), NOW, NOW, 2);
    }
    private static Instrument fixtureInstrument() {
        return Instrument.create(new BrokerInstrumentId("ZERODHA", "123"), "ABC", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    }
    private static Fixture fixture(boolean enabled) {
        var builder = RestClient.builder().baseUrl(LOCAL_BASE);
        var server = MockRestServiceServer.bindTo(builder).build();
        var session = new KiteSession(new KiteProperties(KEY, "syntheticSecret", TOKEN, true));
        session.profileValidated();
        var registry = new InMemoryInstrumentRegistry(); registry.replace(java.util.List.of(fixtureInstrument()), NOW);
        return new Fixture(server, new KiteOrderAdapter(new KiteRestTransport(builder.build(), session), registry,
                new OrderExecutionProperties(enabled)));
    }
    private record Fixture(MockRestServiceServer server, KiteOrderAdapter adapter) {}
}
