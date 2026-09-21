package com.kitehybrid.platform.strategy.infrastructure;

import com.kitehybrid.platform.order.application.OrderApplicationService;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.strategy.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.kitehybrid.platform.config.TradingProperties;

@Configuration(proxyBeanMethods = false)
public class StrategyConfiguration {
    @Bean @ConditionalOnBean(JdbcTemplate.class)
    StrategyEvaluationStore strategyEvaluationStore(JdbcTemplate jdbc) { return new PostgresStrategyEvaluationStore(jdbc); }
    @Bean @ConditionalOnBean({StrategyEvaluationStore.class, OrderApplicationService.class, TradingProperties.class})
    StrategyOrderCoordinator strategyOrderCoordinator(StrategyEvaluationStore store, OrderApplicationService orders,
            Optional<RiskService> risk, Clock clock, TradingProperties trading, MeterRegistry metrics) {
        return new StrategyOrderCoordinator(store, orders, risk, clock, trading, metrics);
    }
}
