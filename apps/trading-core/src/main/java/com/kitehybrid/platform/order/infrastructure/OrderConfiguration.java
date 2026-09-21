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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderExecutionConfigurationProperties.class)
public class OrderConfiguration {
    @Bean
    OrderExecutionProperties orderExecutionProperties(OrderExecutionConfigurationProperties properties) {
        return new OrderExecutionProperties(properties.isEnabled());
    }

    @Bean @ConditionalOnBean(JdbcTemplate.class)
    OrderRepository orderRepository(JdbcTemplate jdbc) { return new PostgresOrderRepository(jdbc); }

    @Bean @ConditionalOnProperty(prefix = "kite.order-execution", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrderExecutionGateway disabledOrderExecutionGateway() { return new DisabledOrderExecutionGateway(); }

    @Bean @ConditionalOnBean({OrderRepository.class, OrderExecutionGateway.class})
    OrderApplicationService orderApplicationService(OrderRepository repository, InstrumentRegistry instruments,
            OrderExecutionGateway gateway, OrderExecutionProperties properties, Clock clock, MeterRegistry metrics) {
        return new OrderApplicationService(repository, new OrderCommandValidator(instruments), gateway,
                properties, clock, metrics);
    }
}
