package com.kitehybrid.platform.trading.infrastructure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/** v1 canonical JSON: sorted object keys, ordered arrays, exact normalized decimal numbers. */
public final class EvidenceSnapshotFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private EvidenceSnapshotFingerprint() {}

    public static JsonNode parse(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > 65536)
            throw new IllegalArgumentException("Payload absent or oversized");
        try {
            JsonNode value = JSON.readTree(text);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("Object required");
            canonical(value, 0);
            return value;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Malformed evidence JSON", e);
        }
    }
    private static JsonNode canonical(JsonNode value, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Payload nesting limit");
        if (value.isObject()) {
            var fields = new TreeMap<String, JsonNode>();
            value.fields().forEachRemaining(e -> fields.put(e.getKey(), canonical(e.getValue(), depth + 1)));
            var result = JsonNodeFactory.instance.objectNode();
            fields.forEach(result::set);
            return result;
        }
        if (value.isArray()) {
            if (value.size() > 1024) throw new IllegalArgumentException("Collection limit");
            var result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(canonical(item, depth + 1)));
            return result;
        }
        if (value.isNumber()) return JsonNodeFactory.instance.numberNode(value.decimalValue().stripTrailingZeros());
        if (value.isTextual() || value.isBoolean() || value.isNull()) return value;
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    public static String sha256(String payload) {
        try {
            byte[] bytes = ("evidence-payload-v1\n" + canonical(parse(payload), 0))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
