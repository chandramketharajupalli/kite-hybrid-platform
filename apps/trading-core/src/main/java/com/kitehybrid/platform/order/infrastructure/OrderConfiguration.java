package com.kitehybrid.platform.order.infrastructure;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.order.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@EnableConfigurationProperties(OrderExecutionConfigurationProperties.class)
public class OrderConfiguration {
    @Bean
    OrderExecutionProperties orderExecutionProperties(OrderExecutionConfigurationProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.kitehybrid.platform.risk.domain.RiskLimits> limits) {
        var ids = properties.getAllowedInstruments().stream().map(com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId::new).collect(java.util.stream.Collectors.toSet());
        return new OrderExecutionProperties(properties.isEnabled(), ids, properties.getMaxQuantity(), properties.getMaxNotional(), properties.getRiskDecisionMaxAge(), properties.getMarketDataMaxAge(), limits.getIfAvailable(() -> null) == null ? "" : limits.getIfAvailable().version(), "phase9");
    }

    @Bean RuntimeExecutionArming runtimeExecutionArming(MeterRegistry metrics) { return new RuntimeExecutionArming(metrics); }

    @Bean @ConditionalOnBean(JdbcTemplate.class)
    ExecutionAuthorizationAuditStore executionAuthorizationAuditStore(JdbcTemplate jdbc) {
        return new PostgresExecutionAuthorizationAuditStore(jdbc);
    }

    @Bean @ConditionalOnBean({OrderRepository.class, RiskDecisionStore.class, RiskLimits.class, InstrumentRegistry.class,
            LatestMarketDataStore.class, MarketDataGateway.class, KiteAuthenticationSession.class})
    ExecutionSafetyPolicy executionSafetyPolicy(OrderExecutionProperties properties, RuntimeExecutionArming arm,
            TradingProperties trading, KiteAuthenticationSession session, RiskDecisionStore risks, InstrumentRegistry instruments,
            LatestMarketDataStore market, MarketDataGateway gateway, OrderRepository orders, Clock clock, MeterRegistry metrics,
            org.springframework.beans.factory.ObjectProvider<ExecutionAuthorizationAuditStore> audit) {
        return new ExecutionSafetyPolicy(properties, arm, trading::emergencyStop, session, risks, instruments, market,
                gateway::health, orders, clock, metrics, audit.getIfAvailable(() -> ExecutionAuthorizationAuditStore.NOOP));
    }

    @Bean @ConditionalOnBean(JdbcTemplate.class)
    OrderRepository orderRepository(JdbcTemplate jdbc) { return new PostgresOrderRepository(jdbc); }

    @Bean @ConditionalOnProperty(prefix = "kite.order-execution", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrderExecutionGateway disabledOrderExecutionGateway() { return new DisabledOrderExecutionGateway(); }

    @Bean @ConditionalOnBean({OrderRepository.class, OrderExecutionGateway.class})
    OrderApplicationService orderApplicationService(OrderRepository repository, InstrumentRegistry instruments,
            OrderExecutionGateway gateway, OrderExecutionProperties properties, Clock clock, MeterRegistry metrics,
            org.springframework.beans.factory.ObjectProvider<ExecutionSafetyPolicy> safety) {
        return new OrderApplicationService(repository, new OrderCommandValidator(instruments), gateway,
                properties, clock, metrics, safety.getIfAvailable());
    }
}
