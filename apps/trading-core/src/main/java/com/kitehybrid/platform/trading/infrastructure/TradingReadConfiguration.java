package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.TradingProjectionService;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/** Deny-only boundary. No identity provider, generated user, login flow or protected data grant. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class TradingReadConfiguration {
    /** Also prevents Boot from installing a generated-password authentication fallback. */
    @Bean
    AuthenticationManager tradingRejectingAuthenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException("Operator authentication is not established");
        };
    }

    @Bean
    @ConditionalOnMissingBean(TradingProjectionService.class)
    TradingProjectionService tradingProjectionService() {
        return TradingProjectionService.unavailable();
    }

    @Bean
    @Order(1)
    SecurityFilterChain tradingBoundary(HttpSecurity http,
            AuthenticationManager tradingRejectingAuthenticationManager) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/api/trading/**"));
        http.authenticationManager(tradingRejectingAuthenticationManager);
        http.authorizeHttpRequests(rules -> rules
                .requestMatchers(new AntPathRequestMatcher("/api/trading/status", "GET")).permitAll()
                .anyRequest().denyAll());
        http.httpBasic(basic -> basic.disable());
        http.formLogin(form -> form.disable());
        http.logout(logout -> logout.disable());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) ->
                        error(response, 401, "OPERATOR_AUTHENTICATION_REQUIRED"))
                .accessDeniedHandler((request, response, error) ->
                        error(response, 403, "TRADING_ACCESS_DENIED")));
        // CSRF remains enabled. No catch-all chain: existing broker controls remain responsible.
        // Permissions never override the absent production identity/activation grant.
        return http.build();
    }

    private static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"schemaVersion\":\"TradingApi.v1\",\"code\":\"" + code
                + "\",\"correlationId\":\"" + UUID.randomUUID() + "\"}");
    }
}
