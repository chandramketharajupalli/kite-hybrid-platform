package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import jakarta.servlet.SessionTrackingMode;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/** Explicit opt-in only. An enabled production/misconfigured instance fails startup, never falls back. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "operator.entra-development", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EntraDevelopmentAuthenticationProperties.class)
public class EntraDevelopmentAuthenticationConfiguration {
    @Bean("entraDevelopmentPolicy")
    EntraDevelopmentAuthenticationProperties checkedPolicy(EntraDevelopmentAuthenticationProperties settings,
            Environment environment) {
        settings.validate(environment);
        return settings;
    }
    @Bean("entraDevelopmentClock")
    Clock clock() { return Clock.systemUTC(); }

    @Bean("entraDevelopmentRegistry")
    OperatorSessionRegistryPort registry(@Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings,
            @Qualifier("entraDevelopmentClock") Clock clock) {
        // Not a DataSource bean: cannot redirect unrelated application persistence.
        var dataSource = new DriverManagerDataSource(settings.databaseUrl(), settings.databaseUser(),
                EntraDevelopmentAuthenticationProperties.externalSecret(settings.databasePasswordFile()));
        return new PostgresOperatorSessionRegistry(dataSource, clock);
    }
    @Bean
    OperatorOidcAuthorizationRequestRepository entraRequests(
            @Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings,
            @Qualifier("entraDevelopmentClock") Clock clock) {
        return new OperatorOidcAuthorizationRequestRepository(settings.policy(), clock, new SecureRandom());
    }
    @Bean
    EntraOperatorAuthenticationAdapter entraAdapter(
            @Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings,
            @Qualifier("entraDevelopmentClock") Clock clock,
            @Qualifier("entraDevelopmentRegistry") OperatorSessionRegistryPort registry,
            OperatorOidcAuthorizationRequestRepository requests) {
        var transport = new EntraDevelopmentOidcTransport(settings.policy(), clock);
        return new EntraOperatorAuthenticationAdapter(settings.policy(), requests,
                transport.registration(EntraDevelopmentAuthenticationProperties.externalSecret(settings.clientCredentialFile())),
                transport.tokenClient(), transport.decoder(), registry, clock, new SecureRandom());
    }
    @Bean
    HttpSessionSecurityContextRepository entraDevelopmentContexts() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }
    @Bean
    ServletContextInitializer entraDevelopmentCookiePolicy(
            @Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings) {
        return context -> {
            context.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
            context.getSessionCookieConfig().setSecure(true);
            context.getSessionCookieConfig().setHttpOnly(true);
            context.getSessionCookieConfig().setPath("/");
        };
    }
    @Bean
    @Order(0)
    public SecurityFilterChain entraDevelopmentChain(HttpSecurity http,
            @Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings,
            HttpSessionSecurityContextRepository contexts) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher(EntraDevelopmentAuthenticationController.ROOT + "/**"));
        // Controllers enforce registry authority. PermitAll does not grant a Trading permission.
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        http.securityContext(c -> c.requireExplicitSave(true).securityContextRepository(contexts));
        http.sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()));
        http.httpBasic(b -> b.disable()).formLogin(f -> f.disable()).logout(l -> l.disable());
        http.requestCache(c -> c.disable());
        http.csrf(c -> { }); // No exclusions; callback is GET and consumes its bound one-time state.
        http.exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, failure) -> res.sendError(401))
                .accessDeniedHandler((req, res, failure) -> res.sendError(403)));
        return http.build();
    }
}
