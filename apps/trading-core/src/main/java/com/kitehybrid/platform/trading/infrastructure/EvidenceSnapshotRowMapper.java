package com.kitehybrid.platform.trading.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.StrategyId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.domain.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Rejects malformed persisted data, including unknown fields and lossy primitive conversions. */
public final class EvidenceSnapshotRowMapper {
    public enum Kind { INSTRUMENT, STRATEGY, QUALIFICATION, ACCOUNT_RISK }
    public record Row(EvidenceProvenance.Snapshot evidence, String authorizationReference) {}

    public Row map(ResultSet rs, Kind kind) throws SQLException {
        if (!Integer.valueOf(1).equals(rs.getObject("schema_version", Integer.class)))
            throw new IllegalArgumentException("Unknown evidence schema");
        String payload = rs.getString("payload");
        if (!EvidenceSnapshotFingerprint.sha256(payload).equals(rs.getString("payload_sha256")))
            throw new IllegalArgumentException("Payload fingerprint mismatch");
        var result = decode(kind, payload);
        var p = result.provenance();
        if (!p.sourceIdentity().equals(rs.getString("source_identity"))
                || !p.fingerprint().equals(rs.getString("source_fingerprint"))
                || !p.observedAt().equals(instant(rs, "observed_at"))
                || !p.expiresAt().equals(instant(rs, "expires_at"))
                || !p.origin().name().equals(rs.getString("origin"))
                || !p.retention().name().equals(rs.getString("retention")))
            throw new IllegalArgumentException("Provenance column mismatch");
        return new Row(result, EvidenceSnapshotReadPolicy.text(rs.getString("authorization_reference")));
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name);
        if (value == null) throw new IllegalArgumentException("Missing timestamp");
        return value.toInstant();
    }
    public EvidenceProvenance.Snapshot decode(Kind kind, String payload) {
        JsonNode n = EvidenceSnapshotFingerprint.parse(payload);
        return switch (kind) {
            case INSTRUMENT -> {
                fields(n, "instrumentId", "exchange", "symbol", "token", "snapshotVersion", "provenance");
                JsonNode token = required(n, "token");
                Optional<BrokerInstrumentId> mapping = Optional.empty();
                if (!token.isNull()) {
                    fields(token, "broker", "value");
                    mapping = Optional.of(new BrokerInstrumentId(text(token, "broker"), text(token, "value")));
                }
                yield new InstrumentSnapshotEvidence(id(n, "instrumentId"), text(n, "exchange"),
                        text(n, "symbol"), mapping, integer(n, "snapshotVersion"), provenance(required(n, "provenance")));
            }
            case STRATEGY -> {
                fields(n, "strategyId", "strategyVersion", "timeframe", "parameters",
                        "entryConditions", "exitConditions", "universe", "provenance");
                var universe = new HashSet<InstrumentId>();
                for (String value : strings(required(n, "universe"))) {
                    if (!universe.add(new InstrumentId(UUID.fromString(value))))
                        throw new IllegalArgumentException("Duplicate universe member");
                }
                yield new StrategyDefinitionEvidence(strategy(n), duration(n), stringMap(required(n, "parameters")),
                        strings(required(n, "entryConditions")), strings(required(n, "exitConditions")),
                        universe, provenance(required(n, "provenance")));
            }
            case QUALIFICATION -> {
                fields(n, "instrumentId", "strategyId", "strategyVersion", "timeframe", "periodStart", "periodEnd",
                        "evaluatedTrades", "wins", "losses", "costModel", "transactionCosts", "slippageBasisPoints",
                        "dataset", "policy", "provenance");
                JsonNode policy = required(n, "policy");
                Optional<StockStrategyQualificationEvidence.PolicyEvidence> rule = Optional.empty();
                if (!policy.isNull()) {
                    fields(policy, "policyId", "version", "fingerprint", "minimumTrades",
                            "minimumWinPercentage", "remainingChecksPassed");
                    if (!required(policy, "remainingChecksPassed").isBoolean())
                        throw new IllegalArgumentException("Boolean required");
                    rule = Optional.of(new StockStrategyQualificationEvidence.PolicyEvidence(text(policy, "policyId"),
                            text(policy, "version"), text(policy, "fingerprint"), integer(policy, "minimumTrades"),
                            decimal(policy, "minimumWinPercentage"), policy.get("remainingChecksPassed").booleanValue()));
                }
                yield new StockStrategyQualificationEvidence(id(n, "instrumentId"), strategy(n), duration(n),
                        Instant.parse(text(n, "periodStart")), Instant.parse(text(n, "periodEnd")),
                        integer(n, "evaluatedTrades"), integer(n, "wins"), integer(n, "losses"),
                        text(n, "costModel"), decimal(n, "transactionCosts"), decimal(n, "slippageBasisPoints"),
                        provenance(required(n, "dataset")), rule, provenance(required(n, "provenance")));
            }
            case ACCOUNT_RISK -> {
                fields(n, "accountScope", "currency", "exposure", "exposureLimit", "additionalLimits", "provenance");
                JsonNode limits = required(n, "additionalLimits");
                if (!limits.isObject()) throw new IllegalArgumentException("Limits object required");
                var values = new HashMap<String, BigDecimal>();
                limits.fieldNames().forEachRemaining(key -> values.put(key, decimal(limits, key)));
                yield new AccountRiskSnapshotEvidence(text(n, "accountScope"), text(n, "currency"),
                        decimal(n, "exposure"), decimal(n, "exposureLimit"), values,
                        provenance(required(n, "provenance")));
            }
        };
    }
    private static EvidenceProvenance provenance(JsonNode n) {
        fields(n, "sourceIdentity", "fingerprint", "observedAt", "expiresAt", "origin", "retention");
        return new EvidenceProvenance(text(n, "sourceIdentity"), text(n, "fingerprint"),
                Instant.parse(text(n, "observedAt")), Instant.parse(text(n, "expiresAt")),
                EvidenceProvenance.Origin.valueOf(text(n, "origin")), EvidenceProvenance.Retention.valueOf(text(n, "retention")));
    }
    private static StrategyDefinition strategy(JsonNode n) {
        return new StrategyDefinition(new StrategyId(text(n, "strategyId")), text(n, "strategyVersion"));
    }
    private static Duration duration(JsonNode n) { return Duration.parse(text(n, "timeframe")); }
    private static InstrumentId id(JsonNode n, String key) { return new InstrumentId(UUID.fromString(text(n, key))); }
    private static JsonNode required(JsonNode n, String key) {
        JsonNode value = n.get(key);
        if (value == null) throw new IllegalArgumentException("Missing field " + key);
        return value;
    }
    private static String text(JsonNode n, String key) {
        JsonNode value = required(n, key);
        if (!value.isTextual()) throw new IllegalArgumentException("String required");
        return EvidenceSnapshotReadPolicy.text(value.textValue());
    }
    private static long integer(JsonNode n, String key) {
        JsonNode value = required(n, key);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException("Exact long required");
        return value.longValue();
    }
    private static BigDecimal decimal(JsonNode n, String key) {
        JsonNode value = required(n, key);
        if (!value.isNumber()) throw new IllegalArgumentException("Decimal required");
        return value.decimalValue();
    }
    private static List<String> strings(JsonNode n) {
        if (!n.isArray() || n.size() > 1024) throw new IllegalArgumentException("Bounded array required");
        var values = new ArrayList<String>();
        for (JsonNode item : n) {
            if (!item.isTextual()) throw new IllegalArgumentException("String required");
            values.add(EvidenceSnapshotReadPolicy.text(item.textValue()));
        }
        return List.copyOf(values);
    }
    private static Map<String, String> stringMap(JsonNode n) {
        if (!n.isObject() || n.size() > 1024) throw new IllegalArgumentException("Bounded object required");
        var values = new HashMap<String, String>();
        n.fieldNames().forEachRemaining(key -> values.put(EvidenceSnapshotReadPolicy.text(key), text(n, key)));
        return Map.copyOf(values);
    }
    private static void fields(JsonNode n, String... names) {
        if (!n.isObject()) throw new IllegalArgumentException("Object required");
        var actual = new HashSet<String>();
        n.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(names))) throw new IllegalArgumentException("Unexpected/missing fields");
    }
}
