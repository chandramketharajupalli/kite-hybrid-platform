package com.kitehybrid.platform.historical.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kitehybrid.platform.historical.domain.HistoricalFingerprint;
import java.time.LocalDate;
import java.util.*;

/** Offline artifact verification only. No provider, filesystem, authentication or database access. */
public final class HistoricalContinuityGate {
    public static final String POLICY_PIN = "a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();
    private HistoricalContinuityGate() { }

    public static void require(MultiInstrumentCorpusPlan plan, String policyArtifact, String certificateArtifact) {
        try {
            Objects.requireNonNull(plan);
            var policy = parse(policyArtifact);
            if (!POLICY_PIN.equals(policy.path("fingerprint").asText())
                    || !POLICY_PIN.equals(hash(policy.required("policy")))) deny();
            var envelope = parse(certificateArtifact);
            var certificate = envelope.required("certification");
            if (!hash(certificate).equals(envelope.path("fingerprint").asText())
                    || !POLICY_PIN.equals(certificate.path("policy_fingerprint").asText())
                    || !"HistoricalIdentityCertification.v1".equals(certificate.path("version").asText())
                    || !certificate.path("evidence_fingerprint").asText().matches("[a-f0-9]{64}")
                    || !plan.universeFingerprint().equals(certificate.path("universe_fingerprint").asText())
                    || !"UNIVERSE_CONTINUITY_CERTIFIED".equals(certificate.path("aggregate_status").asText())) deny();
            var first = plan.members().getFirst().first();
            var end = plan.members().getFirst().endExclusive();
            if (!first.toString().equals(certificate.path("first").asText())
                    || !end.toString().equals(certificate.path("end_exclusive").asText())) deny();
            var members = certificate.required("members");
            if (!members.isArray() || members.size() != plan.members().size()) deny();
            Set<String> ids = new HashSet<>();
            String previousSymbol = "";
            for (var member : members) {
                var identity = member.required("identity");
                String id = identity.path("instrument_id").asText();
                String symbol = identity.path("symbol").asText();
                if (!ids.add(id) || symbol.compareTo(previousSymbol) <= 0
                        || !"NSE".equals(identity.path("exchange").asText())
                        || !"CASH".equals(identity.path("segment").asText())
                        || !"CASH".equals(identity.path("instrument_type").asText())
                        || !"ZERODHA".equals(identity.path("broker").asText())) deny();
                previousSymbol = symbol;
                for (String component : List.of("security_identity", "symbol_continuity", "series_continuity",
                        "intraday_price_continuity", "corporate_action_completeness", "evidence_quality"))
                    if (!"CERTIFIED".equals(member.path(component).asText())) deny();
                String classification = member.path("classification").asText();
                var boundaries = member.required("boundaries");
                if (!boundaries.isArray()) deny();
                if (classification.equals("CERTIFIED_CONTINUOUS")) {
                    if (!boundaries.isEmpty() || !"CERTIFIED".equals(member.path("cross_session_continuity").asText())) deny();
                } else if (classification.equals("CERTIFIED_WITH_DIVIDEND_NOTE")) {
                    if (boundaries.isEmpty() || !"CERTIFIED_WITH_NOTE".equals(member.path("cross_session_continuity").asText())) deny();
                } else deny();
                Set<String> actions = new HashSet<>();
                String previousBoundary = "";
                for (var boundary : boundaries) {
                    var day = LocalDate.parse(boundary.path("date").asText());
                    String actionId = boundary.path("action_id").asText();
                    String order = day + "|" + actionId;
                    if (day.isBefore(first) || !day.isBefore(end) || !id.equals(boundary.path("instrument_id").asText())
                            || !"CASH_DIVIDEND".equals(boundary.path("action").asText())
                            || actionId.isBlank() || !actions.add(actionId) || order.compareTo(previousBoundary) < 0
                            || !isBoolean(boundary, "price_continuity", false)
                            || !isBoolean(boundary, "volume_continuity", true)
                            || !isBoolean(boundary, "intraday_session_valid", true)
                            || !isBoolean(boundary, "segment_reset", false)) deny();
                    previousBoundary = order;
                }
            }
            if (!ids.equals(new HashSet<>(plan.members().stream()
                    .map(p -> p.instrumentId().value().toString()).toList()))) deny();
        } catch (Exception error) {
            throw new IllegalArgumentException("CONTINUITY_CERTIFICATION_DENIED", error);
        }
    }

    private static boolean isBoolean(JsonNode node, String key, boolean expected) {
        return node.path(key).isBoolean() && node.path(key).booleanValue() == expected;
    }
    private static JsonNode parse(String artifact) throws java.io.IOException {
        if (artifact == null || artifact.length() > 1_000_000) deny();
        return JSON.readTree(artifact);
    }
    private static String hash(JsonNode node) throws java.io.IOException {
        return HistoricalFingerprint.sha256(JSON.writeValueAsString(ordered(node)));
    }
    private static JsonNode ordered(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> result.set(name, ordered(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JSON.createArrayNode();
            node.forEach(item -> result.add(ordered(item)));
            return result;
        }
        return node;
    }
    private static void deny() { throw new IllegalArgumentException("CONTINUITY_CERTIFICATION_DENIED"); }
}
