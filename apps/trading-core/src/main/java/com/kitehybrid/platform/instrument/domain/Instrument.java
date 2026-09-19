package com.kitehybrid.platform.instrument.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Reference data only: existence in the registry does not authorize trading. */
public record Instrument(InstrumentId id, BrokerInstrumentId brokerId, String tradingSymbol,
                         String exchange, String segment, InstrumentType type,
                         Optional<LocalDate> expiry, Optional<BigDecimal> strike,
                         BigDecimal tickSize, int lotSize) {
    public Instrument {
        Objects.requireNonNull(id, "Platform instrument ID required");
        Objects.requireNonNull(brokerId, "Broker instrument mapping required");
        Objects.requireNonNull(type, "Instrument type required");
        Objects.requireNonNull(expiry, "Expiry optional required");
        Objects.requireNonNull(strike, "Strike optional required");
        Objects.requireNonNull(tickSize, "Tick size required");
        tradingSymbol = InstrumentText.canonical(tradingSymbol, 128);
        exchange = InstrumentText.canonical(exchange, 32);
        segment = InstrumentText.canonical(segment, 32);
        validateSemantics(segment, type, expiry, strike, tickSize, lotSize);
        strike = strike.map(BigDecimal::stripTrailingZeros);
        tickSize = tickSize.stripTrailingZeros();
        if (!id.equals(InstrumentIdentity.derive(exchange, tradingSymbol, segment, type, expiry, strike)))
            throw new IllegalArgumentException("Platform instrument ID does not match canonical instrument identity");
    }

    public static Instrument create(BrokerInstrumentId brokerId, String tradingSymbol, String exchange,
                                    String segment, InstrumentType type, Optional<LocalDate> expiry,
                                    Optional<BigDecimal> strike, BigDecimal tickSize, int lotSize) {
        Objects.requireNonNull(type, "Instrument type required");
        Objects.requireNonNull(expiry, "Expiry optional required");
        Objects.requireNonNull(strike, "Strike optional required");
        String symbol = InstrumentText.canonical(tradingSymbol, 128);
        String venue = InstrumentText.canonical(exchange, 32);
        String canonicalSegment = InstrumentText.canonical(segment, 32);
        InstrumentId id = InstrumentIdentity.derive(venue, symbol, canonicalSegment, type, expiry, strike);
        return new Instrument(id, brokerId, symbol, venue, canonicalSegment, type, expiry, strike,
                tickSize, lotSize);
    }

    private static void validateSemantics(String segment, InstrumentType type, Optional<LocalDate> expiry,
                                          Optional<BigDecimal> strike, BigDecimal tickSize, int lotSize) {
        String expectedSegment = switch (type) {
            case CASH -> "CASH";
            case INDEX -> "INDICES";
            case FUTURE -> "FUTURES";
            case CALL_OPTION, PUT_OPTION -> "OPTIONS";
        };
        if (!segment.equals(expectedSegment))
            throw new IllegalArgumentException("Instrument segment does not match type");
        boolean derivative = type == InstrumentType.FUTURE
                || type == InstrumentType.CALL_OPTION || type == InstrumentType.PUT_OPTION;
        boolean option = type == InstrumentType.CALL_OPTION || type == InstrumentType.PUT_OPTION;
        if (expiry.isPresent() != derivative)
            throw new IllegalArgumentException("Instrument expiry does not match type");
        if (strike.isPresent() != option || strike.map(value -> value.signum() < 0).orElse(false))
            throw new IllegalArgumentException("Instrument strike does not match type");
        boolean index = type == InstrumentType.INDEX;
        if (lotSize < 0 || (!index && lotSize == 0))
            throw new IllegalArgumentException("Invalid instrument lot size");
        if (tickSize.signum() < 0 || (!index && tickSize.signum() == 0))
            throw new IllegalArgumentException("Invalid instrument tick size");
    }
}
