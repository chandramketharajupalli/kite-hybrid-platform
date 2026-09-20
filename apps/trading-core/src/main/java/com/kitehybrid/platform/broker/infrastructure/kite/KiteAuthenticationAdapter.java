package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.broker.application.auth.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.*;

/** Official login and one-shot session exchange only. No retries, redirects or credential login. */
final class KiteAuthenticationAdapter implements KiteAuthenticationGateway {
    private static final Logger LOG = LoggerFactory.getLogger(KiteAuthenticationAdapter.class);
    private static final int RESPONSE_LIMIT = 64 * 1024;
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
            "(?i)(?:api[\\s_.-]*secret|request[\\s_.-]*token|access[\\s_.-]*token|checksum"
                    + "|encryption[\\s_.-]*key|authorization|password|totp)[\\s\"'`]*(?:[:=]|\\bis\\b)\\s*\\S"
                    + "|\\b(?:Bearer|token)\\s+[^\\s,;]+[:][^\\s,;]+"
                    + "|\\b(?:Basic|Bearer)\\s+[^\\s,;]+");
    private static final Pattern OPAQUE_CREDENTIAL = Pattern.compile("[A-Za-z0-9_+/%=-]{24,}");
    private final KiteProperties properties;
    private final KiteAuthenticationProperties auth;
    private final RestClient client;
    private final Clock clock;

    KiteAuthenticationAdapter(KiteProperties properties, KiteAuthenticationProperties auth,
                              RestClient client, Clock clock) {
        this.properties = properties;
        this.auth = auth;
        this.client = client;
        this.clock = clock;
    }

    @Override public String loginUrl(String state) {
        requireConfigured();
        if (state == null || !state.matches("[A-Za-z0-9_-]{32,128}"))
            throw new KiteAuthenticationException(INVALID_CALLBACK);
        return "https://kite.zerodha.com/connect/login?v=3&api_key=" + properties.apiKey()
                + "&redirect_params=" + URLEncoder.encode("state=" + state, StandardCharsets.UTF_8);
    }

    @Override public KiteAccessToken exchange(String requestToken) {
        requireConfigured();
        if (requestToken == null || !requestToken.matches("[A-Za-z0-9_-]{1,256}"))
            throw new KiteAuthenticationException(INVALID_CALLBACK);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("api_key", properties.apiKey());
        form.add("request_token", requestToken);
        String checksum = digest(properties.apiKey() + requestToken + properties.apiSecret());
        form.add("checksum", checksum);
        // Use the beginning of the exchange for conservative expiry if it crosses the cutoff.
        Instant issuedAt = clock.instant();
        try {
            return client.post().uri("/session/token").header("X-Kite-Version", "3")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            logExchangeFailure(response, status, requestToken, checksum);
                            throw new KiteAuthenticationException(EXCHANGE_FAILED);
                        }
                        byte[] body = response.getBody().readNBytes(RESPONSE_LIMIT + 1);
                        if (body.length > RESPONSE_LIMIT) throw new KiteAuthenticationException(EXCHANGE_FAILED);
                        var root = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build().readTree(body);
                        if (root == null || !"success".equals(root.path("status").asText())
                                || !root.path("data").path("access_token").isTextual()
                                || !properties.apiKey().equals(root.path("data").path("api_key").asText()))
                            throw new KiteAuthenticationException(EXCHANGE_FAILED);
                        String token = root.path("data").path("access_token").textValue();
                        if (!token.matches("[A-Za-z0-9_-]{1,256}"))
                            throw new KiteAuthenticationException(EXCHANGE_FAILED);
                        return new KiteAccessToken(token, issuedAt, expiresAt(issuedAt));
                    });
        } catch (KiteAuthenticationException safe) { throw safe; }
        catch (RuntimeException failure) { throw new KiteAuthenticationException(EXCHANGE_FAILED); }
    }

    /** Broker text is untrusted: never log bodies, headers, parser failures or exception causes. */
    private void logExchangeFailure(ClientHttpResponse response, int status, String requestToken, String checksum) {
        try {
            byte[] body = response.getBody().readNBytes(RESPONSE_LIMIT + 1);
            if (body.length > RESPONSE_LIMIT) {
                logUnparseable(status);
                return;
            }
            JsonNode root = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build().readTree(body);
            if (root == null || !root.isObject() || !"error".equals(root.path("status").asText())
                    || !safeDiagnosticText(root.path("error_type"), 128)
                    || !safeDiagnosticText(root.path("message"), 1024)) {
                logUnparseable(status);
                return;
            }
            List<String> credentials = new ArrayList<>(List.of(properties.apiKey(), properties.apiSecret(),
                    properties.initialAccessToken(), auth.encryptionKey(), requestToken, checksum));
            collectReturnedCredentials(root, credentials);
            LOG.warn("Kite session exchange failed: HTTP status={}, error_type={}, message={}", status,
                    redactDiagnostic(root.path("error_type").textValue(), credentials),
                    redactDiagnostic(root.path("message").textValue(), credentials));
        } catch (IOException | RuntimeException ignored) {
            logUnparseable(status);
        }
    }

    private static boolean safeDiagnosticText(JsonNode field, int limit) {
        if (!field.isTextual() || field.textValue().isBlank() || field.textValue().length() > limit) return false;
        return field.textValue().codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || Character.getType(codePoint) == Character.LINE_SEPARATOR
                || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR);
    }

    private static void collectReturnedCredentials(JsonNode root, List<String> credentials) {
        var pending = new ArrayDeque<JsonNode>();
        pending.add(root);
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeFirst();
            node.properties().forEach(field -> {
                String name = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                if (field.getValue().isTextual() && (name.endsWith("secret") || name.endsWith("token")
                        || name.endsWith("checksum") || name.endsWith("encryptionkey")
                        || name.endsWith("authorization") || name.endsWith("apikey")))
                    credentials.add(field.getValue().textValue());
            });
            node.forEach(child -> { if (child.isContainerNode()) pending.add(child); });
        }
    }

    private static String redactDiagnostic(String text, List<String> credentials) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String credential : credentials) {
            if (!credential.isEmpty() && (lower.contains(credential.toLowerCase(Locale.ROOT))
                    || lower.contains(URLEncoder.encode(credential, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))))
                return "[REDACTED]";
        }
        // Retain useful messages such as "Invalid checksum."; redact credential values and headers.
        if (CREDENTIAL_ASSIGNMENT.matcher(text).find() || OPAQUE_CREDENTIAL.matcher(text).find())
            return "[REDACTED]";
        return text;
    }

    private static void logUnparseable(int status) {
        LOG.warn("Kite session exchange failed: HTTP status={}, unparseable Kite error response", status);
    }

    private void requireConfigured() {
        if (!properties.restEnabled()) throw new KiteAuthenticationException(DISABLED);
        if (!properties.authenticationConfigured()) throw new KiteAuthenticationException(CONFIGURATION);
        auth.requireConfigured();
    }
    static Instant expiresAt(Instant issuedAt) {
        var local = issuedAt.atZone(ZoneId.of("Asia/Kolkata"));
        var cutoff = local.toLocalDate().atTime(LocalTime.of(6, 0)).atZone(local.getZone());
        if (!cutoff.toInstant().isAfter(issuedAt)) cutoff = cutoff.plusDays(1);
        return cutoff.toInstant();
    }
    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
