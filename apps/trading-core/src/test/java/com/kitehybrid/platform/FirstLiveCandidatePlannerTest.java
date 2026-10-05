package com.kitehybrid.platform;

import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.order.application.OrderExecutionProperties;
import com.kitehybrid.platform.risk.domain.RiskLimits;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.platform.operator.application.FirstLiveCandidatePlanner.*;

class FirstLiveCandidatePlannerTest {
    static final Instant NOW = Instant.parse("2026-10-05T06:00:00Z");
    static final BigDecimal CAP = new BigDecimal("10000");
    static final Instrument SBIN = Instrument.create(new BrokerInstrumentId("ZERODHA", "synthetic-only"),
            "SBIN", "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    final FirstLiveCandidatePlanner planner = new FirstLiveCandidatePlanner();
    RiskLimits risk = new RiskLimits(true, 100, CAP, 100, CAP, Duration.ofSeconds(5), Duration.ofSeconds(60), new BigDecimal("1.10"), BigDecimal.ONE);
    OrderExecutionProperties execution = new OrderExecutionProperties(true, Set.of(SBIN.id()), 100, CAP, Duration.ofSeconds(60), Duration.ofSeconds(5), risk, "test");
    LiveTestProperties live = new LiveTestProperties(true, Set.of(SBIN.id()), 100, CAP, Duration.ofSeconds(30));
    InstrumentSnapshot reference = InstrumentSnapshot.validated(List.of(SBIN), 1, NOW);
    Tick tick = new Tick(SBIN.id(), new BigDecimal("800"), NOW);
    Headroom headroom = new Headroom(100, CAP, CAP, NOW, Duration.ofSeconds(5), true);
    MarketDataHealth health = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
            MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
    boolean enabled = true;
    Set<com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId> active = Set.of(SBIN.id());
    Plan plan(Instant now) { return planner.plan("NSE", "SBIN", CAP, Duration.ofSeconds(30), execution, live,
            new Evidence(reference, tick, health, Set.of(SBIN.id()), active, enabled, headroom), now); }

    @Test void deterministicBoundedPlanHasNoOrderIdentityAndRequiresRecheck() {
        var result = plan(NOW);
        assertEquals(Status.SIZED_RECHECK_REQUIRED, result.status()); assertEquals(11, result.quantity());
        assertEquals(0, new BigDecimal("9680").compareTo(result.conservativeNotional()));
        assertEquals(result, plan(NOW)); assertTrue(planner.stillCurrent(result, plan(NOW)));
        assertFalse(result.toString().contains("synthetic-only"));
        assertFalse(Arrays.stream(Plan.class.getRecordComponents()).anyMatch(c -> c.getName().toLowerCase().contains("orderid")));
    }
    @ParameterizedTest @ValueSource(strings={"price", "stale", "future", "exchange", "disabled", "removed", "lot", "tick", "active", "health", "headroom", "account-stale", "account-unreadable", "buffer"})
    void changedOrMissingEvidenceNeverReusesReviewedPlan(String change) {
        var before = plan(NOW); var now = NOW;
        switch (change) {
            case "price" -> tick = new Tick(SBIN.id(), new BigDecimal("900"), NOW);
            case "stale" -> now = NOW.plusSeconds(5);
            case "future" -> tick = new Tick(SBIN.id(), new BigDecimal("800"), NOW.plusSeconds(1));
            case "exchange" -> tick = new Tick(SBIN.id(), new BigDecimal("800"), NOW, Optional.of(NOW.minusSeconds(5)), Optional.empty(), Optional.empty());
            case "disabled" -> enabled = false;
            case "removed" -> reference = InstrumentSnapshot.empty();
            case "lot", "tick" -> reference = InstrumentSnapshot.validated(List.of(Instrument.create(SBIN.brokerId(), "SBIN", "NSE", "CASH", InstrumentType.CASH,
                    Optional.empty(), Optional.empty(), new BigDecimal(change.equals("tick") ? "0.10" : "0.05"), change.equals("lot") ? 3 : 1)), 2, NOW);
            case "active" -> active = Set.of();
            case "health" -> health = null;
            case "headroom" -> headroom = new Headroom(99, CAP, CAP, NOW, Duration.ofSeconds(5), true);
            case "account-stale" -> headroom = new Headroom(100, CAP, CAP, NOW.minusSeconds(5), Duration.ofSeconds(5), true);
            case "account-unreadable" -> headroom = new Headroom(100, CAP, CAP, NOW, Duration.ofSeconds(5), false);
            case "buffer" -> execution = new OrderExecutionProperties(true, Set.of(SBIN.id()), 100, CAP, Duration.ofSeconds(60), Duration.ofSeconds(5),
                    new RiskLimits(true, 100, CAP, 100, CAP, Duration.ofSeconds(5), Duration.ofSeconds(60), new BigDecimal("1.25"), BigDecimal.ONE), "test");
            default -> fail(change);
        }
        assertFalse(planner.stillCurrent(before, plan(now)));
    }
    @Test void stricterHeadroomAndArmWinAndZeroNeverBecomesAnOrder() {
        live = new LiveTestProperties(true, Set.of(SBIN.id()), 100, CAP, Duration.ofSeconds(10));
        headroom = new Headroom(8, new BigDecimal("6000"), new BigDecimal("5000"), NOW, Duration.ofSeconds(5), true);
        assertEquals(5, plan(NOW).quantity()); assertEquals(Duration.ofSeconds(10), plan(NOW).armDuration());
        tick = new Tick(SBIN.id(), new BigDecimal("10000"), NOW);
        assertEquals(Status.NOT_ELIGIBLE, plan(NOW).status()); assertEquals(0, plan(NOW).quantity());
    }
    @ParameterizedTest @ValueSource(strings={"normal-quantity", "live-quantity", "risk-quantity", "normal-money", "live-money", "risk-money", "exposure"})
    void everyIndependentStricterPlatformLimitWins(String cap) {
        var stricter = new BigDecimal("4400");
        risk = new RiskLimits(true, cap.equals("risk-quantity") ? 5 : 100, cap.equals("risk-money") ? stricter : CAP,
                100, cap.equals("exposure") ? stricter : CAP, Duration.ofSeconds(5), Duration.ofSeconds(60), new BigDecimal("1.10"), BigDecimal.ONE);
        execution = new OrderExecutionProperties(true, Set.of(SBIN.id()), cap.equals("normal-quantity") ? 5 : 100,
                cap.equals("normal-money") ? stricter : CAP, Duration.ofSeconds(60), Duration.ofSeconds(5), risk, "test");
        live = new LiveTestProperties(true, Set.of(SBIN.id()), cap.equals("live-quantity") ? 5 : 100,
                cap.equals("live-money") ? stricter : CAP, Duration.ofSeconds(30));
        assertEquals(5, plan(NOW).quantity()); assertEquals(CAP, plan(NOW).humanCeiling());
    }
}
