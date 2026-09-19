package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.account.application.*;
import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.observability.KiteReadOperations;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KiteProperties.class)
public class KiteInfrastructureConfiguration {
    @Bean KiteSession kiteSession(KiteProperties properties) { return new KiteSession(properties); }
    @Bean KiteRestTransport kiteRestTransport(KiteSession session) { return KiteRestTransport.production(session); }
    @Bean BrokerProfileProvider brokerProfileProvider(KiteRestTransport transport, KiteSession session) {
        return new KiteProfileAdapter(transport, session);
    }
    @Bean InstrumentMasterProvider instrumentMasterProvider(KiteRestTransport transport) {
        return new KiteInstrumentMasterAdapter(transport);
    }
    @Bean InstrumentRegistry instrumentRegistry() { return new InMemoryInstrumentRegistry(); }
    @Bean ValidateBrokerProfileUseCase validateBrokerProfile(BrokerProfileProvider provider) {
        return new ValidateBrokerProfileUseCase(provider);
    }
    @Bean RefreshInstrumentRegistryUseCase refreshInstrumentRegistry(InstrumentMasterProvider provider,
                                                                    InstrumentRegistry registry, Clock clock) {
        return new RefreshInstrumentRegistryUseCase(provider, registry, clock);
    }
    @Bean KiteReadOperations kiteReadOperations(ValidateBrokerProfileUseCase profile,
            RefreshInstrumentRegistryUseCase refresh, InstrumentRegistry registry, MeterRegistry metrics) {
        return new KiteReadOperations(profile, refresh, registry, metrics);
    }
}
