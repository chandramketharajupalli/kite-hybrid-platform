package com.kitehybrid.platform.trading.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/** External, owner-approved development settings. Neither a token nor a tenant approval service. */
@ConfigurationProperties("operator.entra-development")
public record EntraDevelopmentAuthenticationProperties(
        boolean enabled, String issuer, String clientId, String subject, String callback,
        String authenticationContext, String approvalFile, String approvalSha256,
        String clientCredentialFile, String databaseUrl, String databaseUser, String databasePasswordFile,
        String policyVersion, String selectedAccount, Set<String> accounts, Set<Permission> permissions,
        Duration absoluteLifetime, Duration inactivity, Duration authenticationAge) {
    public EntraDevelopmentAuthenticationProperties {
        accounts = accounts == null ? Set.of() : Set.copyOf(accounts);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
    public void validate(Environment environment) {
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        if (!enabled || !profiles.contains("development") || !profiles.contains("entra-development")
                || profiles.contains("production")) throw invalid();
        for (String flag : Set.of("kite.rest-enabled", "kite.market-data.enabled", "kite.trading-read.enabled",
                "kite.order-execution.enabled", "kite.operator-control.enabled", "kite.live-test.enabled",
                "trading.enable-live-trading", "risk.enabled"))
            if (environment.getProperty(flag, Boolean.class, false)) throw invalid();
        if (!"PAPER".equals(environment.getProperty("trading.mode", "PAPER"))
                || !environment.getProperty("trading.emergency-stop", Boolean.class, true)) throw invalid();
        if (!"127.0.0.1".equals(environment.getProperty("server.address"))
                || !environment.getProperty("server.ssl.enabled", Boolean.class, false)
                || !environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)
                || !environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class, false)
                || !"lax".equalsIgnoreCase(environment.getProperty("server.servlet.session.cookie.same-site"))
                || !"JSESSIONID".equals(environment.getProperty("server.servlet.session.cookie.name", "JSESSIONID"))
                || !"/".equals(environment.getProperty("server.servlet.session.cookie.path", "/"))
                || environment.getProperty("server.servlet.session.cookie.domain") != null
                || !"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy"))
                || environment.getProperty("spring.flyway.enabled", Boolean.class, true)) throw invalid();
        URI uri = URI.create(callback);
        int port = environment.getProperty("server.port", Integer.class, 8443);
        if (!uri.equals(URI.create("https://localhost:" + port + EntraDevelopmentAuthenticationController.ROOT + "/callback"))
                || databaseUrl == null
                || !databaseUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/phase1447o_[a-z0-9_]{1,40}")
                || databaseUser == null || !databaseUser.matches("[a-z_][a-z0-9_]{0,62}")
                || accounts == null || !accounts.contains(selectedAccount)) throw invalid();
        policy();
        verifyApproval();
    }
    public OperatorSessionRegistryPort.Policy localPolicy() {
        return new OperatorSessionRegistryPort.Policy(issuer, subject, clientId, policyVersion,
                "dedicated-development-registry", absoluteLifetime, inactivity, Duration.ofSeconds(5),
                permissions, accounts);
    }
    public EntraOperatorAuthenticationPolicy policy() {
        return new EntraOperatorAuthenticationPolicy(URI.create(issuer), clientId, URI.create(callback),
                Set.of(subject), Set.of("RS256"), authenticationAge, Duration.ofSeconds(30),
                Duration.ofMinutes(3), 32, authenticationContext, approvalSha256, localPolicy());
    }
    /** Approval is independently supplied by the owner, never inferred from token acrs. */
    public void verifyApproval() {
        try {
            byte[] bytes = externalBytes(approvalFile, 16384);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!digest.equals(approvalSha256)) throw invalid();
            var node = new ObjectMapper().readTree(bytes);
            if (!issuer.equals(node.path("issuer").asText()) || !clientId.equals(node.path("clientId").asText())
                    || !subject.equals(node.path("subject").asText())
                    || !authenticationContext.equals(node.path("authenticationContext").asText())
                    || !node.path("enforcedAuthenticationStrengthReviewed").asBoolean(false)
                    || !policyVersion.equals(node.path("policyVersion").asText())) throw invalid();
        } catch (Exception failure) { throw invalid(); }
    }
    static byte[] externalBytes(String value, int maximum) {
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute()) throw invalid();
            for (Path part = path; part != null; part = part.getParent()) {
                if (Files.isSymbolicLink(part) || !Files.exists(part, LinkOption.NOFOLLOW_LINKS)) throw invalid();
                var attributes = Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) throw invalid();
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) throw invalid();
            try (var in = Files.newInputStream(path)) {
                byte[] bytes = in.readNBytes(maximum + 1);
                if (bytes.length == 0 || bytes.length > maximum) throw invalid();
                return bytes;
            }
        } catch (UnsupportedOperationException unsupported) {
            // Unsupported path-safety checks fail closed rather than accepting redirected secret files.
            throw invalid();
        } catch (Exception failure) { throw invalid(); }
    }
    static String externalSecret(String path) {
        String value = new String(externalBytes(path, 8192), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Development authentication configuration unavailable");
    }
    @Override public String toString() { return "EntraDevelopmentAuthenticationProperties[REDACTED]"; }
}
