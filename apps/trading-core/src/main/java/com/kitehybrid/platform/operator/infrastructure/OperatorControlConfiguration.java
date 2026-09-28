package com.kitehybrid.platform.operator.infrastructure;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.order.application.*;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties({OperatorControlConfigurationProperties.class, LiveTestConfigurationProperties.class})
public class OperatorControlConfiguration {
    @Bean LiveTestProperties liveTestProperties(LiveTestConfigurationProperties properties) { return properties.domain(); }
    @Bean @Profile("!test")
    OperationalReadiness operationalReadiness(JdbcTemplate jdbc, ObjectProvider<BrokerOrdersProvider> orders,
            ObjectProvider<BrokerTradesProvider> trades, ObjectProvider<org.flywaydb.core.Flyway> flyways) {
        var flyway = flyways.getIfAvailable();
        String schema = null;
        String table = "flyway_schema_history";
        if (flyway != null) {
            var configuration = flyway.getConfiguration();
            // Separate Flyway credentials/datasources can resolve a different $user/search_path.
            // Do not certify the application's schema using evidence from an unverified connection.
            if (configuration.getDataSource() != jdbc.getDataSource())
                return () -> new OperationalReadiness.Evidence(false, false, false, false);
            schema = configuration.getDefaultSchema();
            if ((schema == null || schema.isBlank()) && configuration.getSchemas().length > 0)
                schema = configuration.getSchemas()[0];
            table = configuration.getTable();
        }
        return new PostgresOperationalReadiness(jdbc, () -> orders.getIfAvailable() != null && trades.getIfAvailable() != null,
                schema, table);
    }
    @Bean @Profile("!test")
    AdditionalExecutionChecks liveTestExecutionChecks(OperatorControlConfigurationProperties operator, LiveTestProperties live,
            RuntimeExecutionArming arm, OperationalReadiness operational, Clock clock) {
        return new LiveTestExecutionChecks(operator.enabled(), live, arm, operational, clock);
    }
    @Bean @Profile("!test")
    OperatorExecutionService operatorExecutionService(OperatorControlConfigurationProperties operator, OrderExecutionProperties execution,
            LiveTestProperties live, RuntimeExecutionArming arm, KiteAuthenticationSession session, RuntimeTradingHalt halt,
            OrderRepository orders, ExecutionSafetyPolicy policy, OrderApplicationService application, Clock clock) {
        return new OperatorExecutionService(operator.enabled(), execution, live, arm, session, halt,
                orders, policy, application, clock);
    }
}
