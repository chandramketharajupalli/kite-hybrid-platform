package com.kitehybrid.platform.order;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.application.RiskDecisionStore;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionFreshnessAuditTest {
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final String POLICY = "cash-v1:" + "0".repeat(64);

    @Test void futureApprovalIsDenied() {
        assertEquals(ExecutionDenialReason.RISK_APPROVAL_EXPIRED, evaluate(NOW.plusSeconds(1), NOW).reason());
    }
    @Test void futureTickIsDenied() {
        assertEquals(ExecutionDenialReason.MARKET_DATA_STALE, evaluate(NOW, NOW.plusSeconds(1)).reason());
    }
    @Test void currentInputsStillAllow() {
        assertTrue(evaluate(NOW, NOW).allowed());
    }
    @Test void slowEvidenceReadCannotUseAnOldClockSample() {
        assertFalse(evaluate(NOW, NOW, true).allowed());
    }
    @Test void runtimeArmingRejectsUnboundedDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeExecutionArming(new SimpleMeterRegistry(), () -> Optional.of(new UUID(0,1))).arm(Duration.ofDays(365), NOW));
    }
    @Test void auditIdentityChangesWithSafetyConfigurationAndNumericBoundsFailClosed() {
        var id = new InstrumentId(UUID.randomUUID());
        var first = new OrderExecutionProperties(true, Set.of(id), 1, BigDecimal.TEN,
                Duration.ofSeconds(10), Duration.ofSeconds(10), POLICY, "policy");
        var changed = new OrderExecutionProperties(true, Set.of(id), 2, BigDecimal.TEN,
                Duration.ofSeconds(10), Duration.ofSeconds(10), POLICY, "policy");
        assertNotEquals(first.auditVersion(), changed.auditVersion());
        assertTrue(first.auditVersion().matches("execution-v2:[0-9a-f]{64}"));
        assertThrows(IllegalArgumentException.class, () -> new OrderExecutionProperties(true, Set.of(id), 1,
                new BigDecimal("1e100"), Duration.ofSeconds(10), Duration.ofSeconds(10), POLICY, "policy"));
    }

    private ExecutionAuthorizationDecision evaluate(Instant riskAt, Instant tickAt) {
        return evaluate(riskAt, tickAt, false);
    }
    private ExecutionAuthorizationDecision evaluate(Instant riskAt, Instant tickAt, boolean slowRead) {
        var currentTime = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(call -> currentTime.get());
        var id = new OrderId(UUID.randomUUID());
        var instrumentId = new InstrumentId(UUID.randomUUID());
        var command = new PlaceOrder("audit", instrumentId, OrderSide.BUY, 1, OrderType.MARKET,
                OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var order = new OrderRecord(id, command, OrderState.RISK_APPROVED, Optional.empty(),
                Optional.of(BrokerCorrelationId.generate()), Optional.empty(), NOW, NOW, 2);
        var risks = mock(RiskDecisionStore.class);
        when(risks.find(id)).thenAnswer(call -> {
            if (slowRead) currentTime.set(NOW.plusSeconds(61));
            return Optional.of(new RiskDecision(id, 1, RiskDecision.Outcome.APPROVED, RiskReason.APPROVED, riskAt, POLICY));
        });
        var registry = mock(InstrumentRegistry.class);
        when(registry.findById(instrumentId)).thenReturn(Optional.of(mock(Instrument.class)));
        var market = mock(LatestMarketDataStore.class);
        when(market.latest(instrumentId)).thenReturn(Optional.of(new Tick(instrumentId, BigDecimal.TEN, tickAt)));
        var session = mock(KiteAuthenticationSession.class);
        when(session.executionIdentity()).thenReturn(Optional.of(new UUID(0,1)));when(session.enabled()).thenReturn(true);
        when(session.authenticated()).thenReturn(true);
        when(session.tokenAvailable()).thenReturn(true);
        var health = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW),
                1, 1, 0, 1, 1, 0, 0, 0, 0);
        var arm = new RuntimeExecutionArming(new SimpleMeterRegistry(), () -> Optional.of(new UUID(0,1)));
        arm.arm(Duration.ofMinutes(1), NOW);
        var properties = new OrderExecutionProperties(true, Set.of(instrumentId), 1, BigDecimal.TEN,
                Duration.ofMinutes(1), Duration.ofMinutes(1), POLICY, "audit");
        return new ExecutionSafetyPolicy(properties, arm, () -> false, session, risks, registry, market,
                () -> health, mock(OrderRepository.class), clock,
                new SimpleMeterRegistry(), ExecutionAuthorizationAuditStore.NOOP).evaluate(order);
    }
}
