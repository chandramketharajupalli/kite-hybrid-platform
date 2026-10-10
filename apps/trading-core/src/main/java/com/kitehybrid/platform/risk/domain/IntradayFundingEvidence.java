package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.BrokerMargins;
import com.kitehybrid.platform.broker.domain.read.OrderMarginQuote;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Redacted, pure observation of existing cash-only funding policy; never a risk decision or permit. */
public final class IntradayFundingEvidence {
    private IntradayFundingEvidence() {}
    public enum Evidence { OBSERVED, DERIVED, BROKER_AUTHORITATIVE, UNKNOWN }
    public enum Readiness { NOT_READY }
    public record View(Readiness readiness, RiskReason cashOnlyFunding, Map<String, Evidence> fields) {
        public View { fields = Map.copyOf(fields); }
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
