package com.kitehybrid.platform.operator.application;

import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.application.OrderExecutionProperties;
import com.kitehybrid.platform.order.domain.ConservativeOrderQuantitySizer;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Observational MARKET/BUY/DELIVERY/DAY/REGULAR plan. No order, authorization or runtime effects. */
public final class FirstLiveCandidatePlanner {
    public enum Status { SIZED_RECHECK_REQUIRED, NOT_ELIGIBLE, NOT_READY }
    public enum Reason { NONE, EVIDENCE_UNAVAILABLE, REFERENCE_NOT_ELIGIBLE, MARKET_NOT_FRESH,
        LIMIT_UNCONFIGURED, ACCOUNT_NOT_CURRENT, NO_VALID_LOT, MARGIN_ESTIMATE_UNAVAILABLE, MIS_MARGIN_INSUFFICIENT, COLLATERAL_UNSUPPORTED, MIS_MARGIN_UNAVAILABLE }

    /** Explicit conservative remaining capacity, AFTER existing holdings/positions and cash reserve.
     * This is caller-supplied evidence, not a claim that the planner reads or certifies an account. */
    public record Headroom(long positionUnits, BigDecimal exposure, BigDecimal cashAfterReserve,
                           Instant observedAt, Duration maxAge, boolean supported) {
        public Headroom {
            if (positionUnits < 0) throw new IllegalArgumentException("Invalid headroom");
            amount(exposure); amount(cashAfterReserve);
            Objects.requireNonNull(observedAt); positiveDuration(maxAge);
        }
        public static Headroom from(OrderRiskInput input, RiskLimits limits, InstrumentId target,
                                    Instant observedAt, Instant now, Duration maxAge) {
            if (!input.marketHealthy() || !fresh(input.instruments().refreshedAt(), now, limits.registryMaxAge())
                    || !fresh(observedAt, now, maxAge)) throw new IllegalArgumentException("Account evidence not current");
            var capacity = CashAccountCapacity.inspect(input, limits, target, now);
            long units = BigDecimal.valueOf(limits.maxPositionQuantity()).subtract(capacity.heldUnits())
                    .max(BigDecimal.ZERO).longValueExact();
            return new Headroom(units, limits.maxExposure().subtract(capacity.existingExposure()).max(BigDecimal.ZERO),
                    capacity.usableCash().subtract(limits.cashReserve()).max(BigDecimal.ZERO), observedAt, maxAge, true);
        }
    }

    /** Only bounded, broker-independent evidence can leave the planning boundary. */
    public record Plan(InstrumentId instrumentId, String exchange, String symbol, String segment,
                       int lotSize, BigDecimal tickSize, BigDecimal observedPrice,
                       BigDecimal conservativeUnitPrice, long quantity, BigDecimal conservativeNotional,
                       BigDecimal humanCeiling, Duration armDuration, Instant evidenceTimestamp,
                       long registryVersion, String riskPolicyVersion, String evidenceVersion, Status status, Reason reason, OrderProduct product, Optional<BigDecimal> estimatedMarginAndCharges) {
        public OrderSide side() { return OrderSide.BUY; }
        public OrderType orderType() { return OrderType.MARKET; }
        public OrderValidity validity() { return OrderValidity.DAY; }
        public OrderVariety variety() { return OrderVariety.REGULAR; }
    }

    public record Evidence(InstrumentSnapshot reference, Tick tick, MarketDataHealth health,
                           Set<InstrumentId> desired, Set<InstrumentId> active, boolean enabled,
                           Headroom headroom) {
        public Evidence { desired = Set.copyOf(desired); active = Set.copyOf(active); }
        @Override public String toString() { return "CandidateEvidence[details withheld]"; }
    }

    public Plan plan(String exchange, String symbol, BigDecimal humanCeiling, Duration requestedArm,
                     OrderExecutionProperties execution, LiveTestProperties live, Evidence evidence, Instant now) {
        return plan(exchange,symbol,humanCeiling,requestedArm,execution,live,evidence,now,OrderProduct.DELIVERY);
    }
    private Plan plan(String exchange,String symbol,BigDecimal humanCeiling,Duration requestedArm,
                      OrderExecutionProperties execution,LiveTestProperties live,Evidence evidence,Instant now,OrderProduct product) {
        try {
            amount(humanCeiling); positiveDuration(requestedArm);
            var risk = Objects.requireNonNull(execution.riskLimits());
            var instrument = evidence.reference().byExchangeAndSymbol().get(new ExchangeSymbol(exchange, symbol));
            if (instrument == null || !evidence.enabled() || instrument.type() != InstrumentType.CASH
                    || !(instrument.exchange().equals("NSE") || instrument.exchange().equals("BSE"))
                    || !fresh(evidence.reference().refreshedAt(), now, risk.registryMaxAge()))
                return denied(humanCeiling, now, Reason.REFERENCE_NOT_ELIGIBLE);
            if (!execution.enabled() || !live.configured() || !risk.enabled() || !risk.configured()
                    || !execution.allowedInstruments().contains(instrument.id())
                    || !live.allowedInstruments().contains(instrument.id()) || execution.maxQuantity() <= 0
                    || execution.maxNotional().signum() <= 0 || humanCeiling.signum() <= 0)
                return denied(humanCeiling, now, Reason.LIMIT_UNCONFIGURED);
            var tick = evidence.tick(); var h = evidence.health();
            Duration age = execution.marketDataMaxAge().compareTo(risk.marketDataMaxAge()) <= 0
                    ? execution.marketDataMaxAge() : risk.marketDataMaxAge();
            if (tick == null || !tick.instrumentId().equals(instrument.id()) || tick.lastPrice().signum() <= 0
                    || h == null || h.connectionState() != MarketDataGateway.State.CONNECTED
                    || h.status() != MarketDataHealth.Status.FRESH || h.reason() != MarketDataHealth.Reason.NONE
                    || h.desiredSubscriptions() <= 0 || h.activeSubscriptions() != h.desiredSubscriptions()
                    || !evidence.desired().contains(instrument.id()) || !evidence.active().contains(instrument.id())
                    || !fresh(tick.receivedAt(), now, age)
                    || tick.exchangeTimestamp().filter(at -> !fresh(at, now, age)).isPresent())
                return denied(humanCeiling, now, Reason.MARKET_NOT_FRESH);
            var headroom = evidence.headroom();
            if (headroom == null || !headroom.supported() || !fresh(headroom.observedAt(), now, headroom.maxAge()))
                return denied(humanCeiling, now, Reason.ACCOUNT_NOT_CURRENT);
            var ceiling = humanCeiling.min(execution.maxNotional()).min(live.maxNotional())
                    .min(risk.maxOrderValue()).min(risk.maxExposure()).min(headroom.exposure());
            if(product==OrderProduct.DELIVERY) ceiling=ceiling.min(headroom.cashAfterReserve());
            var size = ConservativeOrderQuantitySizer.size(tick.lastPrice(), risk.priceBuffer(), ceiling,
                    instrument.lotSize(), List.of(execution.maxQuantity(), live.maxQuantity(), risk.maxOrderQuantity(),
                            risk.maxPositionQuantity(), headroom.positionUnits()));
            // Repeat every independent money comparison on the exact recomputed result.
            for (var cap : List.of(humanCeiling, execution.maxNotional(), live.maxNotional(), risk.maxOrderValue(),
                    risk.maxExposure(), headroom.exposure(), product==OrderProduct.DELIVERY?headroom.cashAfterReserve():humanCeiling))
                if (size.conservativeNotional().compareTo(cap) > 0) throw new IllegalArgumentException();
            Duration duration = requestedArm.compareTo(live.armMaxDuration()) <= 0 ? requestedArm : live.armMaxDuration();
            return new Plan(instrument.id(), instrument.exchange(), instrument.tradingSymbol(), instrument.segment(),
                    instrument.lotSize(), instrument.tickSize(), tick.lastPrice(), size.conservativeUnitPrice(),
                    size.quantity(), size.conservativeNotional(), humanCeiling, duration, tick.receivedAt(),
                    evidence.reference().version(), risk.version(), fingerprint(execution, live, evidence, requestedArm),
                    size.quantity() == 0 ? Status.NOT_ELIGIBLE : Status.SIZED_RECHECK_REQUIRED,
                    size.quantity() == 0 ? Reason.NO_VALID_LOT : Reason.NONE,product,Optional.empty());
        } catch (RuntimeException unavailable) { return denied(humanCeiling, now, Reason.EVIDENCE_UNAVAILABLE); }
    }

    /** Quantity is allocated from FULL notional before the separate, exact-request funding gate.
     * A missing quote returns the bounded sized quantity with NOT_READY, for subsequent read-only estimation. */
    public Plan planIntraday(String exchange,String symbol,BigDecimal humanCeiling,Duration requestedArm,
            OrderExecutionProperties execution,LiveTestProperties live,Evidence evidence,OrderRiskInput account,Instant now) {
        Plan sized=null;
        try {
            var limits=execution.riskLimits(); var instrument=evidence.reference().byExchangeAndSymbol().get(new ExchangeSymbol(exchange,symbol));
            if(!"NSE".equals(exchange) || instrument==null || !account.marketHealthy()
                    || account.instruments().version()!=evidence.reference().version()
                    || !account.instruments().byId().equals(evidence.reference().byId())
                    || !account.instruments().refreshedAt().equals(evidence.reference().refreshedAt())
                    || !Objects.equals(account.ticks().get(instrument.id()),evidence.tick()))
                return misResult(denied(humanCeiling,now,Reason.ACCOUNT_NOT_CURRENT),Reason.ACCOUNT_NOT_CURRENT,Optional.empty(),null);
            var capacity=CashAccountCapacity.inspectIntraday(account,limits,instrument.id(),now);
            // The caller's observation time must be current; capacities themselves come from the shared domain contract.
            var observed=evidence.headroom();
            var headroom=new Headroom(Math.min(observed.positionUnits(),BigDecimal.valueOf(limits.maxPositionQuantity())
                            .subtract(capacity.heldUnits()).max(BigDecimal.ZERO).longValueExact()),
                    observed.exposure().min(limits.maxExposure().subtract(capacity.existingExposure()).max(BigDecimal.ZERO)),
                    capacity.usableCash().subtract(limits.cashReserve()).max(BigDecimal.ZERO),observed.observedAt(),observed.maxAge(),observed.supported());
            var current=new Evidence(evidence.reference(),evidence.tick(),evidence.health(),evidence.desired(),evidence.active(),evidence.enabled(),headroom);
            sized=plan(exchange,symbol,humanCeiling,requestedArm,execution,live,current,now,OrderProduct.INTRADAY);
            if(sized.status()!=Status.SIZED_RECHECK_REQUIRED) return misResult(sized,sized.reason(),Optional.empty(),null);
            var q=account.marginQuote().orElse(null);
            var request=new com.kitehybrid.platform.broker.domain.read.OrderMarginQuote.Request(instrument.id(),instrument.exchange(),instrument.tradingSymbol(),
                    com.kitehybrid.platform.broker.domain.read.TradingReadTypes.Side.BUY,
                    com.kitehybrid.platform.broker.domain.read.TradingReadTypes.OrderType.MARKET,
                    com.kitehybrid.platform.broker.domain.read.TradingReadTypes.Product.INTRADAY,
                    com.kitehybrid.platform.broker.domain.read.TradingReadTypes.Validity.DAY,
                    com.kitehybrid.platform.broker.domain.read.TradingReadTypes.Variety.REGULAR,sized.quantity());
            if(q==null || !request.equals(q.request()) || !fresh(q.receivedAt(),now,execution.marketDataMaxAge()))
                return misResult(sized,Reason.MARGIN_ESTIMATE_UNAVAILABLE,Optional.empty(),null);
            var reason=IntradayAccountCapacity.checkFunding(capacity,account.margins(),q,limits);
            var funding=IntradayAccountCapacity.funding(capacity,account.margins(),q);
            String fingerprint=sized.evidenceVersion()+"|"+q.receivedAt()+"|"+q.requiredMargin()+"|"+q.charges()
                    +"|"+funding.excludedCredits()+"|"+funding.collateralDeductions()+"|"+funding.cash()+"|"+funding.eligibleCollateral()
                    +"|"+funding.effectiveCapacity()+"|"+q.collateralTerms().map(t->t.minimumCash().toPlainString()).orElse("unknown");
            return misResult(sized,reason==RiskReason.APPROVED?Reason.NONE:Reason.valueOf(reason.name()),
                    Optional.of(q.requiredMargin().add(q.charges())),hash(fingerprint));
        } catch(RuntimeException unavailable) {
            return misResult(sized==null?denied(humanCeiling,now,Reason.MIS_MARGIN_UNAVAILABLE):sized,
                    Reason.MIS_MARGIN_UNAVAILABLE,Optional.empty(),null);
        }
    }
    private static Plan misResult(Plan p,Reason reason,Optional<BigDecimal> margin,String fingerprint) {
        return new Plan(p.instrumentId(),p.exchange(),p.symbol(),p.segment(),p.lotSize(),p.tickSize(),p.observedPrice(),
                p.conservativeUnitPrice(),p.quantity(),p.conservativeNotional(),p.humanCeiling(),p.armDuration(),p.evidenceTimestamp(),
                p.registryVersion(),p.riskPolicyVersion(),fingerprint==null?p.evidenceVersion():fingerprint,
                reason==Reason.NONE || p.status()==Status.NOT_ELIGIBLE?p.status():Status.NOT_READY,reason,OrderProduct.INTRADAY,margin);
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Changed observations require a new review; never silently resize an existing plan. */
    public boolean stillCurrent(Plan reviewed, Plan recomputed) {
        return reviewed != null && reviewed.status() == Status.SIZED_RECHECK_REQUIRED && reviewed.equals(recomputed);
    }
    private static Plan denied(BigDecimal ceiling, Instant now, Reason reason) {
        BigDecimal safeCeiling = ceiling != null && ceiling.signum() >= 0 && ceiling.precision() <= 36
                && Math.abs((long) ceiling.scale()) <= 18 ? ceiling : null;
        return new Plan(null, null, null, null, 0, null, null, null, 0, BigDecimal.ZERO,
                safeCeiling, Duration.ZERO, now, 0, null, null, Status.NOT_READY, reason,OrderProduct.DELIVERY,Optional.empty());
    }
    private static String fingerprint(OrderExecutionProperties execution, LiveTestProperties live, Evidence e, Duration requested) {
        // Hash only explicitly bounded, non-secret observations; never Instrument.toString() or account payloads.
        var text = execution.auditVersion() + "|" + live.enabled() + "|" + live.maxQuantity() + "|" + live.maxNotional()
                + "|" + live.armMaxDuration() + "|" + live.allowedInstruments().stream().map(Object::toString).sorted().toList()
                + "|" + e.headroom() + "|" + requested
                + "|" + e.reference().refreshedAt() + "|" + e.tick().exchangeTimestamp()
                + "|" + e.desired().stream().map(Object::toString).sorted().toList()
                + "|" + e.active().stream().map(Object::toString).sorted().toList();
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void amount(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision() > 36 || Math.abs((long) value.scale()) > 18)
            throw new IllegalArgumentException("Invalid candidate amount");
    }
    private static void positiveDuration(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid candidate duration");
    }
    private static boolean fresh(Instant observed, Instant now, Duration age) {
        return !observed.isAfter(now) && !age.isZero() && !age.isNegative()
                && Duration.between(observed, now).compareTo(age) < 0;
    }
}
