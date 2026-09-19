package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {
    private static final BrokerInstrumentId BROKER_ID = new BrokerInstrumentId("KITE", "123");
    private static final LocalDate EXPIRY = LocalDate.of(2026, 9, 29);

    @Test void logicalIdentityIsCanonicalAndIndependentOfBrokerMappingAndTradingIncrements() {
        Instrument original = InstrumentFixtures.cash("123", "INFY");
        Instrument otherBroker = Instrument.create(new BrokerInstrumentId(" other ", "456"), " infy ",
                " nse ", " cash ", InstrumentType.CASH, Optional.empty(), Optional.empty(),
                new BigDecimal("0.0100"), 10);
        assertEquals(original.id(), otherBroker.id());
        assertNotEquals(original.brokerId(), otherBroker.brokerId());
        assertEquals("INFY", otherBroker.tradingSymbol());
        assertEquals("NSE", otherBroker.exchange());
        assertEquals("CASH", otherBroker.segment());
        assertEquals("OTHER", otherBroker.brokerId().broker());
    }

    @Test void tokenReuseForDifferentInstrumentDoesNotReusePlatformIdentity() {
        assertNotEquals(InstrumentFixtures.cash("123", "INFY").id(),
                InstrumentFixtures.cash("123", "TCS").id());
    }

    @Test void versionOneCanonicalIdentityHasAStableGoldenValue() {
        assertEquals(UUID.fromString("33fdd3eb-12cf-30b3-980c-086e16467a28"),
                InstrumentFixtures.cash("123", "INFY").id().value());
    }

    @Test void derivativeIdentityIncludesExpiryTypeAndCanonicalStrike() {
        Instrument call = option("NIFTY", InstrumentType.CALL_OPTION, EXPIRY, new BigDecimal("25000.00"));
        Instrument sameCall = option(" nifty ", InstrumentType.CALL_OPTION, EXPIRY, new BigDecimal("2.5E+4"));
        assertEquals(call.id(), sameCall.id());
        assertEquals(call.strike(), sameCall.strike());
        assertNotEquals(call.id(), option("NIFTY", InstrumentType.CALL_OPTION,
                EXPIRY.plusMonths(1), new BigDecimal("25000")).id());
        assertNotEquals(call.id(), option("NIFTY", InstrumentType.PUT_OPTION,
                EXPIRY, new BigDecimal("25000")).id());
        assertNotEquals(call.id(), option("NIFTY", InstrumentType.CALL_OPTION,
                EXPIRY, new BigDecimal("25001")).id());
    }

    @Test void identityTupleCannotCollideThroughFieldDelimiterConcatenation() {
        Instrument first = Instrument.create(BROKER_ID, "C", "A:B", "CASH", InstrumentType.CASH,
                Optional.empty(), Optional.empty(), BigDecimal.ONE, 1);
        Instrument second = Instrument.create(BROKER_ID, "B:C", "A", "CASH", InstrumentType.CASH,
                Optional.empty(), Optional.empty(), BigDecimal.ONE, 1);
        assertNotEquals(first.id(), second.id());
    }

    @Test void arbitraryPlatformIdentityIsRejected() {
        Instrument original = InstrumentFixtures.cash("123", "INFY");
        assertThrows(IllegalArgumentException.class, () -> new Instrument(new InstrumentId(new UUID(0, 0)),
                original.brokerId(), original.tradingSymbol(), original.exchange(), original.segment(),
                original.type(), original.expiry(), original.strike(), original.tickSize(), original.lotSize()));
    }

    @Test void indexAllowsZeroIncrementsAndPreservesInternalSpacesWithoutImplyingTradeEligibility() {
        Instrument index = Instrument.create(BROKER_ID, " Nifty 50 ", "NSE", "INDICES", InstrumentType.INDEX,
                Optional.empty(), Optional.empty(), BigDecimal.ZERO, 0);
        assertEquals("NIFTY 50", index.tradingSymbol());
        assertEquals(0, index.lotSize());
        assertEquals(BigDecimal.ZERO, index.tickSize());
    }

    @ParameterizedTest
    @CsvSource({"0.05,0", "0.05,-1", "0,1", "-0.05,1"})
    void cashRejectsInvalidIncrements(String tick, int lot) {
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal(tick), lot));
    }

    @Test void derivativeAndCashFieldsAreValidatedByType() {
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "FUT", "NFO", "FUTURES",
                InstrumentType.FUTURE, Optional.empty(), Optional.empty(), BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "FUT", "NFO", "FUTURES",
                InstrumentType.FUTURE, Optional.of(EXPIRY), Optional.of(BigDecimal.ONE), BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.of(EXPIRY), Optional.empty(), BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.of(BigDecimal.ONE), BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "OPT", "NFO", "OPTIONS",
                InstrumentType.CALL_OPTION, Optional.of(EXPIRY), Optional.empty(), BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> option("OPT", InstrumentType.CALL_OPTION,
                EXPIRY, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> Instrument.create(BROKER_ID, "INFY", "NSE", "OPTIONS",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), BigDecimal.ONE, 1));
        assertNotNull(option("OPT", InstrumentType.CALL_OPTION, EXPIRY, BigDecimal.ZERO));
        assertNotNull(Instrument.create(BROKER_ID, "FUT", "NFO", "FUTURES", InstrumentType.FUTURE,
                Optional.of(EXPIRY), Optional.empty(), BigDecimal.ONE, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "INFY\n", "\tINFY", "IN\u0000FY"})
    void missingOrControlCharacterTextIsRejected(String symbol) {
        var failure = assertThrows(IllegalArgumentException.class, () -> InstrumentFixtures.cash("123", symbol));
        assertFalse(failure.getMessage().contains("INFY"));
    }

    @Test void nullAndOverlongRequiredValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> InstrumentFixtures.cash("123", null));
        assertThrows(IllegalArgumentException.class, () -> InstrumentFixtures.cash("123", "X".repeat(129)));
        assertThrows(NullPointerException.class, () -> Instrument.create(null, "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), BigDecimal.ONE, 1));
        assertThrows(NullPointerException.class, () -> Instrument.create(BROKER_ID, "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), null, 1));
    }

    @Test void brokerIdentifierHasExplicitCanonicalizationAndBoundedSafeCharacters() {
        assertEquals(new BrokerInstrumentId("KITE", "abc:123"), new BrokerInstrumentId(" kite ", " abc:123 "));
        assertNotEquals(new BrokerInstrumentId("KITE", "abc"), new BrokerInstrumentId("KITE", "ABC"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId("\nKITE", "123"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId("KITE", "123\n"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId("KITE", "a b"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId("KITE", "x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId("KITE", ""));
        assertThrows(IllegalArgumentException.class, () -> new BrokerInstrumentId(null, "123"));
    }

    private static Instrument option(String symbol, InstrumentType type, LocalDate expiry, BigDecimal strike) {
        return Instrument.create(BROKER_ID, symbol, "NFO", "OPTIONS", type, Optional.of(expiry),
                Optional.of(strike), new BigDecimal("0.05"), 25);
    }
}
