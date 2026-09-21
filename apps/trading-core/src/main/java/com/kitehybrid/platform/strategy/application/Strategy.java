package com.kitehybrid.platform.strategy.application;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.strategy.domain.*;

public interface Strategy {
    StrategyDefinition definition();
    Signal evaluate(StrategyInput input);
}
