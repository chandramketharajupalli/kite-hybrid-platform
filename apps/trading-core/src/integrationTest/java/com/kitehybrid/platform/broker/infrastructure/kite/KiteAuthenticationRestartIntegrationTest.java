package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.account.application.BrokerProfileProvider;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAccessTokenStore;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationGateway;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStore;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase;
import com.kitehybrid.platform.instrument.application.InstrumentMasterProvider;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Production Spring wiring, Flyway, session, use cases and encrypted database; broker calls are fakes. */
@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class KiteAuthenticationRestartIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final String TOKEN_VALUE = "syntheticSpringRestartToken";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static TimeZone originalTimeZone;

    @BeforeAll
    static void useUtcForJdbcStartup() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreTimezone() {
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }

    @BeforeEach
    void clearDurableAuthenticationState() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()));
        jdbc.update("DELETE FROM trading.kite_login_attempts");
        jdbc.update("DELETE FROM trading.kite_access_tokens");
    }

    @Test
    void outstandingBrowserBoundAttemptSurvivesApplicationRestartAndCannotBeReplayedAfterAnotherRestart() {
        String browserNonce;
        KiteLoginAttemptUseCase firstService;
        try (ConfigurableApplicationContext first = startApplication()) {
            firstService = first.getBean(KiteLoginAttemptUseCase.class);
            assertThat(first.getBean(KiteLoginAttemptStore.class)).isInstanceOf(PostgresKiteLoginAttemptStore.class);
            assertThat(first.getBean(KiteAuthenticationUseCase.class).status().authenticated()).isFalse();

            browserNonce = firstService.start();

            assertThat(first.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM trading.kite_login_attempts", Integer.class)).isEqualTo(1);
            assertThat(first.getBean(BrokerCalls.class).exchanges.get()).isZero();
        }

        // Only PostgreSQL and the browser's nonce survive; no servlet session exists in these contexts.
        try (ConfigurableApplicationContext restarted = startApplication()) {
            var attempts = restarted.getBean(KiteLoginAttemptUseCase.class);
            assertThat(attempts).isNotSameAs(firstService);
            assertThat(restarted.getBean(KiteAuthenticationUseCase.class).status().authenticated()).isFalse();

            assertThat(attempts.validateAndConsume(browserNonce, browserNonce).accepted()).isTrue();
            var authenticated = restarted.getBean(KiteAuthenticationUseCase.class)
                    .complete("syntheticRequestToken", "success", "login", null);

            assertThat(authenticated.authenticated()).isTrue();
            assertThat(authenticated.initializationReady()).isTrue();
            assertThat(restarted.getBean(KiteSession.class).state()).isEqualTo(KiteSession.State.AUTHENTICATED);
            assertThat(restarted.getBean(BrokerCalls.class).exchanges.get()).isEqualTo(1);
            assertThat(restarted.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM trading.kite_login_attempts", Integer.class)).isZero();
            assertOnlyCiphertextIsStored(restarted);
        }

        try (ConfigurableApplicationContext restartedAgain = startApplication()) {
            var attempts = restartedAgain.getBean(KiteLoginAttemptUseCase.class);

            var replay = attempts.validateAndConsume(browserNonce, browserNonce);

            assertThat(replay.accepted()).isFalse();
            assertThat(replay.reason()).isEqualTo(KiteLoginAttemptUseCase.Reason.ATTEMPT_UNAVAILABLE);
            assertThat(replay.toString()).doesNotContain(browserNonce);
            assertThat(restartedAgain.getBean(BrokerCalls.class).exchanges.get()).isZero();
            assertThat(restartedAgain.getBean(KiteAuthenticationUseCase.class).status().authenticated()).isTrue();
        }
    }

    @Test
    void fullApplicationRestartAutomaticallyReusesDurableSessionAndResumesInitialization() {
        KiteSession originalSession;
        try (ConfigurableApplicationContext first = startApplication()) {
            var auth = first.getBean(KiteAuthenticationUseCase.class);
            var calls = first.getBean(BrokerCalls.class);
            originalSession = first.getBean(KiteSession.class);
            assertThat(first.getBean(KiteAccessTokenStore.class)).isInstanceOf(PostgresKiteAccessTokenStore.class);
            assertThat(auth.status().code()).isEqualTo("KITE_AUTH_REQUIRED");
            assertThat(auth.status().authenticated()).isFalse();
            assertThat(calls.profiles.get()).isZero();
            assertThat(calls.instruments.get()).isZero();

            var completed = auth.complete("syntheticRequestToken", "success", "login", null);

            assertThat(completed.authenticated()).isTrue();
            assertThat(completed.initializationReady()).isTrue();
            assertThat(originalSession.state()).isEqualTo(KiteSession.State.AUTHENTICATED);
            assertThat(calls.exchanges.get()).isEqualTo(1);
            assertThat(calls.profiles.get()).isEqualTo(1);
            assertThat(calls.instruments.get()).isEqualTo(1);
            assertThat(first.getBean(InstrumentRegistry.class).snapshot().size()).isEqualTo(1);
            assertOnlyCiphertextIsStored(first);
        }

        // New application, datasource, service and session instances; only PostgreSQL is retained.
        try (ConfigurableApplicationContext restarted = startApplication()) {
            var auth = restarted.getBean(KiteAuthenticationUseCase.class);
            var calls = restarted.getBean(BrokerCalls.class);

            assertThat(restarted.getBean(KiteSession.class)).isNotSameAs(originalSession);
            assertThat(auth.status().code()).isEqualTo("KITE_AUTHENTICATED");
            assertThat(auth.status().authenticated()).isTrue();
            assertThat(auth.status().initializationReady()).isTrue();
            assertThat(auth.status().loginUrl()).isNull();
            assertThat(calls.exchanges.get()).isZero();
            assertThat(calls.profiles.get()).isEqualTo(1);
            assertThat(calls.instruments.get()).isEqualTo(1);
            assertThat(restarted.getBean(InstrumentRegistry.class).snapshot().size()).isEqualTo(1);
            assertThat(restarted.getBean(KiteAccessTokenStore.class).loadCurrent().orElseThrow().value())
                    .isEqualTo(TOKEN_VALUE);
            assertOnlyCiphertextIsStored(restarted);
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(TradingCoreApplication.class, FakeBrokerConfiguration.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run("--spring.profiles.active=development",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.flyway.enabled=true",
                        "--kite.rest-enabled=true",
                        "--kite.api-key=syntheticRestartKey",
                        "--kite.api-secret=syntheticRestartSecret",
                        "--kite.access-token=",
                        "--kite.auth.encryption-key=" + ENCRYPTION_KEY,
                        "--kite.auth.redirect-url=http://localhost:8080/api/broker/kite/auth/callback",
                        "--trading.mode=PAPER",
                        "--trading.enable-live-trading=false",
                        "--trading.emergency-stop=true",
                        "--spring.main.banner-mode=off");
    }

    private static void assertOnlyCiphertextIsStored(ConfigurableApplicationContext context) {
        var jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trading.kite_access_tokens", Integer.class)).isEqualTo(1);
        byte[] ciphertext = jdbc.queryForObject("SELECT token_ciphertext FROM trading.kite_access_tokens", byte[].class);
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).doesNotContain(TOKEN_VALUE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeBrokerConfiguration {
        @Bean
        @Primary
        Clock deterministicAuthenticationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        BrokerCalls brokerCalls() { return new BrokerCalls(); }

        @Bean
        @Primary
        KiteAuthenticationGateway fakeAuthenticationGateway(BrokerCalls calls) {
            return new KiteAuthenticationGateway() {
                @Override public String loginUrl(String state) {
                    throw new AssertionError("No browser login is needed by this integration test");
                }
                @Override public KiteAccessToken exchange(String requestToken) {
                    assertThat(requestToken).isEqualTo("syntheticRequestToken");
                    calls.exchanges.incrementAndGet();
                    return new KiteAccessToken(TOKEN_VALUE, NOW, Instant.parse("2026-09-21T00:30:00Z"));
                }
            };
        }

        @Bean
        @Primary
        BrokerProfileProvider fakeBrokerProfiles(KiteSession session, BrokerCalls calls) {
            return () -> {
                assertThat(session.authorization()).isEqualTo("token syntheticRestartKey:" + TOKEN_VALUE);
                calls.profiles.incrementAndGet();
                session.profileValidated();
                return new BrokerProfile("ZERODHA", "TEST123", Set.of("NSE"));
            };
        }

        @Bean
        @Primary
        InstrumentMasterProvider fakeInstrumentMaster(KiteSession session, BrokerCalls calls) {
            return () -> {
                assertThat(session.authenticated()).isTrue();
                calls.instruments.incrementAndGet();
                return List.of(Instrument.create(new BrokerInstrumentId("KITE", "123"), "TEST", "NSE", "CASH",
                        InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1));
            };
        }
    }

    static final class BrokerCalls {
        final AtomicInteger exchanges = new AtomicInteger();
        final AtomicInteger profiles = new AtomicInteger();
        final AtomicInteger instruments = new AtomicInteger();
    }
}
