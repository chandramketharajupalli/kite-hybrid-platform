package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.HistoricalFingerprint;
import java.nio.file.*;
import java.util.*;

/** Synthetic certification only; not authoritative evidence for the frozen universe. */
public final class ContinuityFixtures {
    private ContinuityFixtures() { }
    public static String policy() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (!Files.exists(root.resolve("research/phase-12.1/certification-policy-v1.json"))) {
                root = Objects.requireNonNull(root.getParent());
            }
            return Files.readString(root.resolve("research/phase-12.1/certification-policy-v1.json"));
        } catch (Exception error) { throw new AssertionError(error); }
    }
    public static Map<String, Object> body(MultiInstrumentCorpusPlan plan) {
        var members = new ArrayList<Map<String, Object>>();
        int index = 0;
        for (var member : plan.members()) {
            var identity = Map.of("instrument_id", member.instrumentId().value().toString(),
                    "symbol", "SYNTHETIC" + (++index), "exchange", "NSE", "segment", "CASH",
                    "instrument_type", "CASH", "broker", "ZERODHA");
            var certificate = new TreeMap<String, Object>();
            certificate.put("identity", identity);
            for (String field : List.of("security_identity", "symbol_continuity", "series_continuity",
                    "intraday_price_continuity", "cross_session_continuity",
                    "corporate_action_completeness", "evidence_quality")) certificate.put(field, "CERTIFIED");
            certificate.put("classification", "CERTIFIED_CONTINUOUS");
            certificate.put("boundaries", List.of());
            members.add(certificate);
        }
        var result = new TreeMap<String, Object>();
        result.put("version", "HistoricalIdentityCertification.v1");
        result.put("policy_fingerprint", HistoricalContinuityGate.POLICY_PIN);
        result.put("evidence_fingerprint", "e".repeat(64));
        result.put("universe_fingerprint", plan.universeFingerprint());
        result.put("first", plan.members().getFirst().first().toString());
        result.put("end_exclusive", plan.members().getFirst().endExclusive().toString());
        result.put("members", members);
        result.put("aggregate_status", "UNIVERSE_CONTINUITY_CERTIFIED");
        return result;
    }
    public static String envelope(Map<String, Object> body) {
        try {
            var mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return mapper.writeValueAsString(Map.of("certification", body, "fingerprint",
                    HistoricalFingerprint.sha256(mapper.writeValueAsString(body))));
        } catch (Exception error) { throw new AssertionError(error); }
    }
    public static String certificate(MultiInstrumentCorpusPlan plan) { return envelope(body(plan)); }
}
