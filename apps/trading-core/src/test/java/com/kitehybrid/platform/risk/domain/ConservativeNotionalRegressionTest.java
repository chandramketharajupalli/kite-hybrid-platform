package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.application.RiskDecisionStore;
import com.kitehybrid.platform.shared.application.ExecutionSession;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.shared.domain.BrokerCorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic observations only: no database, transport, or execution invocation. */
class ConservativeNotionalRegressionTest {
    private static final Instant NOW = Instant.parse("2026-09-28T06:00:00Z");

    @ParameterizedTest
    @CsvSource({
        "9500,1.10,1,false,true",
        "7999.99,1.25,1,true,true",
        "8000,1.25,1,true,true",
        "800,1.25,11,false,true",
        "9090.91,1.10,1,false,true",
        "10000.00,1.0,1,true,true",
        "10000.01,1.0,1,false,false"
    })
    void riskAndExecutionEnforceSameConservativeCeiling(String price, String buffer, long quantity,
            boolean riskAllowed, boolean rawAllowed) {
        var instrument = Instrument.create(new BrokerInstrumentId("ZERODHA", "1"), "SYNTHETIC_ONLY", "NSE",
                "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.01"), 1);
        var registry = new InMemoryInstrumentRegistry(); registry.replace(List.of(instrument), NOW);
        var tick = new Tick(instrument.id(), new BigDecimal(price), NOW);
        var market = new InMemoryLatestMarketDataStore(); market.update(tick);
        var limits = new RiskLimits(true, 100, new BigDecimal("10000"), 100, new BigDecimal("1000000"),
                Duration.ofSeconds(10), Duration.ofSeconds(10), new BigDecimal(buffer), BigDecimal.ONE);
        var command = new PlaceOrder("synthetic", instrument.id(), OrderSide.BUY, quantity, OrderType.MARKET,
                OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var validated = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.VALIDATED,
                Optional.empty(), Optional.of(BrokerCorrelationId.generate()), Optional.empty(), NOW, NOW, 1);
        var margin = new BigDecimal("1000000"); var zero = BigDecimal.ZERO;
        var equity = new BrokerMargins.SegmentMargin(true, margin,
                new BrokerMargins.AvailableMargin(zero, margin, margin, margin, zero, zero),
                new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero));
        var input = new OrderRiskInput(registry.snapshot(), Map.of(instrument.id(), tick), true,
                new BrokerPositions(List.of(), List.of()), List.of(),
                new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY, equity,
                        TradingReadTypes.MarginSegment.COMMODITY, equity)), List.of());
        assertEquals(riskAllowed ? RiskReason.APPROVED : RiskReason.ORDER_VALUE_LIMIT,
                CashOrderRiskRules.evaluate(validated, input, limits, false, NOW));

        // Historical approval models an earlier lower price. Current risk need not approve this price.
        var approved = validated.transitionTo(OrderState.RISK_APPROVED, NOW);
        var risks = mock(RiskDecisionStore.class);
        when(risks.find(approved.id())).thenReturn(Optional.of(new RiskDecision(approved.id(), 1,
                RiskDecision.Outcome.APPROVED, RiskReason.APPROVED, NOW, limits.version())));
        var session = mock(ExecutionSession.class); when(session.enabled()).thenReturn(true);
        when(session.executionIdentity()).thenReturn(Optional.of(new UUID(0, 1)));
        var arm = new RuntimeExecutionArming(new SimpleMeterRegistry(), session::executionIdentity);
        arm.arm(approved, Duration.ofSeconds(30), NOW);
        var orders = mock(OrderRepository.class); when(orders.find(approved.id())).thenReturn(Optional.of(approved));
        var health = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
        var execution = new OrderExecutionProperties(true, Set.of(instrument.id()), 100, new BigDecimal("10000"),
                Duration.ofSeconds(60), Duration.ofSeconds(10), limits, "regression");
        var live = new LiveTestProperties(true, Set.of(instrument.id()), 100, new BigDecimal("10000"), Duration.ofSeconds(30));
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var extra = new LiveTestExecutionChecks(true, live, arm,
                () -> new OperationalReadiness.Evidence(true, true, true, true), clock);
        var policy = new ExecutionSafetyPolicy(execution, arm, () -> false, session, risks, registry, market,
                () -> health, orders, clock, new SimpleMeterRegistry(), ExecutionAuthorizationAuditStore.NOOP, extra);
        var report = policy.inspect(approved);
        assertEquals(rawAllowed, new BigDecimal(price).multiply(BigDecimal.valueOf(quantity))
                .compareTo(new BigDecimal("10000")) <= 0, "Historical raw arithmetic for regression evidence");
        assertEquals(riskAllowed, report.gates().get(ExecutionReadiness.Gate.NOTIONAL_WITHIN_CAP) == ExecutionDenialReason.NONE);
        assertEquals(riskAllowed, report.gates().get(ExecutionReadiness.Gate.LIVE_TEST_NOTIONAL_WITHIN_CAP) == ExecutionDenialReason.NONE);
        assertEquals(riskAllowed, report.ready());
    }
}
