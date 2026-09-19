package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiskEngineTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private Signal signal(String price) {
        return new Signal(new SignalId(UUID.randomUUID()), new StrategyId("test"),
                new InstrumentId(UUID.randomUUID()), Signal.Side.BUY, 1, new BigDecimal(price), clock.instant());
    }
    @Test void emergencyStopOverridesOtherwiseValidSignalAndReadsCurrentState() {
        var halted = new AtomicBoolean(true);
        var engine = new RiskEngine(List.of(new EmergencyStopRiskRule(halted::get),
                new PositiveReferencePriceRiskRule()), clock);
        assertEquals("EMERGENCY_STOP", engine.evaluate(signal("123.45")).reason());
        halted.set(false);
        assertTrue(engine.evaluate(signal("123.45")).approved());
        assertFalse(engine.evaluate(signal("0")).approved());
        assertEquals(clock.instant(), engine.evaluate(signal("1")).evaluatedAt());
    }
    @Test void exceptionsNullResultsAndInvalidSignalsFailClosed() {
        RiskRule failing = signal -> { throw new IllegalStateException("upstream details"); };
        var engine = new RiskEngine(List.of(failing), clock);
        assertEquals("RISK_EVALUATION_ERROR", engine.evaluate(signal("1")).reason());
        assertFalse(engine.evaluate(null).approved());
        assertFalse(new RiskEngine(List.of(s -> null), clock).evaluate(signal("1")).approved());
        assertThrows(IllegalArgumentException.class, () -> new RiskEngine(List.of(), clock));
    }
    @Test void rejectionStopsFurtherEvaluation() {
        var evaluated = new AtomicBoolean(false);
        var engine = new RiskEngine(List.of(s -> Optional.of("DENIED"), s -> {
            evaluated.set(true); return Optional.empty();
        }), clock);
        assertFalse(engine.evaluate(signal("1")).approved());
        assertFalse(evaluated.get());
    }
}
