package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bean construction never opens a socket. Starting is always explicit after authentication and subscription. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KiteMarketDataProperties.class)
public class KiteMarketDataConfiguration {
    @Bean LatestMarketDataStore latestMarketDataStore() { return new InMemoryLatestMarketDataStore(); }
    @Bean KiteWebSocketTransport kiteWebSocketTransport(KiteSession session, KiteMarketDataProperties settings) {
        return new JdkKiteWebSocketTransport(session, settings.connectTimeout(), KiteMarketDataDecoder.MAX_FRAME_BYTES);
    }
    @Bean(destroyMethod = "close") KiteMarketDataAdapter marketDataGateway(KiteSession session,
            InstrumentRegistry instruments, LatestMarketDataStore store, KiteWebSocketTransport transport,
            KiteMarketDataProperties settings, Clock clock, MeterRegistry metrics) {
        return new KiteMarketDataAdapter(session, instruments, store, transport, settings, clock, metrics);
    }
}
