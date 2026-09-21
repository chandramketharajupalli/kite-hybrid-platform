package com.kitehybrid.platform.strategy;

import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.strategy.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceThresholdStrategyTest {
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final InstrumentId INSTRUMENT = new InstrumentId(UUID.randomUUID());
    private final MarketDataHealth fresh = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
            MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
    private final ReferenceThresholdStrategy strategy = new ReferenceThresholdStrategy(new StrategyDefinition(new StrategyId("reference"), "v1"), INSTRUMENT, new BigDecimal("100"), 2);
    @Test void deterministicThresholdProducesBuySellAndExactBoundaryHold() {
        assertEquals(com.kitehybrid.platform.order.domain.Signal.Side.BUY, strategy.evaluate(input("99")).side());
        assertEquals(com.kitehybrid.platform.order.domain.Signal.Side.SELL, strategy.evaluate(input("101")).side());
        assertEquals(com.kitehybrid.platform.order.domain.Signal.Side.HOLD, strategy.evaluate(input("100")).side());
    }
    @Test void staleOrMissingDataProducesNoAction() {
        var stale = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.STALE,
                MarketDataHealth.Reason.NONE, Optional.empty(), Optional.empty(), Optional.empty(), 1, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(com.kitehybrid.platform.order.domain.Signal.Side.HOLD, strategy.evaluate(new StrategyInput(INSTRUMENT, Optional.empty(), stale, NOW)).side());
        assertEquals(com.kitehybrid.platform.order.domain.Signal.Side.HOLD, strategy.evaluate(new StrategyInput(INSTRUMENT, Optional.of(new Tick(INSTRUMENT, BigDecimal.ONE, NOW)), stale, NOW)).side());
    }
    private StrategyInput input(String price) { return new StrategyInput(INSTRUMENT, Optional.of(new Tick(INSTRUMENT, new BigDecimal(price), NOW)), fresh, NOW); }
}
