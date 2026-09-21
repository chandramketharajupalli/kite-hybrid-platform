package com.kitehybrid.platform.risk.infrastructure;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.risk.application.*;
import com.kitehybrid.platform.risk.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wiring for risk only. Constructing these beans performs no broker I/O and enables no trading. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskConfigurationProperties.class)
public class RiskConfiguration {
    @Bean RiskLimits riskLimits(RiskConfigurationProperties properties) { return properties.limits(); }

    @Bean @ConditionalOnBean({JdbcTemplate.class, OrderRepository.class})
    RiskDecisionStore riskDecisionStore(JdbcTemplate jdbc, OrderRepository orders) {
        return new PostgresRiskDecisionStore(jdbc, orders);
    }

    @Bean @ConditionalOnBean({RiskDecisionStore.class, RiskLimits.class, InstrumentRegistry.class,
            LatestMarketDataStore.class, MarketDataGateway.class, BrokerPositionsProvider.class,
            BrokerHoldingsProvider.class, BrokerMarginsProvider.class, BrokerOrdersProvider.class})
    RiskService riskService(RiskDecisionStore decisions, RiskLimits limits, RiskEngine engine,
                            InstrumentRegistry instruments, LatestMarketDataStore market,
                            MarketDataGateway marketGateway, BrokerPositionsProvider positions,
                            BrokerHoldingsProvider holdings, BrokerMarginsProvider margins,
                            BrokerOrdersProvider orders, com.kitehybrid.platform.config.TradingProperties trading,
                            Clock clock, MeterRegistry metrics) {
        return new RiskService(decisions, engine, limits, trading::emergencyStop, instruments, market,
                marketGateway::health, positions, holdings, margins, orders, clock, metrics);
    }
}
