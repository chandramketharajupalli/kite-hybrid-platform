package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.account.application.*;
import com.kitehybrid.platform.broker.application.auth.*;
import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.observability.KiteReadOperations;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({KiteProperties.class, KiteAuthenticationProperties.class})
public class KiteInfrastructureConfiguration {
    @Bean KiteSession kiteSession(KiteProperties properties, Clock clock) { return new KiteSession(properties, clock); }
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
    @Bean KiteAccessTokenStore kiteAccessTokenStore(ObjectProvider<JdbcTemplate> jdbc,
            KiteProperties properties, KiteAuthenticationProperties auth) {
        JdbcTemplate database = jdbc.getIfAvailable();
        if (database != null) return new PostgresKiteAccessTokenStore(database,
                KiteAuthenticationAdapter.digest(properties.apiKey()), auth.encryptionKey());
        // The isolated test profile has no database. Never fall back to nondurable storage.
        return new KiteAccessTokenStore() {
            private KiteTokenStoreException unavailable() {
                return new KiteTokenStoreException(KiteTokenStoreException.Category.STORAGE);
            }
            @Override public Optional<KiteAccessToken> loadCurrent() { throw unavailable(); }
            @Override public void save(KiteAccessToken token) { throw unavailable(); }
            @Override public void clear() { throw unavailable(); }
        };
    }
    @Bean KiteAuthenticationGateway kiteAuthenticationGateway(KiteProperties properties,
            KiteAuthenticationProperties auth, Clock clock) {
        return new KiteAuthenticationAdapter(properties, auth, KiteRestTransport.productionClient(), clock);
    }
    @Bean KiteLoginAttemptStore kiteLoginAttemptStore(ObjectProvider<JdbcTemplate> jdbc, KiteProperties properties) {
        JdbcTemplate database = jdbc.getIfAvailable();
        if (database != null) return new PostgresKiteLoginAttemptStore(database,
                KiteAuthenticationAdapter.digest(properties.apiKey()));
        // The isolated test profile must explicitly mock this port; never use an in-memory production fallback.
        return new KiteLoginAttemptStore() {
            @Override public void create(KiteLoginAttempt attempt) { throw new KiteLoginAttemptStoreException(); }
            @Override public boolean consume(String digest, java.time.Instant now) {
                throw new KiteLoginAttemptStoreException();
            }
            @Override public int deleteExpired(java.time.Instant now, int limit) {
                throw new KiteLoginAttemptStoreException();
            }
        };
    }
    @Bean KiteLoginAttemptUseCase kiteLoginAttemptUseCase(KiteLoginAttemptStore store, Clock clock) {
        return new KiteLoginAttemptUseCase(store, clock);
    }
    @Bean KiteAuthenticationUseCase kiteAuthenticationUseCase(KiteAuthenticationGateway gateway,
            KiteSession session, KiteAccessTokenStore store, ValidateBrokerProfileUseCase profile,
            RefreshInstrumentRegistryUseCase refresh, Clock clock) {
        return new KiteAuthenticationUseCase(gateway, session, store, profile, refresh, clock);
    }
    @Bean ApplicationRunner restoreKiteAuthentication(KiteAuthenticationUseCase authentication) {
        return arguments -> {
            var status = authentication.restore();
            LoggerFactory.getLogger(KiteInfrastructureConfiguration.class)
                    .info("Kite authentication status={} loginEndpoint={}", status.code(), status.loginUrl());
        };
    }
}
