package com.kitehybrid.platform.order.infrastructure;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.order.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession;
import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.risk.application.RiskDecisionStore;
import com.kitehybrid.platform.risk.domain.RiskLimits;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(com.kitehybrid.platform.config.RuntimeTradingHaltConfiguration.class)
@EnableConfigurationProperties(OrderExecutionConfigurationProperties.class)
public class OrderConfiguration {
    @Bean
    OrderExecutionProperties orderExecutionProperties(OrderExecutionConfigurationProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.risk.domain.RiskLimits> limits) {
        var ids = properties.getAllowedInstruments().stream().map(com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId::new).collect(java.util.stream.Collectors.toSet());
        return new OrderExecutionProperties(properties.isEnabled(), ids, properties.getMaxQuantity(), properties.getMaxNotional(), properties.getRiskDecisionMaxAge(), properties.getMarketDataMaxAge(), limits.getIfAvailable(), "phase106-conservative");
    }

    @Bean RuntimeExecutionArming runtimeExecutionArming(MeterRegistry metrics, KiteAuthenticationSession session, RuntimeTradingHalt halt) { return new RuntimeExecutionArming(metrics, session::executionIdentity, halt); }

    @Bean @Profile("!test")
    ExecutionAuthorizationAuditStore executionAuthorizationAuditStore(JdbcTemplate jdbc) {
        return new PostgresExecutionAuthorizationAuditStore(jdbc);
    }

    @Bean @Profile("!test")
    ExecutionSafetyPolicy executionSafetyPolicy(OrderExecutionProperties properties, RuntimeExecutionArming arm,
            RuntimeTradingHalt halt, KiteAuthenticationSession session, RiskDecisionStore risks, InstrumentRegistry instruments,
            LatestMarketDataStore market, MarketDataGateway gateway, OrderRepository orders, Clock clock, MeterRegistry metrics,
            ExecutionAuthorizationAuditStore audit, AdditionalExecutionChecks additional, AccountExecutionChecks accounts,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.shared.application.ExecutionInitialization> initialization) {
        return new ExecutionSafetyPolicy(properties, arm, halt, session, risks, instruments, market,
                gateway::health, orders, clock, metrics, audit, additional, accounts,
                () -> initialization.getIfAvailable(() -> () -> false).initializationReady());
    }

    @Bean @Profile("!test")
    AccountExecutionChecks accountExecutionChecks(
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.broker.application.read.BrokerPositionsProvider> positions,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.broker.application.read.BrokerHoldingsProvider> holdings,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.broker.application.read.BrokerMarginsProvider> margins,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.broker.application.read.BrokerOrdersProvider> orders,
            InstrumentRegistry instruments, LatestMarketDataStore market, OrderExecutionProperties properties, Clock clock,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.broker.application.read.OrderMarginEstimator> estimator) {
        var p=positions.getIfAvailable(); var h=holdings.getIfAvailable(); var m=margins.getIfAvailable(); var o=orders.getIfAvailable();
        if (p==null || h==null || m==null || o==null || properties.riskLimits()==null) return AccountExecutionChecks.UNAVAILABLE;
        return new CurrentAccountExecutionChecks(p,h,m,o,instruments,market,properties.riskLimits(),clock,estimator.getIfAvailable(()->com.kitehybrid.platform.broker.application.read.OrderMarginEstimator.UNAVAILABLE));
    }

    @Bean @Profile("!test")
    OrderRepository orderRepository(JdbcTemplate jdbc) { return new PostgresOrderRepository(jdbc); }

    @Bean @ConditionalOnProperty(prefix = "kite.order-execution", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrderExecutionGateway disabledOrderExecutionGateway() { return new DisabledOrderExecutionGateway(); }

    @Bean @Profile("!test")
    OrderApplicationService orderApplicationService(OrderRepository repository, InstrumentRegistry instruments,
            OrderExecutionGateway gateway, OrderExecutionProperties properties, Clock clock, MeterRegistry metrics,
            ExecutionSafetyPolicy safety) {
        return new OrderApplicationService(repository, new OrderCommandValidator(instruments), gateway,
                properties, clock, metrics, safety);
    }
}
