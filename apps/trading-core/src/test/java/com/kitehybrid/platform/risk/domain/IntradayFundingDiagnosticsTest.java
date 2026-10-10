package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.kitehybrid.platform.risk.domain.IntradayAccountCapacityTest.*;
import static com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.*;
import static org.assertj.core.api.Assertions.*;

class IntradayFundingDiagnosticsTest {
    final IntradayAccountCapacityTest fixture = new IntradayAccountCapacityTest();
    final OrderMarginQuote.Request request = fixture.quote("2000", false).request();
    OrderMarginQuote quote(String margin, String charges, Instant at) {
        return new OrderMarginQuote(request, d(margin), d(charges), Optional.empty(), at);
    }
    OrderRiskInput input(String cash) { return fixture.input(margins(cash, "1000000", true), quote("2000", "1.25", NOW), "800"); }
    Summary summary(OrderRiskInput input, Instant observed, Instant now, boolean auth, boolean halt) {
        return summarizeSynthetic(input, request, fixture.limits, d("10000"), observed, now, auth, halt);
    }
    Detail detail(String cash, OrderMarginQuote quote, Instant observed, boolean auth) {
        return IntradayFundingEvidence.detail(new CashAccountCapacity.Capacity(Z, Z, d(cash)),
                margins(cash, "1000000", true), quote, request, fixture.limits, observed, NOW, auth, Origin.SYNTHETIC);
    }
    OrderRiskInput replace(OrderRiskInput in, BrokerMargins margins, BrokerPositions positions, List<BrokerOrder> orders,
                          Map<com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId, Tick> ticks, OrderMarginQuote quote) {
        return new OrderRiskInput(in.instruments(), ticks, in.marketHealthy(), positions, in.holdings(), margins, orders, Optional.ofNullable(quote));
    }

    @Test void equalityIsOnlyCashPolicyEvidenceAndOutputIsDeterministicRedactedAndImmutable() throws Exception {
        var result = summary(input("2002.25"), NOW, NOW, true, true);
        assertThat(result.checks()).containsEntry(Check.CASH_ONLY_SUFFICIENT, State.PASS)
                .containsEntry(Check.COLLATERAL_ELIGIBLE, State.UNKNOWN).containsEntry(Check.AUTHORIZATION, State.BLOCKED)
                .containsEntry(Check.HALT_CLEAR, State.BLOCKED).containsEntry(Check.FULL_NOTIONAL, State.PASS);
        var f = result.funding();
        assertThat(f.readiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(f.origin()).isEqualTo(Origin.SYNTHETIC);
        assertThat(f.accountSegment()).isEqualTo(TradingReadTypes.MarginSegment.EQUITY);
        assertThat(f.accountObservedAt()).isEqualTo(NOW);
        assertThat(f.quoteReceivedAt()).isEqualTo(NOW);
        assertThat(f.requestBinding()).isEqualTo(Binding.MATCHED);
        assertThat(f.requestFingerprint()).matches("[0-9a-f]{64}");
        assertThat(f.denials()).containsExactly(Denial.ELIGIBLE_ADJUSTED_COLLATERAL_UNKNOWN, Denial.AVAILABLE_COLLATERAL_UNKNOWN,
                Denial.CASH_COMPONENT_UNKNOWN, Denial.CASH_FIELD_ELIGIBILITY_UNKNOWN);
        assertThat(f.fields().get("requiredMargin")).isEqualTo(new Field(Evidence.BROKER_AUTHORITATIVE, Source.EXACT_REQUEST_CALCULATION));
        assertThat(f.fields().get("actuallyAvailableCollateral")).isEqualTo(new Field(Evidence.UNKNOWN, Source.UNESTABLISHED));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var json = mapper.writeValueAsString(result);
        assertThat(json).isEqualTo(mapper.writeValueAsString(summary(input("2002.25"), NOW, NOW, true, true)));
        assertThat(json + result).doesNotContain("2002.25", "1000000", "SBIN", request.instrumentId().toString(), "1.25");
        assertThatThrownBy(() -> f.fields().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.checks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest @CsvSource({"2002.24,2000,1.25", "2002.25,2000.01,1.25", "2002.25,2000,1.26", "-1,2000,1.25"})
    void cashDeficitAndIncreasingCostsCannotUsePositiveNetOrCollateral(String cash, String margin, String charges) {
        var result = detail(cash, quote(margin, charges, NOW), NOW, true);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
        assertThat(result.denials()).contains(Denial.CASH_POLICY_INSUFFICIENT);
    }

    @ParameterizedTest @CsvSource({"account-stale,ACCOUNT_STALE", "account-future,ACCOUNT_FUTURE",
            "quote-stale,QUOTE_STALE", "quote-future,QUOTE_FUTURE", "auth,AUTH_REQUIRED", "missing,QUOTE_MISSING"})
    void staleFutureMissingAndAuthFailureCannotReuseEvidence(String fault, Denial denial) {
        var q = fault.equals("missing") ? null : quote("2000", "1.25",
                fault.equals("quote-stale") ? NOW.minusSeconds(5) : fault.equals("quote-future") ? NOW.plusNanos(1) : NOW);
        var observed = fault.equals("account-stale") ? NOW.minusSeconds(5) : fault.equals("account-future") ? NOW.plusNanos(1) : NOW;
        var result = detail("2002.25", q, observed, !fault.equals("auth"));
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.denials()).contains(denial);
        assertThat(result.fields().values()).allMatch(f -> f.evidence() == Evidence.UNKNOWN);
    }

    @ParameterizedTest @ValueSource(strings = {"symbol", "quantity", "instrument"})
    void fullRequestMismatchIsConflictingAndChangesFingerprint(String change) {
        var other = new OrderMarginQuote.Request(change.equals("instrument")
                ? new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(UUID.fromString("00000000-0000-0000-0000-000000000001")) : request.instrumentId(),
                request.exchange(), change.equals("symbol") ? "OTHER" : request.symbol(), request.side(), request.orderType(),
                request.product(), request.validity(), request.variety(), change.equals("quantity") ? request.quantity() + 1 : request.quantity());
        var q = new OrderMarginQuote(other, d("2000"), Z, Optional.empty(), NOW);
        var result = detail("2002.25", q, NOW, true);
        assertThat(result.requestBinding()).isEqualTo(Binding.CONFLICTING);
        assertThat(result.denials()).contains(Denial.REQUEST_CONFLICT);
        var matched = IntradayFundingEvidence.detail(new CashAccountCapacity.Capacity(Z, Z, d("2002.25")),
                margins("2002.25", "0", true), q, other, fixture.limits, NOW, NOW, true, Origin.SYNTHETIC);
        assertThat(matched.requestFingerprint()).isNotEqualTo(result.requestFingerprint());
    }

    @ParameterizedTest @ValueSource(strings = {"product", "side", "type", "validity", "exchange", "quantity"})
    void unsupportedRequestIsRejectedBeforeAnyDiagnosticOrProvider(String change) {
        assertThatThrownBy(() -> new OrderMarginQuote.Request(request.instrumentId(), change.equals("exchange") ? "MCX" : "NSE", "SYNTHETIC",
                change.equals("side") ? TradingReadTypes.Side.SELL : request.side(),
                change.equals("type") ? TradingReadTypes.OrderType.LIMIT : request.orderType(),
                change.equals("product") ? TradingReadTypes.Product.DELIVERY : request.product(),
                change.equals("validity") ? TradingReadTypes.Validity.IMMEDIATE_OR_CANCEL : request.validity(),
                request.variety(), change.equals("quantity") ? 0 : request.quantity())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"large-utilised", "fully-utilised", "commodity", "cash-conflict", "negative-collateral"})
    void utilisationAndOtherSegmentsNeverSupplyEligibleFunding(String change) {
        var in = input("1"); var m = in.margins(); var e = m.segments().get(TradingReadTypes.MarginSegment.EQUITY);
        var a = e.available(); var u = e.utilised();
        var updated = new BrokerMargins.SegmentMargin(!change.equals("commodity"), e.net(),
                new BrokerMargins.AvailableMargin(Z, change.equals("cash-conflict") ? d("1000000") : a.cash(), a.openingBalance(), a.liveBalance(),
                        change.equals("negative-collateral") ? d("-1") : a.collateral(), Z),
                new BrokerMargins.UtilisedMargin(u.debits(),Z,Z,Z,Z,Z,Z,Z,Z,Z,
                        change.equals("fully-utilised") ? a.collateral() : change.equals("large-utilised") ? d("999999999") : Z,Z));
        var result = summary(replace(in, new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY, updated,
                TradingReadTypes.MarginSegment.COMMODITY, m.segments().get(TradingReadTypes.MarginSegment.COMMODITY))),
                in.positions(), in.orders(), in.ticks(), in.marginQuote().orElseThrow()), NOW, NOW, true, true);
        assertThat(result.checks().get(Check.CASH_ONLY_SUFFICIENT)).isNotEqualTo(State.PASS);
        assertThat(result.funding().fields().get("actuallyAvailableCollateral").evidence()).isEqualTo(Evidence.UNKNOWN);
    }

    @ParameterizedTest @EnumSource(TradingReadTypes.OrderStatus.class)
    void normalizedTerminalOrdersAreNonblockingAndAllPendingOrUnknownStatesBlock(TradingReadTypes.OrderStatus status) {
        var in = input("2002.25");
        long filled = status == TradingReadTypes.OrderStatus.FILLED ? 2 : status == TradingReadTypes.OrderStatus.PARTIALLY_FILLED ? 1 : 0;
        var order = new BrokerOrder("synthetic-private-id", Optional.empty(), Optional.empty(), request.instrumentId(), request.side(),
                request.orderType(), request.product(), request.validity(), request.variety(), status, 2, filled, 2-filled,0,0,Z,Z,Z,
                NOW, Optional.of(NOW), Optional.of(NOW));
        var result = summary(replace(in, in.margins(), in.positions(), List.of(order), in.ticks(), in.marginQuote().orElseThrow()), NOW,NOW,true,true);
        boolean terminal = Set.of(TradingReadTypes.OrderStatus.FILLED, TradingReadTypes.OrderStatus.CANCELLED, TradingReadTypes.OrderStatus.REJECTED).contains(status);
        assertThat(result.checks().get(Check.ORDERS_CLEAR)).isEqualTo(terminal ? State.PASS : State.BLOCKED);
        assertThat(result.toString()).doesNotContain("synthetic-private-id");
    }

    @ParameterizedTest @ValueSource(strings = {"net", "day"})
    void nonzeroPositionInEitherArrayIsNotClean(String array) {
        var in = input("2002.25"); var totals = new BrokerPosition.SideTotals(0,Z,Z);
        var position = new BrokerPosition(request.instrumentId(), request.product(),1,0,BigDecimal.ONE,Z,Z,Z,Z,Z,Z,Z,Z,totals,totals,totals,totals);
        var positions = new BrokerPositions(array.equals("net") ? List.of(position) : List.of(), array.equals("day") ? List.of(position) : List.of());
        var result = summary(replace(in, in.margins(), positions, in.orders(), in.ticks(), in.marginQuote().orElseThrow()),NOW,NOW,true,true);
        assertThat(result.checks()).containsEntry(Check.POSITIONS_CLEAR, State.BLOCKED).containsEntry(Check.ACCOUNT_CLEAN, State.BLOCKED);
    }

    @ParameterizedTest @ValueSource(strings = {"price", "stale-price", "future-price", "market", "auth", "halt", "ceiling"})
    void recomputationCannotReuseAFormerPassingGate(String change) {
        var in = input("2002.25");
        assertThat(summary(in,NOW,NOW,true,false).checks()).containsEntry(Check.FULL_NOTIONAL, State.PASS);
        if (change.contains("price")) in = replace(in,in.margins(),in.positions(),in.orders(),
                Map.of(request.instrumentId(),new Tick(request.instrumentId(),d(change.equals("price") ? "910" : "800"),
                        change.equals("stale-price") ? NOW.minusSeconds(6) : change.equals("future-price") ? NOW.plusNanos(1) : NOW)),in.marginQuote().orElseThrow());
        if (change.equals("market")) in = new OrderRiskInput(in.instruments(),in.ticks(),false,in.positions(),in.holdings(),in.margins(),in.orders(),in.marginQuote());
        var result = summarizeSynthetic(in,request,fixture.limits,d(change.equals("ceiling") ? "10000.01" : "10000"),NOW,NOW,!change.equals("auth"),change.equals("halt"));
        var gate = change.equals("halt") ? Check.HALT_CLEAR : change.equals("auth") ? Check.AUTHENTICATION : Check.FULL_NOTIONAL;
        assertThat(result.checks().get(gate)).isEqualTo(State.BLOCKED);
        assertThat(result.checks().get(Check.AUTHORIZATION)).isEqualTo(State.BLOCKED);
        assertThat(result.readiness()).isEqualTo(Readiness.NOT_READY);
    }

    @Test void entirelyMissingEvidenceIsBoundedAndUnknownRatherThanReady() {
        var result = summarizeSynthetic(null,null,null,null,null,null,false,true);
        assertThat(result.checks()).containsEntry(Check.AUTHORIZATION, State.BLOCKED).containsEntry(Check.CASH_ONLY_SUFFICIENT, State.UNKNOWN);
        assertThat(result.funding().denials()).contains(Denial.ACCOUNT_UNAVAILABLE,Denial.QUOTE_MISSING,Denial.POLICY_UNAVAILABLE);
        assertThat(result.funding().requestFingerprint()).isNull();
    }

    @Test void callerNumericCollateralTermsCannotCreateAuthoritativeEligibility() {
        var result = detail("1",fixture.quote("2000",true),NOW,true);
        assertThat(result.fields().get("eligibleAdjustedCollateral").evidence()).isEqualTo(Evidence.UNKNOWN);
        assertThat(result.denials()).contains(Denial.CASH_COMPONENT_UNKNOWN,Denial.CASH_FIELD_ELIGIBILITY_UNKNOWN);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.COLLATERAL_UNSUPPORTED);
    }
}
