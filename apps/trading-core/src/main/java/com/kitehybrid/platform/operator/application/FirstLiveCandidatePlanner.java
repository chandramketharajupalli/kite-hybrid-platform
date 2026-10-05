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
        LIMIT_UNCONFIGURED, ACCOUNT_NOT_CURRENT, NO_VALID_LOT }

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
                       long registryVersion, String riskPolicyVersion, String evidenceVersion, Status status, Reason reason) {
        public OrderSide side() { return OrderSide.BUY; }
        public OrderType orderType() { return OrderType.MARKET; }
        public OrderProduct product() { return OrderProduct.DELIVERY; }
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
                    .min(risk.maxOrderValue()).min(risk.maxExposure()).min(headroom.exposure()).min(headroom.cashAfterReserve());
            var size = ConservativeOrderQuantitySizer.size(tick.lastPrice(), risk.priceBuffer(), ceiling,
                    instrument.lotSize(), List.of(execution.maxQuantity(), live.maxQuantity(), risk.maxOrderQuantity(),
                            risk.maxPositionQuantity(), headroom.positionUnits()));
            // Repeat every independent money comparison on the exact recomputed result.
            for (var cap : List.of(humanCeiling, execution.maxNotional(), live.maxNotional(), risk.maxOrderValue(),
                    risk.maxExposure(), headroom.exposure(), headroom.cashAfterReserve()))
                if (size.conservativeNotional().compareTo(cap) > 0) throw new IllegalArgumentException();
            Duration duration = requestedArm.compareTo(live.armMaxDuration()) <= 0 ? requestedArm : live.armMaxDuration();
            return new Plan(instrument.id(), instrument.exchange(), instrument.tradingSymbol(), instrument.segment(),
                    instrument.lotSize(), instrument.tickSize(), tick.lastPrice(), size.conservativeUnitPrice(),
                    size.quantity(), size.conservativeNotional(), humanCeiling, duration, tick.receivedAt(),
                    evidence.reference().version(), risk.version(), fingerprint(execution, live, evidence, requestedArm),
                    size.quantity() == 0 ? Status.NOT_ELIGIBLE : Status.SIZED_RECHECK_REQUIRED,
                    size.quantity() == 0 ? Reason.NO_VALID_LOT : Reason.NONE);
        } catch (RuntimeException unavailable) { return denied(humanCeiling, now, Reason.EVIDENCE_UNAVAILABLE); }
    }

    /** Changed observations require a new review; never silently resize an existing plan. */
    public boolean stillCurrent(Plan reviewed, Plan recomputed) {
        return reviewed != null && reviewed.status() == Status.SIZED_RECHECK_REQUIRED && reviewed.equals(recomputed);
    }
    private static Plan denied(BigDecimal ceiling, Instant now, Reason reason) {
        BigDecimal safeCeiling = ceiling != null && ceiling.signum() >= 0 && ceiling.precision() <= 36
                && Math.abs((long) ceiling.scale()) <= 18 ? ceiling : null;
        return new Plan(null, null, null, null, 0, null, null, null, 0, BigDecimal.ZERO,
                safeCeiling, Duration.ZERO, now, 0, null, null, Status.NOT_READY, reason);
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
