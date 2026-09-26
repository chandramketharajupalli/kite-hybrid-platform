package com.kitehybrid.platform.reconciliation.infrastructure;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.reconciliation.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only wiring; it has no execution gateway and performs no startup broker reads. */
@Configuration(proxyBeanMethods = false)
public class ReconciliationConfiguration {
    @Bean @Profile("!test")
    ReconciliationStore reconciliationStore(JdbcTemplate jdbc) { return new PostgresReconciliationStore(jdbc); }

    @Bean @Profile("!test")
    @ConditionalOnProperty(prefix = "kite.trading-read", name = "enabled", havingValue = "true")
    OrderReconciliationService orderReconciliationService(OrderRepository orders, BrokerOrdersProvider brokerOrders,
            BrokerTradesProvider brokerTrades, ReconciliationStore store, Clock clock, MeterRegistry metrics) {
        return new OrderReconciliationService(orders, brokerOrders, brokerTrades, store, clock, metrics);
    }
}
