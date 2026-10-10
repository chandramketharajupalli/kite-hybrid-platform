package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.kitehybrid.platform.risk.domain.IntradayAccountCapacityTest.*;
import static com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.*;
import static org.assertj.core.api.Assertions.*;

class IntradayCollateralContractTest {
    final IntradayAccountCapacityTest fixture = new IntradayAccountCapacityTest();
    final OrderMarginQuote.Request request = fixture.quote("2000", false).request();
    final Instrument reference = Instrument.create(new BrokerInstrumentId("ZERODHA", "123456"), SBIN.tradingSymbol(),
            "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), d("0.05"), 1);
    OrderMarginQuote quote(String required, String charges, Instant at, boolean terms) {
        return new OrderMarginQuote(request,d(required),d(charges),terms
                ? Optional.of(new OrderMarginQuote.CollateralTerms(d("1000000"),Z)) : Optional.empty(),at);
    }
    CollateralContract contract(String cash, OrderMarginQuote q, Instant observed, Instant referenceAt, boolean auth, Origin origin) {
        return collateralContract(new CashAccountCapacity.Capacity(Z,Z,d(cash)), margins(cash,"1000000",true),
                q,request,reference,fixture.limits,observed,referenceAt,NOW,auth,origin);
    }

    @ParameterizedTest @EnumSource(Origin.class)
    void neitherSyntheticNorCallerOriginCanPromoteQuoteOrNumericTermsToIndependentAuthority(Origin origin) {
        var result = contract("2002.25",quote("2000","1.25",NOW,true),NOW,NOW,true,origin);
        assertThat(result.version()).isEqualTo("KiteMisCollateralEvidence.v1");
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.APPROVED);
        assertThat(result.collateralAssistedReadiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.observations().values()).allMatch(v -> v.independentAuthority() == Proof.UNKNOWN)
                .noneMatch(v -> v.classification() == Evidence.BROKER_AUTHORITATIVE);
        assertThat(result.observations().get("requiredMargin")).isEqualTo(new ContractField(Evidence.OBSERVED,
                SourceIdentifier.KITE_V3_ORDER_MARGIN_SCHEMA,Proof.UNKNOWN));
        assertThat(result.questions()).hasSize(4);
        assertThat(result.questions().values()).allMatch(t -> t.status() == Proof.UNKNOWN && t.effectiveAt() == null
                && t.source() == SourceIdentifier.NO_ACCOUNT_ATTESTATION && t.denial() == EvidenceDenial.INDEPENDENT_EVIDENCE_MISSING);
        assertThat(result.scope().product()).isEqualTo(TradingReadTypes.Product.INTRADAY);
        assertThat(result.scope().segment()).isEqualTo(TradingReadTypes.MarginSegment.EQUITY);
    }

    @ParameterizedTest @CsvSource({"2002.24,2000,1.25", "2002.25,2000.01,1.25", "2002.25,2000,1.26", "-1,2000,1.25"})
    void contractPreservesCashPolicyAndNeverTreatsUnknownTermsAsZeroOrAsAuthority(String cash,String required,String charges) {
        var result = contract(cash,quote(required,charges,NOW,true),NOW,NOW,true,Origin.SYNTHETIC);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(result.questions().values()).allMatch(t -> t.status() == Proof.UNKNOWN);
    }

    @ParameterizedTest @CsvSource({"0,0", "0,2000", "800,0", "800,1000", "1000,1000", "1000,2000"})
    void changingUnverifiedAdjustedAmountOrCashRuleCannotRepairCashDeficitOrSupplyCategoryProof(
            String adjusted, String minimumCash) {
        // Synthetic candidate terms: changed haircut/category or cash-rule assumptions are not attestations.
        // The API has no authenticated category input; even a zero claimed cash requirement is unverified.
        var baseline = contract("2002.24",quote("2000","1.25",NOW,false),NOW,NOW,true,Origin.SYNTHETIC);
        var supplied = new OrderMarginQuote(request,d("2000"),d("1.25"),
                Optional.of(new OrderMarginQuote.CollateralTerms(d(adjusted),d(minimumCash))),NOW);
        var result = contract("2002.24",supplied,NOW,NOW,true,Origin.CALLER_SUPPLIED_NORMALIZED);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(result.questions()).isEqualTo(baseline.questions());
        assertThat(result.questions().values()).allMatch(t -> t.status() == Proof.UNKNOWN && t.effectiveAt() == null);
        assertThat(result.observations()).isEqualTo(baseline.observations());
        assertThat(result.collateralAssistedReadiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.scope().requestFingerprint()).isEqualTo(baseline.scope().requestFingerprint());
    }

    @ParameterizedTest @ValueSource(strings={"account-stale","quote-stale","reference-stale","account-future","quote-future","reference-future"})
    void validitySeparatesStaleAndConflictingContextFromMissingAuthority(String fault) {
        Instant accountAt = fault.equals("account-stale") ? NOW.minusSeconds(5) : fault.equals("account-future") ? NOW.plusNanos(1) : NOW;
        Instant quoteAt = fault.equals("quote-stale") ? NOW.minusSeconds(5) : fault.equals("quote-future") ? NOW.plusNanos(1) : NOW;
        Instant refAt = fault.equals("reference-stale") ? NOW.minusSeconds(60) : fault.equals("reference-future") ? NOW.plusNanos(1) : NOW;
        var result=contract("2002.25",quote("2000","1.25",quoteAt,false),accountAt,refAt,true,Origin.SYNTHETIC);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.questions().values()).allMatch(t -> t.status() == (fault.endsWith("stale") ? Proof.STALE : Proof.CONFLICTING)
                && t.effectiveAt() == null && t.denial() == (fault.endsWith("stale") ? EvidenceDenial.INPUT_STALE : EvidenceDenial.FUTURE_OBSERVATION));
    }

    @ParameterizedTest @ValueSource(strings={"auth","quote","account","reference","clock"})
    void unavailableInputsFailClosedWithoutThrowingOrManufacturingBrokerDates(String fault) {
        var result=collateralContract(fault.equals("account")?null:new CashAccountCapacity.Capacity(Z,Z,d("2002.25")),
                margins("2002.25","1000000",true),fault.equals("quote")?null:quote("2000","1.25",NOW,false),request,
                fault.equals("reference")?null:reference,fixture.limits,NOW,NOW,fault.equals("clock")?null:NOW,!fault.equals("auth"),Origin.SYNTHETIC);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.questions().values()).allMatch(t -> t.status()==Proof.UNKNOWN && t.effectiveAt()==null
                && t.denial()==(fault.equals("auth")?EvidenceDenial.AUTH_REQUIRED:EvidenceDenial.INPUT_UNAVAILABLE));
    }

    @ParameterizedTest @ValueSource(strings={"symbol","namespace","quantity"})
    void inconsistentQuoteOrReferenceCannotProveScope(String change) {
        var ref=Instrument.create(new BrokerInstrumentId(change.equals("namespace")?"OTHER":"ZERODHA","123456"),
                change.equals("symbol")?"OTHER":SBIN.tradingSymbol(),"NSE","CASH",InstrumentType.CASH,
                Optional.empty(),Optional.empty(),d("0.05"),1);
        var q=change.equals("quantity")?new OrderMarginQuote(new OrderMarginQuote.Request(request.instrumentId(),"NSE",request.symbol(),
                request.side(),request.orderType(),request.product(),request.validity(),request.variety(),request.quantity()+1),d("2000"),Z,Optional.empty(),NOW)
                :quote("2000","1.25",NOW,false);
        var result=collateralContract(new CashAccountCapacity.Capacity(Z,Z,d("2002.25")),margins("2002.25","0",true),q,request,ref,
                fixture.limits,NOW,NOW,NOW,true,Origin.SYNTHETIC);
        assertThat(result.questions().values()).allMatch(t -> t.status()==Proof.CONFLICTING
                && t.denial()==EvidenceDenial.REQUEST_OR_REFERENCE_CONFLICT);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
    }

    @Test void tokenMappingChangesAreVisibleEvenWhenStablePlatformIdentityAndQuoteAreUnchanged() {
        var original=contract("2002.25",quote("2000","1.25",NOW,false),NOW,NOW,true,Origin.SYNTHETIC);
        var changed=Instrument.create(new BrokerInstrumentId("ZERODHA","654321"),reference.tradingSymbol(),"NSE","CASH",InstrumentType.CASH,
                Optional.empty(),Optional.empty(),reference.tickSize(),reference.lotSize());
        var result=collateralContract(new CashAccountCapacity.Capacity(Z,Z,d("2002.25")),margins("2002.25","1000000",true),
                quote("2000","1.25",NOW,false),request,changed,fixture.limits,NOW,NOW,NOW,true,Origin.SYNTHETIC);
        assertThat(changed.id()).isEqualTo(reference.id());
        assertThat(result.scope().requestFingerprint()).isEqualTo(original.scope().requestFingerprint());
        assertThat(result.scope().instrumentMappingFingerprint()).isNotEqualTo(original.scope().instrumentMappingFingerprint());
        assertThat(result.questions().values()).noneMatch(t -> t.status()==Proof.PROVEN);
    }

    @Test void versionedSerializationIsRedactedDeterministicAndImmutableWhileLegacyApiIsCompatible() throws Exception {
        var q=quote("2000","1.25",NOW,true);
        var result=contract("2002.25",q,NOW,NOW,true,Origin.SYNTHETIC);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(json.writeValueAsString(result)).isEqualTo(json.writeValueAsString(contract("2002.25",q,NOW,NOW,true,Origin.SYNTHETIC)));
        assertThat(json.writeValueAsString(result)+result).doesNotContain("2002.25","1000000","1.25","123456","SBIN",reference.id().toString());
        assertThatThrownBy(()->result.questions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->result.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
        var legacy=inspect(new CashAccountCapacity.Capacity(Z,Z,d("2002.25")),margins("2002.25","1000000",true),q,request,fixture.limits,NOW,NOW,true);
        assertThat(legacy.fields().get("requiredMargin")).isEqualTo(Evidence.BROKER_AUTHORITATIVE);
        assertThat(legacy.cashOnlyFunding()).isEqualTo(result.cashOnlyFunding());
    }
}
