package com.kitehybrid.platform.risk.infrastructure;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.risk.application.*;
import com.kitehybrid.platform.risk.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wiring for risk only. Constructing these beans performs no broker I/O and enables no trading. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskConfigurationProperties.class)
public class RiskConfiguration {
    @Bean RiskLimits riskLimits(RiskConfigurationProperties properties) { return properties.limits(); }

    @Bean @Profile("!test")
    RiskDecisionStore riskDecisionStore(JdbcTemplate jdbc, OrderRepository orders) {
        return new PostgresRiskDecisionStore(jdbc, orders);
    }

    @Bean @Profile("!test")
    @ConditionalOnProperty(prefix = "kite.trading-read", name = "enabled", havingValue = "true")
    RiskService riskService(RiskDecisionStore decisions, RiskLimits limits, RiskEngine engine,
                            InstrumentRegistry instruments, LatestMarketDataStore market,
                            MarketDataGateway marketGateway, BrokerPositionsProvider positions,
                            BrokerHoldingsProvider holdings, BrokerMarginsProvider margins,
                            BrokerOrdersProvider orders, RuntimeTradingHalt halt,
                            Clock clock, MeterRegistry metrics, org.springframework.beans.factory.ObjectProvider<OrderMarginEstimator> estimator) {
        return new RiskService(decisions, engine, limits, halt, instruments, market,
                marketGateway::health, positions, holdings, margins, orders, clock, metrics,estimator.getIfAvailable(()->OrderMarginEstimator.UNAVAILABLE));
    }
}
