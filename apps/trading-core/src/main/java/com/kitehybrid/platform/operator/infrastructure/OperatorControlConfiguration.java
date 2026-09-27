package com.kitehybrid.platform.operator.infrastructure;

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
            ObjectProvider<BrokerTradesProvider> trades) {
        return new PostgresOperationalReadiness(jdbc, () -> orders.getIfAvailable() != null && trades.getIfAvailable() != null);
    }
    @Bean @Profile("!test")
    AdditionalExecutionChecks liveTestExecutionChecks(OperatorControlConfigurationProperties operator, LiveTestProperties live,
            RuntimeExecutionArming arm, OperationalReadiness operational, Clock clock) {
        return new LiveTestExecutionChecks(operator.enabled(), live, arm, operational, clock);
    }
    @Bean @Profile("!test")
    OperatorExecutionService operatorExecutionService(OperatorControlConfigurationProperties operator, OrderExecutionProperties execution,
            LiveTestProperties live, RuntimeExecutionArming arm, KiteAuthenticationSession session, TradingProperties trading,
            OrderRepository orders, ExecutionSafetyPolicy policy, OrderApplicationService application, Clock clock) {
        return new OperatorExecutionService(operator.enabled(), execution, live, arm, session, trading::emergencyStop,
                orders, policy, application, clock);
    }
}
