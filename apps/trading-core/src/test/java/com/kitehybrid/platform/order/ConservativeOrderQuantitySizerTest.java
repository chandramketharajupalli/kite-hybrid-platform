package com.kitehybrid.platform.order;

import com.kitehybrid.platform.order.domain.ConservativeOrderQuantitySizer;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ConservativeOrderQuantitySizerTest {
    @ParameterizedTest @CsvSource({
        "9999.99,1,10000,1,1", "10000,1,10000,1,1", "10000.01,1,10000,1,0",
        "800,1.10,10000,1,11", "800.01,1.125,10000,1,11", "100,1,10000,3,99",
        "4000,1,10000,3,0", "0.01,1.01,10000,1,990099", "100.000,1.00,10000.0,1,100",
        "999999999999999999,2,10000,1,0", "1,1,0,1,0" })
    void floorsAndExactlyRecomputes(String price, String buffer, String cap, int lot, long expected) {
        var result = ConservativeOrderQuantitySizer.size(new BigDecimal(price), new BigDecimal(buffer),
                new BigDecimal(cap), lot, List.of(Long.MAX_VALUE));
        assertEquals(expected, result.quantity());
        assertEquals(0, result.conservativeUnitPrice().multiply(BigDecimal.valueOf(expected)).compareTo(result.conservativeNotional()));
        assertTrue(result.conservativeNotional().compareTo(new BigDecimal(cap)) <= 0);
        assertEquals(0, result.quantity() % lot);
        var nextLotValue = result.conservativeUnitPrice().multiply(BigDecimal.valueOf(result.quantity()).add(BigDecimal.valueOf(lot)));
        assertTrue(nextLotValue.compareTo(new BigDecimal(cap)) > 0, "A further lot must exceed this budget");
    }
    @Test void capsAreAppliedBeforeLotRoundingAndConversion() {
        assertEquals(6, ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("10000"), 3, List.of(100L, 8L, 9L)).quantity());
        assertEquals(Long.MAX_VALUE, ConservativeOrderQuantitySizer.size(new BigDecimal("0.000000000000000001"),
                BigDecimal.ONE, new BigDecimal("1E+18"), 1, List.of(Long.MAX_VALUE)).quantity());
        assertEquals(Long.MAX_VALUE - 1, ConservativeOrderQuantitySizer.size(BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("999999999999999999999"), 2, List.of(Long.MAX_VALUE)).quantity());
        assertEquals(0, ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, 1, List.of(0L, 10L)).quantity());
        var wideLot = ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("999999999999999999999"), Integer.MAX_VALUE, List.of(Long.MAX_VALUE));
        assertEquals(0, wideLot.quantity() % Integer.MAX_VALUE);
        assertTrue(BigDecimal.valueOf(wideLot.quantity()).add(BigDecimal.valueOf(Integer.MAX_VALUE))
                .compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0);
    }
    @Test void invalidInputsFailClosed() {
        for (var bad : List.of(BigDecimal.ZERO, BigDecimal.ONE.negate(), new BigDecimal("1E-19"), new BigDecimal("1E+19")))
            assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(bad, BigDecimal.ONE, BigDecimal.TEN, 1, List.of(1L)));
        for (var bad : List.of(new BigDecimal("0.99"), new BigDecimal("2.01")))
            assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, bad, BigDecimal.TEN, 1, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, 0, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, 1, List.of(-1L)));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE, null, 1, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> ConservativeOrderQuantitySizer.size(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1E+19"), 1, List.of(1L)));
    }
}
