package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.OrderMarginQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kitehybrid.platform.risk.domain.IntradayAccountCapacityTest.*;
import static com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Evidence.*;
import static org.assertj.core.api.Assertions.assertThat;

class IntradayFundingEvidenceTest {
    final IntradayAccountCapacityTest fixture = new IntradayAccountCapacityTest();

    private IntradayFundingEvidence.View view(String cash, String required, String charges,
            Instant observed, Instant received, boolean authenticated) {
        var request = fixture.quote("2000", false).request();
        return IntradayFundingEvidence.inspect(new CashAccountCapacity.Capacity(Z, Z, d(cash)),
                margins(cash, "1000000", true), new OrderMarginQuote(request, d(required), d(charges),
                        Optional.empty(), received), request, fixture.limits, observed, NOW, authenticated);
    }

    @Test void cashEqualityIsSeparateFromUnresolvedCollateralAndReadinessAndHasNoSensitiveValues() throws Exception {
        var result = view("2002.25", "2000", "1.25", NOW, NOW, true);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.APPROVED);
        assertThat(result.readiness()).isEqualTo(IntradayFundingEvidence.Readiness.NOT_READY);
        assertThat(result.fields()).containsEntry("availableCollateral", OBSERVED)
                .containsEntry("applicationCashLowerBound", DERIVED)
                .containsEntry("requiredMargin", BROKER_AUTHORITATIVE)
                .containsEntry("eligibleAdjustedCollateral", UNKNOWN)
                .containsEntry("applicableCashComponent", UNKNOWN)
                .containsEntry("cashFieldEligibility", UNKNOWN);
        String serialized = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        assertThat(serialized).doesNotContain("2002.25", "1000000", "SBIN", "synthetic", "1.25");
    }

    @Test void onePaisaDropAndMarginOrChargeIncreaseDenyUnderUnchangedPolicy() {
        assertThat(view("2002.24", "2000", "1.25", NOW, NOW, true).cashOnlyFunding())
                .isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(view("2002.25", "2000.01", "1.25", NOW, NOW, true).cashOnlyFunding())
                .isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(view("2002.25", "2000", "1.26", NOW, NOW, true).cashOnlyFunding())
                .isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
    }

    @ParameterizedTest @ValueSource(strings = {"account-stale", "quote-stale", "future", "auth"})
    void staleOrUnauthenticatedEvidenceCannotReusePreviousObservations(String fault) {
        var result = view("2002.25", "2000", "1.25",
                fault.equals("account-stale") ? NOW.minusSeconds(5) : NOW,
                fault.equals("quote-stale") ? NOW.minusSeconds(5) : fault.equals("future") ? NOW.plusSeconds(1) : NOW,
                !fault.equals("auth"));
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.fields().values()).containsOnly(UNKNOWN);
        assertThat(result.readiness()).isEqualTo(IntradayFundingEvidence.Readiness.NOT_READY);
    }

    @Test void numericTermsCannotSelfCertifyCollateralProvenance() {
        var quote = fixture.quote("2000", true);
        var result = IntradayFundingEvidence.inspect(new CashAccountCapacity.Capacity(Z, Z, BigDecimal.ONE),
                margins("1", "1000000", true), quote, quote.request(), fixture.limits, NOW, NOW, true);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(result.fields()).containsEntry("eligibleAdjustedCollateral", UNKNOWN);
    }

    @Test void quoteForAnotherQuantityCannotPopulateEvidence() {
        var quote = fixture.quote("2000", false);
        var r = quote.request();
        var another = new OrderMarginQuote.Request(r.instrumentId(), r.exchange(), r.symbol(), r.side(),
                r.orderType(), r.product(), r.validity(), r.variety(), r.quantity() + 1);
        var result = IntradayFundingEvidence.inspect(new CashAccountCapacity.Capacity(Z, Z, d("1000000")),
                margins("1000000", "0", true), quote, another, fixture.limits, NOW, NOW, true);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.fields().values()).containsOnly(UNKNOWN);
    }
}
