package com.kitehybrid.platform.strategy;

import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.application.OrderApplicationService;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.PlaceOrder;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.shared.domain.TradingMode;
import com.kitehybrid.platform.strategy.application.*;
import com.kitehybrid.platform.strategy.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyReplayAuditTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 128})
    void replayAfterClaimUsesPersistedSignalTerms(int length) {
        var eventKey = "e".repeat(length);
        var now = Instant.parse("2026-09-26T10:00:00Z");
        var instrument = new InstrumentId(UUID.randomUUID());
        var definition = new StrategyDefinition(new StrategyId("reference"), "v1");
        var signal = new Signal(new SignalId(UUID.randomUUID()), definition.id(), instrument,
                Signal.Side.BUY, 1, BigDecimal.TEN, now, "v1", Signal.Reason.BELOW_THRESHOLD);
        var persisted = new StrategyEvaluation(eventKey, definition.id(), "v1", signal,
                Optional.of(new OrderIntentId(UUID.randomUUID())), Optional.empty(), now);
        var store = mock(StrategyEvaluationStore.class);
        when(store.claim(any())).thenReturn(StrategyEvaluationStore.Claim.EXISTING);
        when(store.find(eventKey, "reference", "v1")).thenReturn(Optional.of(persisted));
        when(store.attachOrder(any(), any())).thenReturn(true);
        var orders = mock(OrderApplicationService.class);
        when(orders.place(any())).thenAnswer(call -> {
            PlaceOrder command = call.getArgument(0);
            assertEquals(1, command.quantity(), "replay must retain persisted quantity");
            var result = mock(OrderRecord.class);
            when(result.id()).thenReturn(new OrderId(UUID.randomUUID()));
            return result;
        });
        var health = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(now), Optional.of(now), Optional.of(now),
                1, 1, 0, 1, 1, 0, 0, 0, 0);
        var coordinator = new StrategyOrderCoordinator(store, orders, Optional.empty(), Clock.fixed(now, ZoneOffset.UTC),
                new TradingProperties(TradingMode.PAPER, false, false), new SimpleMeterRegistry());
        var completed = coordinator.evaluate(eventKey, new ReferenceThresholdStrategy(definition, instrument, new BigDecimal("20"), 1),
                new StrategyInput(instrument, Optional.of(new Tick(instrument, BigDecimal.TEN, now)), health, now.plusSeconds(1)));
        assertEquals(persisted.evaluatedAt(), completed.evaluatedAt());
        verify(orders).place(any());
        // The PostgreSQL store detects changed terms; the application must stop before proposing anything.
        clearInvocations(orders);
        when(store.claim(any())).thenReturn(StrategyEvaluationStore.Claim.CONFLICT);
        assertEquals("STRATEGY_EVALUATION_CONFLICT", assertThrows(IllegalStateException.class,
                () -> coordinator.evaluate(eventKey,
                        new ReferenceThresholdStrategy(definition, instrument, new BigDecimal("20"), 99),
                        new StrategyInput(instrument, Optional.of(new Tick(instrument, BigDecimal.TEN, now)), health, now))).getMessage());
        verifyNoInteractions(orders);
    }
}
