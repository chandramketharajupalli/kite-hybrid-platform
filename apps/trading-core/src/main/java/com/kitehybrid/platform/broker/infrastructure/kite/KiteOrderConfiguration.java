package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.order.application.OrderExecutionGateway;
import com.kitehybrid.platform.order.application.OrderExecutionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KiteOrderConfiguration {
    @Bean @ConditionalOnProperty(prefix = "kite.order-execution", name = "enabled", havingValue = "true")
    OrderExecutionGateway kiteOrderAdapter(KiteRestTransport transport, InstrumentRegistry instruments,
                                           OrderExecutionProperties properties) {
        return new KiteOrderAdapter(transport, instruments, properties);
    }
}
