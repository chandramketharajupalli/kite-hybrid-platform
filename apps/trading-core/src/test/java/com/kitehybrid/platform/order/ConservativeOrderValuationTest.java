package com.kitehybrid.platform.order;

import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.command.OrderType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ConservativeOrderValuationTest {
    @ParameterizedTest
    @CsvSource({
        "9500,1.10,1,10450,false", "9999.99,1,1,9999.99,true",
        "10000,1.0,1,10000,true", "10000.01,1.0,1,10000.01,false",
        "7999.992,1.25,1,9999.99,true", "8000,1.25,1,10000,true",
        "8000.008,1.25,1,10000.01,false", "800,1.25,11,11000,false",
        "0.1,1.1,3,0.33,true", "0.000000000000000001,2,1,0.000000000000000002,true",
        "1,2,9223372036854775807,18446744073709551614,false"
    })
    void marketIsExactAndCapIsInclusive(String price, String buffer, long quantity, String expected, boolean allowed) {
        var value = market(price, buffer, quantity);
        assertEquals(0, new BigDecimal(expected).compareTo(value.conservativeNotional()));
        assertEquals(allowed, value.within(new BigDecimal("10000")));
        assertEquals(allowed, value.within(new BigDecimal("10000.00")));
        assertFalse(value.within(BigDecimal.ZERO));
        assertFalse(value.within(null));
    }
    @ParameterizedTest @CsvSource({"100,105,105,115.50", "110,105,110,121.00"})
    void limitUsesHigherReference(String market, String limit, String reference, String notional) {
        var value = ConservativeOrderValuation.evaluate(OrderType.LIMIT, 1, new BigDecimal(market),
                Optional.of(new BigDecimal(limit)), new BigDecimal("1.10"));
        assertEquals(0, new BigDecimal(reference).compareTo(value.referencePrice()));
        assertEquals(0, new BigDecimal(notional).compareTo(value.conservativeUnitPrice()));
        assertEquals(value.conservativeUnitPrice(), value.conservativeNotional());
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "1E+19", "1E-19", "1234567890123456789012345678901234567"})
    void invalidPriceFailsClosed(String price) {
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.MARKET,
                1, price == null ? null : new BigDecimal(price), Optional.empty(), BigDecimal.ONE));
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0.99", "2.0001", "-1", "1.0000000000000000001", "1E100"})
    void invalidBufferFailsClosed(String buffer) {
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.MARKET,
                1, BigDecimal.ONE, Optional.empty(), buffer == null ? null : new BigDecimal(buffer)));
    }
    @ParameterizedTest @ValueSource(longs={0, -1, Long.MIN_VALUE})
    void invalidQuantityFailsClosed(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> market("10", "1.1", quantity));
    }
    @Test void shapeCannotSubstituteArbitraryPricesOrUnsupportedOrderTypes() {
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.MARKET,
                1, BigDecimal.TEN, Optional.of(BigDecimal.ONE), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.LIMIT,
                1, BigDecimal.TEN, Optional.empty(), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.LIMIT,
                1, BigDecimal.TEN, Optional.of(BigDecimal.ZERO), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderValuation.evaluate(OrderType.STOP_MARKET,
                1, BigDecimal.TEN, Optional.empty(), BigDecimal.ONE));
    }
    @Test void maximumInputPrecisionAndScaleDoNotRoundMultiplication() {
        var price = new BigDecimal("999999999999999999.999999999999999999");
        var buffer = new BigDecimal("1.999999999999999999");
        var value = ConservativeOrderValuation.evaluate(OrderType.MARKET, Long.MAX_VALUE, price, Optional.empty(), buffer);
        assertEquals(price.multiply(buffer).multiply(BigDecimal.valueOf(Long.MAX_VALUE)), value.conservativeNotional());
        assertEquals(36, value.conservativeNotional().scale());
        assertTrue(value.within(value.conservativeNotional()));
        assertFalse(value.within(value.conservativeNotional().subtract(new BigDecimal("1E-36"))));
    }
    private static ConservativeOrderValuation market(String price, String buffer, long quantity) {
        return ConservativeOrderValuation.evaluate(OrderType.MARKET, quantity, new BigDecimal(price),
                Optional.empty(), new BigDecimal(buffer));
    }
}
