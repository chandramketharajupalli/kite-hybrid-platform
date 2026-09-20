package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.account.application.BrokerProfileProvider;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.IOException;
import java.util.HashSet;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

public final class KiteProfileAdapter implements BrokerProfileProvider {
    private final KiteRestTransport transport;
    private final KiteSession session;
    KiteProfileAdapter(KiteRestTransport transport, KiteSession session) {
        this.transport = transport;
        this.session = session;
    }
    @Override public BrokerProfile currentProfile() {
        synchronized (session) { return validateCurrentSession(); }
    }
    private BrokerProfile validateCurrentSession() {
        String body = transport.get(KiteRestTransport.Endpoint.PROFILE);
        BrokerProfile profile = map(body);
        session.profileValidated();
        return profile;
    }
    static BrokerProfile map(String body) {
        try {
            JsonNode root = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build().readTree(body);
            if (root == null || !root.isObject()) throw new BrokerReadException(INVALID_RESPONSE);
            String status = root.path("status").asText();
            if (status.equals("error")) throw new BrokerReadException(BROKER_API);
            if (!status.equals("success")) throw new BrokerReadException(INVALID_RESPONSE);
            JsonNode data = root.path("data");
            if (!data.path("user_id").isTextual() || !data.path("broker").isTextual()
                    || !"ZERODHA".equals(data.path("broker").textValue())
                    || !data.path("exchanges").isArray())
                throw new BrokerReadException(INVALID_RESPONSE);
            var exchanges = new HashSet<String>();
            for (var exchange : data.path("exchanges")) {
                if (!exchange.isTextual() || !exchanges.add(exchange.textValue()))
                    throw new BrokerReadException(INVALID_RESPONSE);
            }
            return new BrokerProfile("ZERODHA", data.path("user_id").textValue(), exchanges);
        } catch (BrokerReadException safe) { throw safe; }
        catch (IOException | RuntimeException invalid) { throw new BrokerReadException(INVALID_RESPONSE); }
    }
}
