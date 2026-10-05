package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enabling the read ports creates no requests. Authentication retains its existing owner. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kite.trading-read", name = "enabled", havingValue = "true")
public class KiteTradingReadConfiguration {
    @Bean com.kitehybrid.platform.broker.application.read.OrderMarginEstimator orderMarginEstimator(
            KiteRestTransport transport,KiteSession session,java.time.Clock clock) {
        return new KiteOrderMarginAdapter(transport,session,clock);
    }
    @Bean KiteTradingReadAdapter kiteTradingReadAdapter(KiteRestTransport transport, KiteSession session,
            InstrumentRegistry instruments, MeterRegistry metrics) {
        return new KiteTradingReadAdapter(transport, session, new KiteTradingReadMapper(instruments), metrics);
    }
}
