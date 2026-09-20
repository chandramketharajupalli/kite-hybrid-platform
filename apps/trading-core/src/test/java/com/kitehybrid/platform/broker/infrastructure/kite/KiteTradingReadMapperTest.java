package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.BrokerPositions;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteTradingReadFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class KiteTradingReadMapperTest {
    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instrument INFY = Instrument.create(new BrokerInstrumentId("KITE", "256265"),
            "INFY", "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(),
            new BigDecimal("0.05"), 1);
    private final InMemoryInstrumentRegistry registry = registry();
    private final KiteTradingReadMapper mapper = new KiteTradingReadMapper(registry);

    @Test void mapsOrderSnapshotWithoutChangingPlatformLifecycleAndPreservesDecimalsAndUtc() {
        var order = mapper.orders(envelope("[" + ORDER + "]")).getFirst();
        assertEquals(INFY.id(), order.instrumentId());
        assertEquals("order-1", order.brokerOrderId());
        assertEquals(Optional.of("exchange-1"), order.exchangeOrderId());
        assertEquals(Optional.empty(), order.parentOrderId());
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.status());
        assertEquals(Side.BUY, order.side());
        assertEquals(OrderType.LIMIT, order.orderType());
        assertEquals(Product.DELIVERY, order.product());
        assertEquals(Validity.DAY, order.validity());
        assertEquals(Variety.REGULAR, order.variety());
        assertEquals(new BigDecimal("100.1250"), order.price());
        assertEquals(Instant.parse("2026-09-18T03:45:30Z"), order.orderedAt());
        assertEquals(Optional.of(Instant.parse("2026-09-18T03:45:31Z")), order.exchangeTimestamp());
        assertTrue(order.exchangeUpdatedAt().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"PUT ORDER REQ RECEIVED,RECEIVED", "AMO REQ RECEIVED,RECEIVED",
            "VALIDATION PENDING,VALIDATION_PENDING", "OPEN PENDING,OPEN_PENDING", "OPEN,OPEN",
            "COMPLETE,FILLED", "MODIFY VALIDATION PENDING,MODIFY_VALIDATION_PENDING",
            "MODIFY PENDING,MODIFY_PENDING", "TRIGGER PENDING,TRIGGER_PENDING",
            "CANCEL PENDING,CANCEL_PENDING", "CANCELLED,CANCELLED", "REJECTED,REJECTED", "NEW STATUS,UNKNOWN"})
    void mapsKnownAndUnknownStatuses(String wire, OrderStatus expected) throws Exception {
        var node = row(ORDER);
        node.put("status", wire).put("filled_quantity", expected == OrderStatus.FILLED ? 10 : 0);
        if (expected == OrderStatus.FILLED) node.put("pending_quantity", 0);
        assertEquals(expected, mapper.orders(envelope("[" + node + "]")).getFirst().status());
    }

    @Test void unknownWellFormedTypesRemainTypedUnknownWithoutRawWireText() throws Exception {
        var node = row(ORDER);
        for (String field : List.of("transaction_type", "order_type", "product", "validity", "variety", "status"))
            node.put(field, "FUTURE_WIRE_TYPE");
        var order = mapper.orders(envelope("[" + node + "]")).getFirst();
        assertEquals(Side.UNKNOWN, order.side()); assertEquals(OrderType.UNKNOWN, order.orderType());
        assertEquals(Product.UNKNOWN, order.product()); assertEquals(Validity.UNKNOWN, order.validity());
        assertEquals(Variety.UNKNOWN, order.variety()); assertEquals(OrderStatus.UNKNOWN, order.status());
        assertFalse(order.toString().contains("FUTURE_WIRE_TYPE"));
    }

    @ParameterizedTest
    @CsvSource({"MARKET,MARKET", "LIMIT,LIMIT", "SL,STOP_LIMIT", "SL-M,STOP_MARKET"})
    void mapsOrderTypes(String wire, OrderType expected) throws Exception {
        assertEquals(expected, mapper.orders(one(changed(ORDER, "order_type", "\"" + wire + "\"")))
                .getFirst().orderType());
    }

    @ParameterizedTest
    @CsvSource({"CNC,DELIVERY", "MIS,INTRADAY", "NRML,CARRY_FORWARD", "CO,COVER",
            "BO,BRACKET", "MTF,MARGIN_FUNDING"})
    void mapsProducts(String wire, Product expected) throws Exception {
        assertEquals(expected, mapper.orders(one(changed(ORDER, "product", "\"" + wire + "\"")))
                .getFirst().product());
    }

    @Test void nullableOrderFieldsRemainAbsentAndMissingRequiredTimeFails() throws Exception {
        var node = row(ORDER);
        node.putNull("exchange_order_id").remove("parent_order_id");
        node.putNull("exchange_timestamp").remove("exchange_update_timestamp");
        var order = mapper.orders(envelope("[" + node + "]")).getFirst();
        assertTrue(order.exchangeOrderId().isEmpty()); assertTrue(order.parentOrderId().isEmpty());
        assertTrue(order.exchangeTimestamp().isEmpty()); assertTrue(order.exchangeUpdatedAt().isEmpty());
        node.remove("order_timestamp");
        invalid(() -> mapper.orders(envelope("[" + node + "]")));
    }

    @Test void mapsTradesWithoutFabricatingDateForTimeOnlyOrderTimestamp() {
        var trade = mapper.trades(one(TRADE)).getFirst();
        assertEquals(INFY.id(), trade.instrumentId());
        assertEquals("trade-1", trade.brokerTradeId()); assertEquals("order-1", trade.brokerOrderId());
        assertEquals(2, trade.quantity()); assertEquals(new BigDecimal("100.1250"), trade.price());
        assertEquals(Instant.parse("2026-09-18T03:45:31Z"), trade.filledAt());
    }

    @Test void signedPositionsAndBrokerReportedValuesRemainSeparateAcrossNetAndDay() {
        var result = mapper.positions(envelope("{\"net\":[" + POSITION + "],\"day\":[]}"));
        var position = result.net().getFirst();
        assertEquals(-3, position.quantity()); assertEquals(-1, position.overnightQuantity());
        assertEquals(new BigDecimal("-2.25"), position.pnl());
        assertEquals(2, position.buy().quantity()); assertEquals(5, position.sell().quantity());
        assertEquals(1, position.dayBuy().quantity()); assertEquals(3, position.daySell().quantity());
        assertTrue(result.day().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.net().clear());
    }

    @Test void holdingsPreserveDistinctQuantityBucketsAndFloatingPointArtifactsExactly() {
        var holding = mapper.holdings(one(HOLDING)).getFirst();
        assertEquals(INFY.id(), holding.instrumentId()); assertEquals(10, holding.quantity());
        assertEquals(2, holding.unsettledQuantity()); assertEquals(3, holding.collateralQuantity());
        assertEquals(new BigDecimal("-0.09999999999999432"), holding.dayChange());
        assertTrue(holding.marginFunded().isEmpty());
    }

    @Test void mapsOptionalMarginFundedHoldingWithoutInventingAbsentAmounts() throws Exception {
        var holding = changed(HOLDING, "mtf", """
                {"quantity":2,"used_quantity":1,"average_price":100,"value":200,"initial_margin":20}
                """);
        assertEquals(2, mapper.holdings(one(holding)).getFirst().marginFunded().orElseThrow().quantity());
        invalid(() -> mapper.holdings(one(changed(HOLDING, "mtf", "{}"))));
    }

    @Test void marginsPreserveBothSegmentsEnabledFlagsAndExactReportedBreakdown() {
        var result = mapper.margins(MARGINS);
        assertEquals(2, result.segments().size());
        var equity = result.segments().get(MarginSegment.EQUITY);
        assertEquals(new BigDecimal("99725.05000000002"), equity.net());
        assertEquals(new BigDecimal("245431.6"), equity.available().cash());
        assertEquals(new BigDecimal("-5"), equity.utilised().unrealisedMarkToMarket());
        assertTrue(equity.enabled()); assertFalse(result.segments().get(MarginSegment.COMMODITY).enabled());
        assertThrows(UnsupportedOperationException.class, () -> result.segments().clear());
    }

    @Test void emptyCollectionsAreValidButIncompleteMarginOrPositionObjectsAreNot() {
        assertTrue(mapper.orders(envelope("[]")).isEmpty()); assertTrue(mapper.trades(envelope("[]")).isEmpty());
        assertTrue(mapper.holdings(envelope("[]")).isEmpty());
        assertEquals(new BrokerPositions(List.of(), List.of()), mapper.positions(envelope("{\"net\":[],\"day\":[]}")));
        invalid(() -> mapper.positions(envelope("{}")));
        invalid(() -> mapper.margins(envelope("{}")));
        invalid(() -> mapper.margins(envelope("{\"equity\":" + SEGMENT + "}")));
        invalid(() -> mapper.margins(MARGINS.replace("\"commodity\"", "\"future_segment\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"order_id", "instrument_token", "exchange", "tradingsymbol", "transaction_type",
            "order_type", "product", "validity", "variety", "status", "quantity", "filled_quantity",
            "pending_quantity", "cancelled_quantity", "disclosed_quantity", "price", "trigger_price", "average_price",
            "order_timestamp"})
    void rejectsEveryMissingRequiredOrderField(String field) throws Exception {
        var node = row(ORDER); node.remove(field);
        invalid(() -> mapper.orders(envelope("[" + node + "]")));
    }

    @ParameterizedTest
    @MethodSource("badNumbers")
    void rejectsMalformedOutOfBoundsAndWrongTypeNumericFields(String field, String wire) throws Exception {
        invalid(() -> mapper.orders(one(changed(ORDER, field, wire))));
    }
    static Stream<Arguments> badNumbers() {
        return Stream.of(Arguments.of("quantity", "-1"), Arguments.of("quantity", "0"),
                Arguments.of("quantity", "1.5"), Arguments.of("quantity", "\"10\""),
                Arguments.of("quantity", "9223372036854775808"), Arguments.of("quantity", "null"),
                Arguments.of("filled_quantity", "11"), Arguments.of("pending_quantity", "11"),
                Arguments.of("cancelled_quantity", "-1"), Arguments.of("disclosed_quantity", "11"),
                Arguments.of("price", "-0.01"), Arguments.of("price", "\"100.25\""),
                Arguments.of("price", "1e18"), Arguments.of("price", "0.0000000000000000001"),
                Arguments.of("price", "1e-100"), Arguments.of("price", "true"),
                Arguments.of("trigger_price", "-1"), Arguments.of("average_price", "-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "1", "\"\"", "\"bad\\nstatus\""})
    void rejectsMalformedEnumsInsteadOfTreatingThemAsUnknown(String value) throws Exception {
        invalid(() -> mapper.orders(one(changed(ORDER, "status", value))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{", "{\"status\":\"success\",\"data\":[]}{\"extra\":1}",
            "{\"status\":\"success\",\"status\":\"success\",\"data\":[]}",
            "{\"status\":true,\"data\":[]}", "{\"status\":\"success\",\"data\":[null]}",
            "{\"status\":\"success\",\"data\":{}}"})
    void malformedEnvelopesAndJsonFailSafely(String body) { invalid(() -> mapper.orders(body)); }

    @Test void responseBoundsCoverBytesRowsDepthStringsNumbersAndPropertyNames() {
        invalid(() -> mapper.orders(" ".repeat(KiteTradingReadMapper.MAX_RESPONSE_CHARS + 1)));
        invalid(() -> mapper.orders(envelope("[" + "{},".repeat(KiteTradingReadMapper.MAX_ROWS) + "{}]")));
        invalid(() -> mapper.orders(envelope("[".repeat(20) + "0" + "]".repeat(20))));
        invalid(() -> mapper.orders(envelope("[\"" + "x".repeat(1025) + "\"]")));
        invalid(() -> mapper.orders(envelope("[" + "1".repeat(65) + "]")));
        invalid(() -> mapper.orders(envelope("[{\"" + "x".repeat(129) + "\":0}]")));
    }

    @Test void brokerErrorEnvelopeIsCategorizedAndExceptionDoesNotRetainSensitivePayloadOrCause() {
        String marker = "DO_NOT_EXPOSE_UPSTREAM_SECRET";
        var failure = assertThrows(BrokerReadException.class,
                () -> mapper.orders("{\"status\":\"error\",\"message\":\"" + marker + "\"}"));
        assertEquals(BROKER_API, failure.category()); assertNull(failure.getCause());
        assertFalse(failure.toString().contains(marker));
        var malformed = assertThrows(BrokerReadException.class, () -> mapper.orders("{\"" + marker));
        assertEquals(INVALID_RESPONSE, malformed.category()); assertNull(malformed.getCause());
        assertFalse(malformed.toString().contains(marker));
    }

    @Test void tokenStringResolvesThroughExistingRegistryAndNoInstrumentIdIsInvented() throws Exception {
        assertEquals(INFY.id(), mapper.orders(one(changed(ORDER, "instrument_token", "\"256265\"")))
                .getFirst().instrumentId());
        invalid(() -> mapper.orders(one(changed(ORDER, "instrument_token", "256266"))));
        invalid(() -> mapper.orders(one(changed(ORDER, "tradingsymbol", "\"TCS\""))));
        invalid(() -> mapper.orders(one(changed(ORDER, "exchange", "\"BSE\""))));
        invalid(() -> new KiteTradingReadMapper(new InMemoryInstrumentRegistry()).orders(one(ORDER)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "4294967296", "256265.0", "\"-1\"", "\"NaN\"", "null"})
    void malformedTokensCannotBecomePlatformInstrumentIds(String value) throws Exception {
        invalid(() -> mapper.orders(one(changed(ORDER, "instrument_token", value))));
    }

    @Test void oneRegistrySnapshotPinsEveryRowAndBothPositionCollections() {
        var reads = new AtomicInteger();
        InstrumentSnapshot stable = registry.snapshot();
        InstrumentRegistry counted = new InstrumentRegistry() {
            public InstrumentSnapshot snapshot() { reads.incrementAndGet(); return stable; }
            public InstrumentSnapshot replace(List<Instrument> values, Instant now) { throw new AssertionError(); }
        };
        var local = new KiteTradingReadMapper(counted);
        local.positions(envelope("{\"net\":[" + POSITION + "],\"day\":[" + POSITION + "]}"));
        assertEquals(1, reads.get());
    }

    @Test void malformedLaterRowRejectsWholeResponseAndReturnedListsAreImmutable() throws Exception {
        invalid(() -> mapper.orders(envelope("[" + ORDER + "," + changed(ORDER, "quantity", "-1") + "]")));
        var orders = mapper.orders(one(ORDER));
        assertThrows(UnsupportedOperationException.class, orders::clear);
        var copied = new ArrayList<>(mapper.positions(envelope("{\"net\":[" + POSITION + "],\"day\":[]}")).net());
        var positions = new BrokerPositions(copied, List.of()); copied.clear();
        assertEquals(1, positions.net().size());
    }

    @Test void impossibleDatesAndIncompleteFilledOrdersFailRatherThanGuessing() throws Exception {
        invalid(() -> mapper.orders(one(changed(ORDER, "order_timestamp", "\"2026-02-30 09:15:00\""))));
        invalid(() -> mapper.orders(one(changed(ORDER, "order_timestamp", "\"09:15:30\""))));
        invalid(() -> mapper.orders(one(changed(ORDER, "status", "\"COMPLETE\""))));
        invalid(() -> mapper.trades(one(changed(TRADE, "fill_timestamp", "null"))));
    }

    @Test void overlappingPendingAndCancelledCountersArePreservedAsDocumented() throws Exception {
        var node = row(ORDER);
        node.put("status", "CANCELLED").put("quantity", 1).put("filled_quantity", 0)
                .put("pending_quantity", 1).put("cancelled_quantity", 1);
        var order = mapper.orders(one(node.toString())).getFirst();
        assertEquals(1, order.pendingQuantity()); assertEquals(1, order.cancelledQuantity());
    }

    @Test void duplicateRowsCannotSilentlyDoubleCountObservations() {
        invalid(() -> mapper.orders(envelope("[" + ORDER + "," + ORDER + "]")));
        invalid(() -> mapper.trades(envelope("[" + TRADE + "," + TRADE + "]")));
        invalid(() -> mapper.holdings(envelope("[" + HOLDING + "," + HOLDING + "]")));
        invalid(() -> mapper.positions(envelope("{\"net\":[" + POSITION + "," + POSITION + "],\"day\":[]}")));
        // The same instrument is expected in each separate net/day collection.
        assertEquals(1, mapper.positions(envelope("{\"net\":[" + POSITION + "],\"day\":[" + POSITION + "]}"))
                .day().size());
    }

    @Test void fullyFilledOpenOrderIsAnInconsistentObservation() throws Exception {
        var node = row(ORDER); node.put("filled_quantity", 10).put("pending_quantity", 0);
        invalid(() -> mapper.orders(one(node.toString())));
    }

    @Test void allNonOrderResponsesRejectMissingFieldsAndBadNumerics() throws Exception {
        invalid(() -> mapper.trades(one(changed(TRADE, "trade_id", "null"))));
        invalid(() -> mapper.trades(one(changed(TRADE, "quantity", "0"))));
        invalid(() -> mapper.positions(envelope("{\"net\":[" + changed(POSITION, "multiplier", "0") + "],\"day\":[]}")));
        invalid(() -> mapper.positions(envelope("{\"net\":[" + changed(POSITION, "buy_quantity", "-1") + "],\"day\":[]}")));
        invalid(() -> mapper.holdings(one(changed(HOLDING, "last_price", "null"))));
        invalid(() -> mapper.holdings(one(changed(HOLDING, "quantity", "-1"))));
        invalid(() -> mapper.margins(MARGINS.replace("\"cash\":245431.6", "\"cash\":null")));
        invalid(() -> mapper.margins(MARGINS.replace("\"enabled\":true", "\"enabled\":\"true\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"1\"", "9223372036854775808", "null", "-1"})
    void positionMultiplierMustBeAnExplicitPositiveInt64(String value) throws Exception {
        invalid(() -> mapper.positions(envelope("{\"net\":[" + changed(POSITION, "multiplier", value)
                + "],\"day\":[]}")));
    }

    private static ObjectNode row(String value) throws Exception { return (ObjectNode) JSON.readTree(value); }
    private static String changed(String value, String field, String replacement) throws Exception {
        ObjectNode node = row(value); node.set(field, JSON.readTree(replacement)); return node.toString();
    }
    private static String one(String row) { return envelope("[" + row + "]"); }
    private static void invalid(org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(BrokerReadException.class, action);
        assertEquals(INVALID_RESPONSE, failure.category()); assertNull(failure.getCause());
    }
    private static InMemoryInstrumentRegistry registry() {
        var value = new InMemoryInstrumentRegistry(); value.replace(List.of(INFY), NOW); return value;
    }
}
