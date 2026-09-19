package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** Fixed origin, two GET routes, no redirects/retries, bounded bodies and no wire logging. */
final class KiteRestTransport {
    enum Endpoint {
        PROFILE("/user/profile", 64 * 1024),
        INSTRUMENTS("/instruments", 32 * 1024 * 1024);
        final String path;
        final int limit;
        Endpoint(String path, int limit) { this.path = path; this.limit = limit; }
    }
    private final RestClient client;
    private final KiteSession session;
    private final ObjectMapper json = new ObjectMapper();

    KiteRestTransport(RestClient client, KiteSession session) {
        this.client = client;
        this.session = session;
    }
    static KiteRestTransport production(KiteSession session) {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new KiteRestTransport(RestClient.builder().baseUrl("https://api.kite.trade")
                .requestFactory(factory).build(), session);
    }

    String get(Endpoint endpoint) {
        String authorization = session.authorization();
        try {
            return client.get().uri(endpoint.path)
                    .header("X-Kite-Version", "3").header("Authorization", authorization)
                    .header("Accept-Encoding", "gzip")
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            session.invalidate();
                            throw new BrokerReadException(AUTHENTICATION, status);
                        }
                        byte[] body = readBounded(response.getBody(), endpoint.limit);
                        String encoding = response.getHeaders().getFirst("Content-Encoding");
                        boolean gzip = "gzip".equalsIgnoreCase(encoding)
                                || (body.length > 1 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b);
                        if (encoding != null && !encoding.equalsIgnoreCase("gzip")
                                && !encoding.equalsIgnoreCase("identity"))
                            throw new BrokerReadException(INVALID_RESPONSE, status);
                        if (gzip) {
                            try (var input = new GZIPInputStream(new ByteArrayInputStream(body))) {
                                body = readBounded(input, endpoint.limit);
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
                                session.invalidate();
                                throw new BrokerReadException(AUTHENTICATION, status);
                            }
                            throw new BrokerReadException(BROKER_API, status);
                        }
                        if (isTokenError(text)) {
                            session.invalidate();
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
        try { return "TokenException".equals(json.readTree(body).path("error_type").asText()); }
        catch (IOException ignored) { return false; }
    }
}
