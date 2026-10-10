package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** Fixed origin and allowlisted GET routes, no redirects/retries, bounded bodies and no wire logging. */
final class KiteRestTransport {
    enum Endpoint {
        PROFILE("/user/profile", 64 * 1024),
        INSTRUMENTS("/instruments", 32 * 1024 * 1024),
        ORDERS("/orders", 4 * 1024 * 1024),
        TRADES("/trades", 4 * 1024 * 1024),
        POSITIONS("/portfolio/positions", 4 * 1024 * 1024),
        HOLDINGS("/portfolio/holdings", 4 * 1024 * 1024),
        MARGINS("/user/margins", 64 * 1024),
        EQUITY_MARGINS("/user/margins/equity", 64 * 1024);
        final String path;
        final int limit;
        Endpoint(String path, int limit) { this.path = path; this.limit = limit; }
    }
    private final RestClient client;
    private final KiteSession session;
    private final boolean officialOrigin;
    private final ObjectMapper json = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    KiteRestTransport(RestClient client, KiteSession session) {
        this(client, session, false);
    }
    private KiteRestTransport(RestClient client, KiteSession session, boolean officialOrigin) {
        this.client = client;
        this.session = session;
        this.officialOrigin = officialOrigin;
    }
    static KiteRestTransport production(KiteSession session) {
        return new KiteRestTransport(productionClient(), session, true);
    }
    static KiteRestTransport controlledEquity(KiteSession session, KiteEquityReadRequestFactory wire) {
        return new KiteRestTransport(RestClient.builder().baseUrl(wire.origin().toString())
                .requestFactory(wire).build(), session, wire.officialOrigin());
    }
    boolean usesSession(KiteSession expected) { return session == expected; }
    /** Configuration provenance only; never account eligibility or independent attestation. */
    boolean officialOrigin() { return officialOrigin; }
    static RestClient productionClient() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return RestClient.builder().baseUrl("https://api.kite.trade")
                .requestFactory(factory).build();
    }

    String get(Endpoint endpoint) {
        // A late response from an old token must never invalidate a newly installed session.
        synchronized (session) { return getWithSession(endpoint); }
    }

    String postRegularOrder(Map<String, String> form, Runnable dispatchValidation) { return orderRequest("POST", "/orders/regular", form, dispatchValidation); }
    String putRegularOrder(String brokerOrderId, Map<String, String> form) {
        if (brokerOrderId == null || !brokerOrderId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new BrokerReadException(INVALID_RESPONSE);
        return orderRequest("PUT", "/orders/regular/" + brokerOrderId, form);
    }
    String deleteRegularOrder(String brokerOrderId) {
        if (brokerOrderId == null || !brokerOrderId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new BrokerReadException(INVALID_RESPONSE);
        return orderRequest("DELETE", "/orders/regular/" + brokerOrderId, Map.of());
    }

    private String orderRequest(String method, String path, Map<String, String> form) {
        return orderRequest(method, path, form, () -> { });
    }
    private String orderRequest(String method, String path, Map<String, String> form, Runnable dispatchValidation) {
        synchronized (session) {
            try {
                if (!session.authenticated()) throw new BrokerReadException(AUTHENTICATION);
                String encoded = form.entrySet().stream().map(entry ->
                        URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                                + "=" + URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
                dispatchValidation.run();
                return client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(URI.create(path))
                        .header("X-Kite-Version", "3").header("Authorization", session.authorization())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .body(encoded).exchange((request, response) -> {
                            int status = response.getStatusCode().value();
                            byte[] body = readBounded(response.getBody(), 64 * 1024);
                            String text = StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(body)).toString();
                            if (status == 401 || status == 403) {
                                session.invalidate(); throw new BrokerReadException(AUTHENTICATION, status);
                            }
                            if (status != 200) throw new BrokerReadException(BROKER_API, status);
                            return text;
                        });
            } catch (com.kitehybrid.platform.order.domain.command.OrderCommandValidationException denied) { throw denied; }
            catch (BrokerReadException safe) { throw safe; }
            catch (RuntimeException unexpected) {
                throw new BrokerReadException(TRANSPORT);
            }
        }
    }
    /** Calculation-only official endpoint. This is not an /orders mutation and never calls orderRequest. */
    String calculateOrderMargin(String jsonBody) {
        if(jsonBody==null || jsonBody.length()>8192) throw new BrokerReadException(INVALID_RESPONSE);
        synchronized(session) { return readWithSession("/margins/orders",64*1024,jsonBody); }
    }
    private String getWithSession(Endpoint endpoint) {
        return readWithSession(endpoint.path,endpoint.limit,null);
    }
    private String readWithSession(String path,int limit,String jsonBody) {
        return readWithSession(path,limit,jsonBody,true);
    }
    /** Historical GET only. Authentication failure never clears the shared/stored token. */
    String historicalMinute(String token, java.time.Instant from, java.time.Instant to) {
        if(token==null || !token.matches("[1-9][0-9]{0,9}") || Long.parseLong(token)>4294967295L
                || from==null || to==null || !from.isBefore(to)
                || java.time.Duration.between(from,to).compareTo(java.time.Duration.ofDays(1))>0)
            throw new BrokerReadException(INVALID_RESPONSE);
        var format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("Asia/Kolkata"));
        String path="/instruments/historical/"+token+"/minute?from="
                +format.format(from)+"&to="+format.format(to)+"&continuous=0&oi=0";
        synchronized(session) {
            if(!session.authenticated()) throw new BrokerReadException(AUTHENTICATION);
            return readWithSession(path,1024*1024,null,false);
        }
    }
    private String readWithSession(String path,int limit,String jsonBody,boolean invalidateAuthentication) {
        String authorization = session.authorization();
        try {
            RestClient.RequestHeadersSpec<?> requestSpec=jsonBody==null ? client.get().uri(path)
                    : client.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(jsonBody);
            return requestSpec.header("X-Kite-Version", "3").header("Authorization", authorization)
                    .header("Accept-Encoding", "gzip")
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            if(invalidateAuthentication) session.invalidate();
                            throw new BrokerReadException(AUTHENTICATION, status);
                        }
                        byte[] body = readBounded(response.getBody(), limit);
                        String encoding = response.getHeaders().getFirst("Content-Encoding");
                        boolean gzip = "gzip".equalsIgnoreCase(encoding)
                                || (body.length > 1 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b);
                        if (encoding != null && !encoding.equalsIgnoreCase("gzip")
                                && !encoding.equalsIgnoreCase("identity"))
                            throw new BrokerReadException(INVALID_RESPONSE, status);
                        if (gzip) {
                            try (var input = new GZIPInputStream(new ByteArrayInputStream(body))) {
                                body = readBounded(input, limit);
                            } catch (IOException corrupt) {
                                throw new BrokerReadException(INVALID_RESPONSE, status);
                            }
                        }
                        String text;
                        try {
                            text = StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(body)).toString();
                        } catch (CharacterCodingException invalidText) {
                            throw new BrokerReadException(INVALID_RESPONSE, status);
                        }
                        // Error messages are never retained, even if they echo tokens.
                        if (status != 200) {
                            if (isTokenError(text)) {
                                if(invalidateAuthentication) session.invalidate();
                                throw new BrokerReadException(AUTHENTICATION, status);
                            }
                            throw new BrokerReadException(BROKER_API, status);
                        }
                        if (isTokenError(text)) {
                            if(invalidateAuthentication) session.invalidate();
                            throw new BrokerReadException(AUTHENTICATION, status);
                        }
                        return text;
                    });
        } catch (BrokerReadException safe) {
            throw safe;
        } catch (RestClientException transport) {
            throw new BrokerReadException(TRANSPORT);
        } catch (RuntimeException unexpected) {
            throw new BrokerReadException(INVALID_RESPONSE);
        }
    }
    private byte[] readBounded(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new BrokerReadException(INVALID_RESPONSE);
        return bytes;
    }
    private boolean isTokenError(String body) {
        if (!body.stripLeading().startsWith("{")) return false;
        try {
            var root = json.readTree(body);
            return root != null && "error".equals(root.path("status").textValue())
                    && "TokenException".equals(root.path("error_type").textValue());
        }
        catch (IOException ignored) { return false; }
    }
}
