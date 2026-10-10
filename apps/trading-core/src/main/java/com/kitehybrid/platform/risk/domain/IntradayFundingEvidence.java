package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.BrokerMargins;
import com.kitehybrid.platform.broker.domain.read.OrderMarginQuote;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.EnumSet;
import java.math.BigDecimal;
import com.kitehybrid.platform.broker.domain.read.TradingReadTypes;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.command.OrderType;

/** Redacted, pure observation of existing cash-only funding policy; never a risk decision or permit. */
public final class IntradayFundingEvidence {
    private IntradayFundingEvidence() {}
    public enum Evidence { OBSERVED, DERIVED, BROKER_AUTHORITATIVE, UNKNOWN }
    public enum Readiness { NOT_READY }
    public record View(Readiness readiness, RiskReason cashOnlyFunding, Map<String, Evidence> fields) {
        public View { fields = Map.copyOf(fields); }
    }

    public enum Freshness { CURRENT, STALE, FUTURE, UNKNOWN }
    public enum Binding { MATCHED, CONFLICTING, UNKNOWN }
    /** Provenance describes the input contract, not an independently authenticated attestation. */
    public enum Origin { SYNTHETIC, CALLER_SUPPLIED_NORMALIZED }
    public enum Source { ACCOUNT_FIELDS, EXACT_REQUEST_CALCULATION, APPLICATION_POLICY, UNESTABLISHED }
    public enum Denial {
        AUTH_REQUIRED, POLICY_UNAVAILABLE, ACCOUNT_UNAVAILABLE, ACCOUNT_STALE, ACCOUNT_FUTURE,
        QUOTE_MISSING, QUOTE_STALE, QUOTE_FUTURE, REQUEST_CONFLICT, INVALID_EVIDENCE,
        CASH_POLICY_INSUFFICIENT, ELIGIBLE_ADJUSTED_COLLATERAL_UNKNOWN,
        AVAILABLE_COLLATERAL_UNKNOWN, CASH_COMPONENT_UNKNOWN, CASH_FIELD_ELIGIBILITY_UNKNOWN
    }
    public record Field(Evidence evidence, Source source) {}
    public record Detail(Readiness readiness, RiskReason cashOnlyFunding, Origin origin,
            TradingReadTypes.MarginSegment accountSegment, Instant accountObservedAt, Instant quoteReceivedAt,
            Freshness accountFreshness, Freshness quoteFreshness, Binding requestBinding,
            String requestFingerprint, Map<String, Field> fields, List<Denial> denials) {
        public Detail {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            denials = List.copyOf(denials);
        }
    }

    /** Additive diagnostic; preserves the original View API and never trusts supplied collateral terms. */
    public static Detail detail(CashAccountCapacity.Capacity account, BrokerMargins margins,
            OrderMarginQuote quote, OrderMarginQuote.Request expected, RiskLimits limits,
            Instant accountObservedAt, Instant now, boolean authenticated, Origin origin) {
        var view = inspect(account, margins, quote, expected, limits, accountObservedAt, now, authenticated);
        var age = limits == null ? null : limits.marketDataMaxAge();
        var accountAge = freshness(accountObservedAt, now, age);
        var quoteAge = freshness(quote == null ? null : quote.receivedAt(), now, age);
        var binding = expected == null || quote == null ? Binding.UNKNOWN
                : quote.request().equals(expected) ? Binding.MATCHED : Binding.CONFLICTING;
        var denied = EnumSet.noneOf(Denial.class);
        if (!authenticated) denied.add(Denial.AUTH_REQUIRED);
        if (limits == null || !limits.configured() || !limits.enabled()) denied.add(Denial.POLICY_UNAVAILABLE);
        if (account == null || margins == null || accountAge == Freshness.UNKNOWN) denied.add(Denial.ACCOUNT_UNAVAILABLE);
        if (accountAge == Freshness.STALE) denied.add(Denial.ACCOUNT_STALE);
        if (accountAge == Freshness.FUTURE) denied.add(Denial.ACCOUNT_FUTURE);
        if (quote == null) denied.add(Denial.QUOTE_MISSING);
        if (quoteAge == Freshness.STALE) denied.add(Denial.QUOTE_STALE);
        if (quoteAge == Freshness.FUTURE) denied.add(Denial.QUOTE_FUTURE);
        if (binding != Binding.MATCHED) denied.add(Denial.REQUEST_CONFLICT);
        if (view.cashOnlyFunding() == RiskReason.MIS_MARGIN_UNAVAILABLE) denied.add(Denial.INVALID_EVIDENCE);
        else if (view.cashOnlyFunding() != RiskReason.APPROVED) denied.add(Denial.CASH_POLICY_INSUFFICIENT);
        denied.addAll(EnumSet.of(Denial.ELIGIBLE_ADJUSTED_COLLATERAL_UNKNOWN, Denial.AVAILABLE_COLLATERAL_UNKNOWN,
                Denial.CASH_COMPONENT_UNKNOWN, Denial.CASH_FIELD_ELIGIBILITY_UNKNOWN));
        var fields = new LinkedHashMap<String, Field>();
        // Explicit order gives stable JSON and human review diffs, independent of Map.copyOf iteration.
        for (var name : List.of("equityCashFields", "aggregateNet", "availableCollateral", "utilisedCollateral",
                "applicationCashLowerBound", "requiredMargin", "charges", "eligibleAdjustedCollateral",
                "actuallyAvailableCollateral", "applicableCashComponent", "cashFieldEligibility")) {
            var evidence = view.fields().getOrDefault(name, Evidence.UNKNOWN);
            var source = switch (evidence) {
                case OBSERVED -> Source.ACCOUNT_FIELDS;
                case DERIVED -> Source.APPLICATION_POLICY;
                case BROKER_AUTHORITATIVE -> Source.EXACT_REQUEST_CALCULATION;
                case UNKNOWN -> Source.UNESTABLISHED;
            };
            fields.put(name, new Field(evidence, source));
        }
        return new Detail(Readiness.NOT_READY, view.cashOnlyFunding(),
                origin == null ? Origin.CALLER_SUPPLIED_NORMALIZED : origin, TradingReadTypes.MarginSegment.EQUITY,
                accountObservedAt, quote == null ? null : quote.receivedAt(), accountAge, quoteAge,
                binding, fingerprint(expected), fields, List.copyOf(denied));
    }

    public enum Check {
        AUTHENTICATION, ACCOUNT_CURRENT, ACCOUNT_CLEAN, ORDERS_CLEAR, POSITIONS_CLEAR,
        MARKET_HEALTHY, MARKET_FRESH, INSTRUMENT_IDENTITY, QUOTE_CURRENT,
        CASH_ONLY_SUFFICIENT, COLLATERAL_ELIGIBLE, FULL_NOTIONAL, HALT_CLEAR, AUTHORIZATION
    }
    public enum State { PASS, BLOCKED, UNKNOWN }
    public record Summary(Readiness readiness, Detail funding, Map<Check, State> checks) {
        public Summary { checks = Collections.unmodifiableMap(new EnumMap<>(checks)); }
    }

    public enum Proof { PROVEN, UNKNOWN, STALE, CONFLICTING }
    public enum Question { ELIGIBLE_ADJUSTED_COLLATERAL, ACTUALLY_AVAILABLE_COLLATERAL,
        CASH_COMPONENT_REQUIREMENT, CASH_FIELD_ELIGIBILITY }
    /** Stable catalog identifiers, not assertions that an endpoint was called. */
    public enum SourceIdentifier { KITE_V3_FUNDS_SCHEMA, KITE_V3_ORDER_MARGIN_SCHEMA,
        APPLICATION_CASH_POLICY, NO_ACCOUNT_ATTESTATION }
    public enum EvidenceDenial { INDEPENDENT_EVIDENCE_MISSING, AUTH_REQUIRED, INPUT_UNAVAILABLE,
        INPUT_STALE, FUTURE_OBSERVATION, REQUEST_OR_REFERENCE_CONFLICT }
    public record ContractField(Evidence classification, SourceIdentifier schemaSource, Proof independentAuthority) {}
    /** effectiveAt is absent until an independently verified broker assertion supplies it. */
    public record Term(Proof status, SourceIdentifier source, Instant effectiveAt, EvidenceDenial denial) {}
    public record Scope(TradingReadTypes.MarginSegment segment, TradingReadTypes.Product product,
            String requestFingerprint, String instrumentMappingFingerprint, Binding requestBinding,
            Binding referenceBinding, Instant accountObservedAt, Instant quoteReceivedAt, Instant referenceObservedAt,
            Freshness accountFreshness, Freshness quoteFreshness, Freshness referenceFreshness) {}
    public record CollateralContract(String version, Readiness collateralAssistedReadiness,
            RiskReason cashOnlyFunding, Origin origin, Scope scope,
            Map<String, ContractField> observations, Map<Question, Term> questions) {
        public CollateralContract {
            observations = Collections.unmodifiableMap(new LinkedHashMap<>(observations));
            questions = Collections.unmodifiableMap(new EnumMap<>(questions));
        }
    }

    /**
     * Pure projection of a single equity receipt. Inputs remain caller-supplied and unverified.
     * No full account/positions/orders capacity is fabricated, and no term becomes PROVEN.
     */
    public static CollateralContract equityObservation(OrderMarginQuote quote, OrderMarginQuote.Request expected,
            com.kitehybrid.platform.instrument.domain.Instrument reference, RiskLimits limits,
            Instant receivedAt, Instant referenceObservedAt, Instant now, boolean authenticated, Origin origin) {
        var base = collateralContract(null, null, quote, expected, reference, limits,
                receivedAt, referenceObservedAt, now, authenticated, origin);
        var fields = new LinkedHashMap<>(base.observations());
        if (authenticated && receivedAt != null) {
            for (var name : List.of("equityCashFields", "aggregateNet", "availableCollateral", "utilisedCollateral"))
                fields.put(name, new ContractField(Evidence.OBSERVED, SourceIdentifier.KITE_V3_FUNDS_SCHEMA, Proof.UNKNOWN));
        }
        return new CollateralContract(base.version(), base.collateralAssistedReadiness(),
                base.cashOnlyFunding(), base.origin(), base.scope(), fields, base.questions());
    }

    /**
     * Versioned redacted contract for unverified normalized inputs. No caller switch or numeric term
     * can establish independent broker authority. This method has no PROVEN-producing path.
     * A future authoritative provider requires separate review, not a boolean added to this API.
     */
    public static CollateralContract collateralContract(CashAccountCapacity.Capacity account, BrokerMargins margins,
            OrderMarginQuote quote, OrderMarginQuote.Request expected,
            com.kitehybrid.platform.instrument.domain.Instrument reference, RiskLimits limits,
            Instant accountObservedAt, Instant referenceObservedAt, Instant now, boolean authenticated, Origin origin) {
        var detail = detail(account, margins, quote, expected, limits, accountObservedAt, now, authenticated, origin);
        var referenceAge = freshness(referenceObservedAt, now, limits == null ? null : limits.registryMaxAge());
        Binding referenceBinding = Binding.UNKNOWN;
        String mapping = null;
        if (reference != null && expected != null) {
            referenceBinding = reference.id().equals(expected.instrumentId())
                    && reference.exchange().equals(expected.exchange()) && reference.tradingSymbol().equals(expected.symbol())
                    && reference.type() == InstrumentType.CASH && reference.brokerId().broker().equals("ZERODHA")
                    ? Binding.MATCHED : Binding.CONFLICTING;
            // The stable platform ID alone does not bind a changing broker token mapping.
            // Tokens here are instrument reference identifiers, never authentication credentials.
            mapping = mappingFingerprint(detail.requestFingerprint(), reference.brokerId().broker(), reference.brokerId().value());
        }
        EvidenceDenial denial = EvidenceDenial.INDEPENDENT_EVIDENCE_MISSING;
        Proof status = Proof.UNKNOWN;
        if (detail.requestBinding() == Binding.CONFLICTING || referenceBinding == Binding.CONFLICTING) {
            status = Proof.CONFLICTING; denial = EvidenceDenial.REQUEST_OR_REFERENCE_CONFLICT;
        } else if (detail.accountFreshness() == Freshness.FUTURE || detail.quoteFreshness() == Freshness.FUTURE
                || referenceAge == Freshness.FUTURE) {
            status = Proof.CONFLICTING; denial = EvidenceDenial.FUTURE_OBSERVATION;
        } else if (!authenticated) {
            denial = EvidenceDenial.AUTH_REQUIRED;
        } else if (detail.accountFreshness() == Freshness.STALE || detail.quoteFreshness() == Freshness.STALE
                || referenceAge == Freshness.STALE) {
            status = Proof.STALE; denial = EvidenceDenial.INPUT_STALE;
        } else if (detail.cashOnlyFunding() == RiskReason.MIS_MARGIN_UNAVAILABLE || referenceBinding == Binding.UNKNOWN
                || referenceAge == Freshness.UNKNOWN) {
            denial = EvidenceDenial.INPUT_UNAVAILABLE;
        }
        var observations = new LinkedHashMap<String, ContractField>();
        for (var entry : detail.fields().entrySet()) {
            var field = entry.getValue();
            // Legacy label describes a calculator contract, not authentication of caller-supplied values.
            var classification = field.evidence() == Evidence.BROKER_AUTHORITATIVE ? Evidence.OBSERVED : field.evidence();
            var schema = switch (field.source()) {
                case ACCOUNT_FIELDS -> SourceIdentifier.KITE_V3_FUNDS_SCHEMA;
                case EXACT_REQUEST_CALCULATION -> SourceIdentifier.KITE_V3_ORDER_MARGIN_SCHEMA;
                case APPLICATION_POLICY -> SourceIdentifier.APPLICATION_CASH_POLICY;
                case UNESTABLISHED -> SourceIdentifier.NO_ACCOUNT_ATTESTATION;
            };
            observations.put(entry.getKey(), new ContractField(classification, schema, Proof.UNKNOWN));
        }
        var questions = new EnumMap<Question, Term>(Question.class);
        for (var question : Question.values())
            questions.put(question, new Term(status, SourceIdentifier.NO_ACCOUNT_ATTESTATION, null, denial));
        var scope = new Scope(TradingReadTypes.MarginSegment.EQUITY, TradingReadTypes.Product.INTRADAY,
                detail.requestFingerprint(), mapping, detail.requestBinding(), referenceBinding,
                accountObservedAt, detail.quoteReceivedAt(), referenceObservedAt,
                detail.accountFreshness(), detail.quoteFreshness(), referenceAge);
        return new CollateralContract("KiteMisCollateralEvidence.v1", Readiness.NOT_READY,
                referenceBinding == Binding.MATCHED && referenceAge == Freshness.CURRENT
                        ? detail.cashOnlyFunding() : RiskReason.MIS_MARGIN_UNAVAILABLE,
                detail.origin(), scope, observations, questions);
    }

    private static String mappingFingerprint(String request, String broker, String instrumentToken) {
        try {
            var text = "mis-mapping-v1|" + request + "|" + broker.length() + ":" + broker
                    + "|" + instrumentToken.length() + ":" + instrumentToken;
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * Synthetic-only composition of existing domain checks. No fetch, cache, risk approval or permit.
     * Caller booleans are observations, never authority; authorization is unconditionally absent.
     * Recompute from newly supplied observations for every review. This is not a final dispatch fence.
     */
    public static Summary summarizeSynthetic(OrderRiskInput input, OrderMarginQuote.Request expected,
            RiskLimits limits, BigDecimal reviewedNotionalCeiling, Instant accountObservedAt,
            Instant now, boolean authenticated, boolean halted) {
        var checks = new EnumMap<Check, State>(Check.class);
        for (var check : Check.values()) checks.put(check, State.UNKNOWN);
        checks.put(Check.AUTHENTICATION, state(authenticated));
        checks.put(Check.HALT_CLEAR, state(!halted));
        checks.put(Check.AUTHORIZATION, State.BLOCKED);
        checks.put(Check.COLLATERAL_ELIGIBLE, State.UNKNOWN);
        CashAccountCapacity.Capacity capacity = null;
        OrderMarginQuote quote = input == null ? null : input.marginQuote().orElse(null);
        try {
            if (input != null && expected != null && limits != null && now != null) {
                boolean current = authenticated && freshness(accountObservedAt, now, limits.marketDataMaxAge()) == Freshness.CURRENT;
                checks.put(Check.ACCOUNT_CURRENT, state(current));
                if (current && input.orders() != null && input.positions() != null && input.holdings() != null) {
                    boolean ordersClear = input.orders().stream().allMatch(o -> switch (o.status()) {
                        case FILLED, CANCELLED, REJECTED -> true;
                        default -> false;
                    });
                    boolean positionsClear = java.util.stream.Stream.concat(input.positions().net().stream(),
                            input.positions().day().stream()).allMatch(p -> p.quantity() == 0);
                    checks.put(Check.ORDERS_CLEAR, state(ordersClear));
                    checks.put(Check.POSITIONS_CLEAR, state(positionsClear));
                    // First-review cleanliness concerns positions/orders. Holdings still enter shared exposure checks.
                    checks.put(Check.ACCOUNT_CLEAN, state(ordersClear && positionsClear));
                }
                checks.put(Check.MARKET_HEALTHY, state(input.marketHealthy()));
                var tick = input.ticks().get(expected.instrumentId());
                boolean freshTick = tick != null && tick.instrumentId().equals(expected.instrumentId())
                        && CashOrderRiskRules.priceReason(tick, now, limits.marketDataMaxAge()) == RiskReason.APPROVED;
                checks.put(Check.MARKET_FRESH, state(freshTick));
                var reference = input.instruments();
                var instrument = reference.byId().get(expected.instrumentId());
                boolean identity = instrument != null && instrument.type() == InstrumentType.CASH
                        && instrument.exchange().equals(expected.exchange()) && instrument.tradingSymbol().equals(expected.symbol())
                        && instrument.lotSize() > 0 && expected.quantity() % instrument.lotSize() == 0
                        && freshness(reference.refreshedAt(), now, limits.registryMaxAge()) == Freshness.CURRENT;
                checks.put(Check.INSTRUMENT_IDENTITY, state(identity));
                if (current) capacity = CashAccountCapacity.inspectIntraday(input, limits, expected.instrumentId(), now);
                if (identity && freshTick && input.marketHealthy() && limits.configured() && limits.enabled()
                        && reviewedNotionalCeiling != null && reviewedNotionalCeiling.signum() > 0
                        && reviewedNotionalCeiling.compareTo(new BigDecimal("10000")) <= 0) {
                    var valuation = ConservativeOrderValuation.evaluate(OrderType.MARKET, expected.quantity(),
                            tick.lastPrice(), Optional.empty(), limits.priceBuffer());
                    checks.put(Check.FULL_NOTIONAL, state(valuation.within(reviewedNotionalCeiling)
                            && valuation.within(limits.maxOrderValue()) && expected.quantity() <= limits.maxOrderQuantity()
                            && capacity != null && CashAccountCapacity.checkExposure(capacity, expected.quantity(), valuation, limits) == RiskReason.APPROVED));
                } else checks.put(Check.FULL_NOTIONAL, State.BLOCKED);
            }
        } catch (RuntimeException unavailable) {
            // Keep independent observations, leave uncomputed checks UNKNOWN, disclose no input or cause.
        }
        var funding = detail(capacity, input == null ? null : input.margins(), quote, expected, limits,
                accountObservedAt, now, authenticated, Origin.SYNTHETIC);
        checks.put(Check.QUOTE_CURRENT, state(funding.quoteFreshness() == Freshness.CURRENT
                && funding.requestBinding() == Binding.MATCHED));
        checks.put(Check.CASH_ONLY_SUFFICIENT, funding.cashOnlyFunding() == RiskReason.MIS_MARGIN_UNAVAILABLE
                ? State.UNKNOWN : state(funding.cashOnlyFunding() == RiskReason.APPROVED));
        return new Summary(Readiness.NOT_READY, funding, checks);
    }

    private static State state(boolean pass) { return pass ? State.PASS : State.BLOCKED; }
    private static Freshness freshness(Instant observed, Instant now, Duration age) {
        if (observed == null || now == null || age == null || age.isNegative() || age.isZero()) return Freshness.UNKNOWN;
        if (observed.isAfter(now)) return Freshness.FUTURE;
        return Duration.between(observed, now).compareTo(age) < 0 ? Freshness.CURRENT : Freshness.STALE;
    }
    private static String fingerprint(OrderMarginQuote.Request request) {
        if (request == null) return null;
        // Length prefixes prevent delimiter ambiguity; do not hash any account/session/token data.
        var canonical = new StringBuilder("mis-request-v1");
        for (var value : List.of(request.instrumentId().toString(), request.exchange(), request.symbol(),
                request.side().name(), request.orderType().name(), request.product().name(),
                request.validity().name(), request.variety().name(), Long.toString(request.quantity())))
            canonical.append('|').append(value.length()).append(':').append(value);
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * Caller supplies one current synchronous account observation and its exact-request quote.
     * Local receipt time is not a broker atomic-snapshot guarantee. No snapshot is retained here.
     * Numeric collateral terms alone cannot establish broker provenance, so they are never promoted.
     */
    public static View inspect(CashAccountCapacity.Capacity account, BrokerMargins margins,
            OrderMarginQuote quote, OrderMarginQuote.Request expected, RiskLimits limits,
            Instant accountObservedAt, Instant now, boolean authenticated) {
        var fields = new java.util.LinkedHashMap<String, Evidence>();
        for (var name : java.util.List.of("equityCashFields", "aggregateNet", "availableCollateral",
                "utilisedCollateral", "applicationCashLowerBound", "requiredMargin", "charges",
                "eligibleAdjustedCollateral", "applicableCashComponent", "cashFieldEligibility"))
            fields.put(name, Evidence.UNKNOWN);
        RiskReason reason = RiskReason.MIS_MARGIN_UNAVAILABLE;
        try {
            if (!authenticated || !limits.configured() || !limits.enabled()
                    || !fresh(accountObservedAt, now, limits.marketDataMaxAge())
                    || quote == null || !quote.request().equals(expected)
                    || !fresh(quote.receivedAt(), now, limits.marketDataMaxAge()))
                return new View(Readiness.NOT_READY, reason, fields);
            // Evaluate the existing cash-only branch even if a caller supplied unverified terms.
            var cashQuote = new OrderMarginQuote(quote.request(), quote.requiredMargin(), quote.charges(),
                    Optional.empty(), quote.receivedAt());
            IntradayAccountCapacity.funding(account, margins, cashQuote);
            for (var name : java.util.List.of("equityCashFields", "aggregateNet", "availableCollateral", "utilisedCollateral"))
                fields.put(name, Evidence.OBSERVED);
            fields.put("applicationCashLowerBound", Evidence.DERIVED);
            fields.put("requiredMargin", Evidence.BROKER_AUTHORITATIVE);
            fields.put("charges", Evidence.BROKER_AUTHORITATIVE);
            reason = IntradayAccountCapacity.checkFunding(account, margins, cashQuote, limits);
        } catch (RuntimeException unavailable) {
            // Missing/invalid observations disclose neither amounts nor upstream errors.
        }
        // Cash sufficiency is distinct from unresolved broker cash/collateral eligibility and execution readiness.
        return new View(Readiness.NOT_READY, reason, fields);
    }

    private static boolean fresh(Instant observed, Instant now, Duration age) {
        return !observed.isAfter(now) && Duration.between(observed, now).compareTo(age) < 0;
    }
}
