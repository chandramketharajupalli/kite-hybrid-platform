package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.StrategyId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.application.EvidenceSnapshotQueryPort;
import com.kitehybrid.platform.trading.domain.*;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotReadPolicy;
import com.kitehybrid.platform.trading.infrastructure.EvidenceTradingProjectionAdapter;
import com.kitehybrid.platform.trading.infrastructure.TradingReadController;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.kitehybrid.platform.trading.domain.TradingProjection.UnavailableReason.*;

class EvidenceTradingProjectionAdapterTest {
    static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final class Fixture {
        final InstrumentId id = new InstrumentId(new UUID(1, 7));
        final StrategyDefinition strategy = new StrategyDefinition(new StrategyId("synthetic"), "v7");
        final Duration timeframe = Duration.ofMinutes(5);
        final Instant start = NOW.minusSeconds(7200), end = NOW.minusSeconds(3600);
        final EvidenceSnapshotQueryPort queries = mock(EvidenceSnapshotQueryPort.class);
        final EvidenceTradingProjectionAdapter.Scope scope = new EvidenceTradingProjectionAdapter.Scope(id, 7,
                strategy, timeframe, start, end, "fixture-policy", "v2", "synthetic-account", "fixture", "fixture-grant");
        final EvidenceSnapshotReadPolicy policy = new EvidenceSnapshotReadPolicy("fixture", "fixture-grant",
                NOW.minusSeconds(60), NOW.plusSeconds(600), Duration.ofMinutes(5), Set.of(id), Set.of(strategy), Set.of("synthetic-account"));
        final InstrumentSnapshotEvidence instrument = new InstrumentSnapshotEvidence(id, "FIXTURE", "SYNTHETIC",
                Optional.of(new BrokerInstrumentId("FIXTURE", "synthetic-token")), 7, provenance(NOW));
        final StrategyDefinitionEvidence definition = new StrategyDefinitionEvidence(strategy, timeframe,
                Map.of("threshold", "0.70", "precision", "1.2500"), List.of("synthetic-entry"), List.of("synthetic-exit"), Set.of(id), provenance(NOW));
        final StockStrategyQualificationEvidence qualification = new StockStrategyQualificationEvidence(id, strategy,
                timeframe, start, end, 10, 7, 3, "synthetic-cost", new BigDecimal("1.2500"), new BigDecimal("2.125"),
                provenance(start.minusSeconds(60), NOW.plusSeconds(600)), Optional.of(new StockStrategyQualificationEvidence.PolicyEvidence(
                        "fixture-policy", "v2", "b".repeat(64), 10, new BigDecimal("70"), true)), provenance(NOW));
        final AccountRiskSnapshotEvidence risk = risk("synthetic-account", provenance(NOW));
        final EvidenceTradingProjectionAdapter adapter = adapter(policy, CLOCK);
        Fixture() {
            when(queries.instrument(id, 7)).thenReturn(available(instrument));
            when(queries.strategy(strategy)).thenReturn(available(definition));
            when(queries.qualification(id, strategy, timeframe, start, end, "fixture-policy", "v2")).thenReturn(available(qualification));
            when(queries.accountRisk("synthetic-account")).thenReturn(available(risk));
        }
        EvidenceTradingProjectionAdapter adapter(EvidenceSnapshotReadPolicy p, Clock clock) {
            return new EvidenceTradingProjectionAdapter(queries, p, clock, scope);
        }
        List<TradingProjection> reads() {
            return List.of(adapter.instruments(), adapter.strategies(), adapter.qualifications(), adapter.riskStatus());
        }
        void all(Object result) {
            doReturn(result).when(queries).instrument(id, 7);
            doReturn(result).when(queries).strategy(strategy);
            doReturn(result).when(queries).qualification(id, strategy, timeframe, start, end, "fixture-policy", "v2");
            doReturn(result).when(queries).accountRisk("synthetic-account");
        }
    }
    private static EvidenceProvenance provenance(Instant observed) { return provenance(observed, observed.plusSeconds(600)); }
    private static EvidenceProvenance provenance(Instant observed, Instant expires) {
        return new EvidenceProvenance("fixture", "a".repeat(64), observed, expires,
                EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY);
    }
    private static AccountRiskSnapshotEvidence risk(String account, EvidenceProvenance p) {
        return new AccountRiskSnapshotEvidence(account, "INR", new BigDecimal("12.5000"), new BigDecimal("100.00"),
                Map.of("daily", new BigDecimal("25.1250")), p);
    }
    private static <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> available(T value) {
        return EvidenceAvailability.inspect(value, Clock.fixed(value.provenance().observedAt(), ZoneOffset.UTC));
    }
    private static TradingProjection.Available available(TradingProjection value) {
        return assertInstanceOf(TradingProjection.Available.class, value);
    }
    private static void unavailable(TradingProjection value, TradingProjection.UnavailableReason reason) {
        assertEquals(reason, assertInstanceOf(TradingProjection.Unavailable.class, value).reason());
        assertFalse(value.executionAuthorized());
    }
    @Test void instrumentPreservesExactSnapshot() {
        Fixture f = new Fixture(); var mapped = available(f.adapter.instruments());
        assertEquals(TradingProjection.Kind.INSTRUMENTS, mapped.kind()); assertEquals(1, mapped.entries().size());
        assertSame(f.instrument, assertInstanceOf(TradingProjection.InstrumentEvidenceView.class, mapped.entries().get(0)).snapshot());
        assertEquals(7, f.instrument.snapshotVersion()); assertEquals("synthetic-token", f.instrument.instrumentToken().orElseThrow().value());
    }
    @Test void strategyPreservesVersionParametersAndUniverse() {
        Fixture f = new Fixture(); var row = assertInstanceOf(TradingProjection.StrategyEvidenceView.class, available(f.adapter.strategies()).entries().get(0));
        assertSame(f.definition, row.snapshot()); assertEquals("v7", row.snapshot().strategy().version());
        assertEquals(Duration.ofMinutes(5), row.snapshot().timeframe()); assertEquals("1.2500", row.snapshot().parameters().get("precision"));
        assertEquals(Set.of(f.id), row.snapshot().applicableUniverse());
    }
    @Test void qualificationPreservesPeriodDatasetAndConservativeDecision() {
        Fixture f = new Fixture(); var row = assertInstanceOf(TradingProjection.QualificationEvidenceView.class, available(f.adapter.qualifications()).entries().get(0));
        assertSame(f.qualification, row.snapshot()); assertEquals(f.start, row.snapshot().periodStart()); assertEquals(f.end, row.snapshot().periodEnd());
        assertEquals(f.qualification.dataset(), row.snapshot().dataset()); assertEquals(f.qualification.decision(), row.state());
        assertEquals(new BigDecimal("70.00000000"), row.snapshot().winPercentage().orElseThrow());
        assertEquals(TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE, row.state());
        assertThrows(IllegalArgumentException.class, () -> new TradingProjection.QualificationEvidenceView(f.qualification, TradingProjection.QualificationState.REJECTED));
    }
    @Test void accountRiskPreservesScopeCurrencyAndDecimals() {
        Fixture f = new Fixture(); var row = assertInstanceOf(TradingProjection.AccountRiskEvidenceView.class, available(f.adapter.riskStatus()).entries().get(0));
        assertSame(f.risk, row.snapshot()); assertEquals("synthetic-account", row.snapshot().accountScope()); assertEquals("INR", row.snapshot().currency());
        assertEquals(new BigDecimal("12.5000"), row.snapshot().exposure()); assertEquals(new BigDecimal("100.00"), row.snapshot().exposureLimit());
        assertEquals(new BigDecimal("25.1250"), row.snapshot().additionalLimits().get("daily"));
    }
    @Test void fullProvenanceSurvivesMapping() {
        Fixture f = new Fixture();
        for (TradingProjection projection : f.reads()) {
            var result = available(projection); assertEquals("a".repeat(64), result.evidence().sourceSha256()); assertEquals(NOW, result.evidence().observedAt());
            EvidenceProvenance.Snapshot snapshot = switch (result.entries().get(0)) {
                case TradingProjection.InstrumentEvidenceView v -> v.snapshot();
                case TradingProjection.StrategyEvidenceView v -> v.snapshot();
                case TradingProjection.QualificationEvidenceView v -> v.snapshot();
                case TradingProjection.AccountRiskEvidenceView v -> v.snapshot();
                default -> throw new AssertionError("Unexpected summary variant");
            };
            assertEquals(provenance(NOW), snapshot.provenance()); assertFalse(snapshot.tradingAuthorized());
        }
    }
    @Test void missingNeverBecomesEmptySuccess() {
        Fixture f = new Fixture(); f.all(EvidenceAvailability.missing()); f.reads().forEach(p -> unavailable(p, EVIDENCE_UNAVAILABLE));
    }
    @Test void staleNeverBecomesAvailable() {
        Fixture f = new Fixture(); f.all(EvidenceAvailability.inspect(f.risk, Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC)));
        f.reads().forEach(p -> unavailable(p, STALE_EVIDENCE));
        when(f.queries.accountRisk("synthetic-account")).thenReturn(available(risk("synthetic-account", provenance(NOW.minusSeconds(300)))));
        unavailable(f.adapter.riskStatus(), STALE_EVIDENCE);
        when(f.queries.accountRisk("synthetic-account")).thenReturn(available(risk("synthetic-account", provenance(NOW.minusSeconds(700)))));
        unavailable(f.adapter.riskStatus(), STALE_EVIDENCE);
    }
    @Test void incompleteRemainsDistinct() {
        Fixture f = new Fixture(); f.all(EvidenceAvailability.incomplete()); f.reads().forEach(p -> unavailable(p, INCOMPLETE_EVIDENCE));
        var q = f.qualification;
        var incomplete = new StockStrategyQualificationEvidence(q.instrumentId(), q.strategy(), q.timeframe(), q.periodStart(), q.periodEnd(),
                q.evaluatedTrades(), q.wins(), q.losses(), q.costModel(), q.transactionCosts(), q.slippageBasisPoints(), q.dataset(), Optional.empty(), q.provenance());
        when(f.queries.qualification(f.id, f.strategy, f.timeframe, f.start, f.end, "fixture-policy", "v2")).thenReturn(available(incomplete));
        unavailable(f.adapter.qualifications(), INCOMPLETE_EVIDENCE);
    }
    @Test void invalidNeverBecomesAvailable() {
        Fixture f = new Fixture(); f.all(EvidenceAvailability.invalid()); f.reads().forEach(p -> unavailable(p, UNVERIFIED_EVIDENCE));
        var p = new EvidenceProvenance("other-source", "a".repeat(64), NOW, NOW.plusSeconds(600), EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY);
        when(f.queries.accountRisk("synthetic-account")).thenReturn(available(risk("synthetic-account", p)));
        unavailable(f.adapter.riskStatus(), UNVERIFIED_EVIDENCE);
    }
    @Test void futureEvidenceIsRejected() {
        Fixture f = new Fixture(); when(f.queries.accountRisk("synthetic-account")).thenReturn(available(risk("synthetic-account", provenance(NOW.plusSeconds(1)))));
        unavailable(f.adapter.riskStatus(), UNVERIFIED_EVIDENCE); verify(f.queries).accountRisk("synthetic-account"); verifyNoMoreInteractions(f.queries);
    }
    @Test void expiredReadPolicyDeniesBeforeQuery() {
        Fixture f = new Fixture(); var expired = new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", NOW.minusSeconds(60), NOW,
                Duration.ofMinutes(5), Set.of(f.id), Set.of(f.strategy), Set.of("synthetic-account"));
        var a = f.adapter(expired, CLOCK);
        List.of(a.instruments(), a.strategies(), a.qualifications(), a.riskStatus()).forEach(p -> unavailable(p, UNVERIFIED_EVIDENCE)); verifyNoInteractions(f.queries);
        var wrongSource = new EvidenceSnapshotReadPolicy("other", "fixture-grant", NOW.minusSeconds(60), NOW.plusSeconds(600),
                Duration.ofMinutes(5), Set.of(f.id), Set.of(f.strategy), Set.of("synthetic-account"));
        assertThrows(IllegalArgumentException.class, () -> f.adapter(wrongSource, CLOCK));
    }
    @Test void mismatchedIdentitiesAreRejected() {
        Fixture f = new Fixture();
        when(f.queries.instrument(f.id, 7)).thenReturn(available(new InstrumentSnapshotEvidence(new InstrumentId(new UUID(2, 2)), "FIXTURE", "SYNTHETIC", Optional.empty(), 7, provenance(NOW))));
        unavailable(f.adapter.instruments(), UNVERIFIED_EVIDENCE);
        when(f.queries.instrument(f.id, 7)).thenReturn(available(new InstrumentSnapshotEvidence(f.id, "FIXTURE", "SYNTHETIC", Optional.empty(), 8, provenance(NOW))));
        unavailable(f.adapter.instruments(), UNVERIFIED_EVIDENCE);
        when(f.queries.strategy(f.strategy)).thenReturn(available(new StrategyDefinitionEvidence(new StrategyDefinition(f.strategy.id(), "v8"), f.timeframe,
                f.definition.parameters(), f.definition.entryConditions(), f.definition.exitConditions(), Set.of(f.id), provenance(NOW))));
        unavailable(f.adapter.strategies(), UNVERIFIED_EVIDENCE);
        var q = f.qualification;
        var wrongPeriod = new StockStrategyQualificationEvidence(q.instrumentId(), q.strategy(), q.timeframe(), q.periodStart().minusSeconds(1), q.periodEnd(),
                q.evaluatedTrades(), q.wins(), q.losses(), q.costModel(), q.transactionCosts(), q.slippageBasisPoints(), q.dataset(), q.policy(), q.provenance());
        when(f.queries.qualification(f.id, f.strategy, f.timeframe, f.start, f.end, "fixture-policy", "v2")).thenReturn(available(wrongPeriod));
        unavailable(f.adapter.qualifications(), UNVERIFIED_EVIDENCE);
    }
    @Test void crossAccountEvidenceIsRejected() {
        Fixture f = new Fixture(); when(f.queries.accountRisk("synthetic-account")).thenReturn(available(risk("other-account", provenance(NOW))));
        unavailable(f.adapter.riskStatus(), UNVERIFIED_EVIDENCE); verify(f.queries).accountRisk("synthetic-account"); verifyNoMoreInteractions(f.queries);
    }
    @Test void databaseFailureBecomesSourceUnavailable() {
        Fixture f = new Fixture(); var failure = new DataAccessResourceFailureException("synthetic private diagnostic");
        doThrow(failure).when(f.queries).instrument(f.id, 7); doThrow(failure).when(f.queries).strategy(f.strategy);
        doThrow(failure).when(f.queries).qualification(f.id, f.strategy, f.timeframe, f.start, f.end, "fixture-policy", "v2");
        doThrow(failure).when(f.queries).accountRisk("synthetic-account");
        for (var p : f.reads()) { unavailable(p, SOURCE_UNAVAILABLE); assertFalse(p.toString().contains("private diagnostic")); }
        verify(f.queries).instrument(f.id, 7); verify(f.queries).strategy(f.strategy);
        verify(f.queries).qualification(f.id, f.strategy, f.timeframe, f.start, f.end, "fixture-policy", "v2");
        verify(f.queries).accountRisk("synthetic-account"); verifyNoMoreInteractions(f.queries);
    }
    @Test void nullPortResultFailsClosed() { Fixture f = new Fixture(); f.all(null); f.reads().forEach(p -> unavailable(p, UNVERIFIED_EVIDENCE)); }
    @Test void mappedEvidenceIsImmutableAndNeverAuthorizesTrading() {
        Fixture f = new Fixture(); for (var projection : f.reads()) {
            assertFalse(projection.executionAuthorized()); assertThrows(UnsupportedOperationException.class, () -> available(projection).entries().clear());
        }
        assertThrows(UnsupportedOperationException.class, () -> f.definition.parameters().put("threshold", "0"));
        assertThrows(UnsupportedOperationException.class, () -> f.definition.applicableUniverse().clear());
        assertThrows(UnsupportedOperationException.class, () -> f.risk.additionalLimits().clear());
    }
    @Test void exactQueryArgumentsAndSingleReadArePreserved() {
        Fixture f = new Fixture(); f.reads(); verify(f.queries).instrument(f.id, 7); verify(f.queries).strategy(f.strategy);
        verify(f.queries).qualification(f.id, f.strategy, f.timeframe, f.start, f.end, "fixture-policy", "v2");
        verify(f.queries).accountRisk("synthetic-account"); verifyNoMoreInteractions(f.queries);
    }
    @Test void serializedEvidenceVariantsMatchApprovedContract() throws Exception {
        // Match TradingCoreApplication's Boot MVC/Jackson boundary, not the plain
        // @EnableWebMvc security fixture, which omits Boot's Jackson defaults.
        // No application scan, broker services, database or protected routes are started.
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                WebMvcAutoConfiguration.class)).run(context -> {
            assertNull(context.getStartupFailure());
            ObjectMapper mapper = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                    .filter(MappingJackson2HttpMessageConverter.class::isInstance).map(MappingJackson2HttpMessageConverter.class::cast)
                    .findFirst().orElseThrow().getObjectMapper();
            assertSame(context.getBean(ObjectMapper.class), mapper);
            assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
            JsonNode api = mapper.readTree(Path.of(System.getProperty("phaseK.openapi", "../../docs/api/trading-v1.openapi.json")).toFile());
            Fixture fixture = new Fixture();
            var results = fixture.reads();
            String[] names = {"InstrumentsReadResponse", "StrategiesReadResponse", "QualificationsReadResponse", "RiskReadResponse"};
            List<EvidenceProvenance.Snapshot> snapshots = List.of(fixture.instrument, fixture.definition,
                    fixture.qualification, fixture.risk);
            String hash = "a".repeat(64);
            // Explicit synthetic legacy rows, not invented fields on evidence snapshots.
            List<TradingProjection.Entry> legacy = List.of(
                    new TradingProjection.InstrumentView(fixture.id.value(), "FIXTURE", "SYNTHETIC", hash, hash),
                    new TradingProjection.StrategyView("synthetic", "v7", hash, "SYNTHETIC_FIXTURE"),
                    new TradingProjection.QualificationView(fixture.id.value(), "synthetic", "v7", hash,
                            TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE),
                    new TradingProjection.RiskView(true, hash));
            List<Executable> variants = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                int index = i;
                variants.add(() -> {
                    var projection = available(results.get(index));
                    assertSerializedContract(mapper, api, names[index], projection, snapshots.get(index));
                });
                variants.add(() -> assertSerializedContract(mapper, api, names[index],
                        new TradingProjection.Available(legacy.get(index).kind(), List.of(legacy.get(index)),
                                new TradingProjection.Evidence(hash, NOW, TradingProjection.EvidenceProvenance.SYNTHETIC_FIXTURE)), null));
            }
            // All four evidence and all four legacy variants are evaluated even if one fails.
            assertAll("Boot MVC evidence and legacy serialization", variants);
        });
    }

    private static void assertSerializedContract(ObjectMapper mapper, JsonNode api, String schemaName,
            TradingProjection.Available projection, EvidenceProvenance.Snapshot snapshot) throws Exception {
        ObjectNode schema = mapper.createObjectNode().put("$schema", "https://json-schema.org/draft/2020-12/schema")
                .put("$ref", "#/components/schemas/" + schemaName);
        schema.set("components", api.get("components"));
        var validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema);
        JsonNode body = mapper.readTree(mapper.writeValueAsBytes(TradingReadController.render(projection).getBody()));
        JsonNode row = body.path("entries").path(0);
        ObjectNode extra = body.deepCopy(); extra.put("unexpected", true);
        ObjectNode extraRow = body.deepCopy(); ((ObjectNode) extraRow.path("entries").path(0)).put("unexpected", true);
        List<Executable> checks = new ArrayList<>(List.of(
                () -> assertTrue(validator.validate(body).isEmpty(), () -> validator.validate(body).toString()),
                () -> assertFalse(body.path("executionAuthorized").booleanValue()),
                () -> assertUtcInstant(body.path("evidence").path("observedAt"), projection.evidence().observedAt()),
                () -> assertFalse(validator.validate(extra).isEmpty()),
                () -> assertFalse(validator.validate(extraRow).isEmpty())));
        if (snapshot == null) {
            checks.add(() -> assertFalse(row.has("snapshot")));
        } else {
            JsonNode value = row.path("snapshot");
            ObjectNode dropped = body.deepCopy();
            ((ObjectNode) dropped.path("entries").path(0).path("snapshot")).remove("provenance");
            checks.add(() -> assertTrue(row.has("snapshot")));
            checks.add(() -> assertUtcInstant(value.path("provenance").path("observedAt"), snapshot.provenance().observedAt()));
            checks.add(() -> assertUtcInstant(value.path("provenance").path("expiresAt"), snapshot.provenance().expiresAt()));
            checks.add(() -> assertFalse(validator.validate(dropped).isEmpty()));
            if (snapshot instanceof StockStrategyQualificationEvidence qualification) {
                checks.add(() -> assertUtcInstant(value.path("periodStart"), qualification.periodStart()));
                checks.add(() -> assertUtcInstant(value.path("periodEnd"), qualification.periodEnd()));
                checks.add(() -> assertUtcInstant(value.path("dataset").path("observedAt"), qualification.dataset().observedAt()));
                checks.add(() -> assertUtcInstant(value.path("dataset").path("expiresAt"), qualification.dataset().expiresAt()));
            }
        }
        assertAll(schemaName + (snapshot == null ? " legacy" : " evidence"), checks);
    }

    private static void assertUtcInstant(JsonNode value, Instant expected) {
        assertTrue(value.isTextual(), "Timestamp must be a string");
        assertTrue(value.textValue().endsWith("Z"), "Timestamp must retain UTC representation");
        assertEquals(expected, Instant.parse(value.textValue()), "Timestamp instant must be preserved");
    }
}
