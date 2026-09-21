package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.ExchangeSymbol;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.BrokerCorrelationId;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteBrokerIdentity.BROKER_ID;

/** Strict bounded wire normalization. No broker payload, free text or cause escapes on failure. */
public final class KiteTradingReadMapper {
    private static final Logger LOG = LoggerFactory.getLogger(KiteTradingReadMapper.class);
    public static final int MAX_RESPONSE_CHARS = 8 * 1024 * 1024;
    public static final int MAX_ROWS = 10_000;
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("1e18");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId KITE_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                            .maxStringLength(1024).maxNumberLength(64).build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true)).build();
    private final InstrumentRegistry instruments;

    public KiteTradingReadMapper(InstrumentRegistry instruments) {
        this.instruments = Objects.requireNonNull(instruments);
    }

    public List<BrokerOrder> orders(String body) {
        return map(body, data -> {
            var snapshot = instruments.snapshot();
            return distinct(rows(data, row -> order(row, snapshot)), BrokerOrder::brokerOrderId);
        });
    }
    public List<BrokerTrade> trades(String body) {
        return map(body, data -> {
            var snapshot = instruments.snapshot();
            return distinct(rows(data, row -> trade(row, snapshot)),
                    trade -> List.of(trade.brokerOrderId(), trade.brokerTradeId()));
        });
    }
    public BrokerPositions positions(String body) {
        return map(body, data -> {
            object(data);
            var snapshot = instruments.snapshot();
            return new BrokerPositions(distinct(rows(required(data, "net"), row -> position(row, snapshot)),
                    row -> List.of(row.instrumentId(), row.product())),
                    distinct(rows(required(data, "day"), row -> position(row, snapshot)),
                            row -> List.of(row.instrumentId(), row.product())));
        });
    }
    public List<BrokerHolding> holdings(String body) {
        try {
            return map(body, data -> {
                var snapshot = instruments.snapshot();
                return distinct(rows(data, row -> holding(row, snapshot)),
                        row -> List.of(row.instrumentId(), row.product()));
            });
        } catch (HoldingNormalizationFailure failure) {
            LOG.warn("Kite holdings normalization failed: reason={}, field={}",
                    failure.reason, failure.field);
            throw invalid();
        }
    }
    public BrokerMargins margins(String body) {
        return map(body, data -> {
            object(data);
            if (data.size() != 2 || !data.has("equity") || !data.has("commodity")) throw invalid();
            var segments = new EnumMap<MarginSegment, BrokerMargins.SegmentMargin>(MarginSegment.class);
            segments.put(MarginSegment.EQUITY, margin(required(data, "equity")));
            segments.put(MarginSegment.COMMODITY, margin(required(data, "commodity")));
            return new BrokerMargins(segments);
        });
    }

    private BrokerOrder order(JsonNode row, InstrumentSnapshot snapshot) {
        long quantity = quantity(row, "quantity", true);
        long filled = quantity(row, "filled_quantity", false);
        OrderStatus status = status(text(row, "status", 64), filled);
        if (status == OrderStatus.FILLED && filled != quantity) throw invalid();
        return new BrokerOrder(text(row, "order_id", 128), optionalText(row, "exchange_order_id"),
                optionalText(row, "parent_order_id"), instrument(row, snapshot), side(row),
                orderType(text(row, "order_type", 64)), product(row), validity(text(row, "validity", 64)),
                variety(text(row, "variety", 64)), status, quantity, filled,
                quantity(row, "pending_quantity", false), quantity(row, "cancelled_quantity", false),
                quantity(row, "disclosed_quantity", false), price(row, "price"), price(row, "trigger_price"),
                price(row, "average_price"), timestamp(required(row, "order_timestamp")),
                optionalTimestamp(row, "exchange_timestamp"), optionalCorrelation(row, "tag"),
                optionalTimestamp(row, "exchange_update_timestamp"));
    }
    private static Optional<BrokerCorrelationId> optionalCorrelation(JsonNode row, String name) {
        var value = optionalText(row, name);
        if (value.isEmpty()) return Optional.empty();
        try { return Optional.of(new BrokerCorrelationId(value.get())); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private BrokerTrade trade(JsonNode row, InstrumentSnapshot snapshot) {
        // Kite also returns order_timestamp as a time-only value: never invent its calendar date.
        return new BrokerTrade(text(row, "trade_id", 128), text(row, "order_id", 128),
                optionalText(row, "exchange_order_id"), instrument(row, snapshot), side(row), product(row),
                quantity(row, "quantity", true), price(row, "average_price"),
                timestamp(required(row, "fill_timestamp")), optionalTimestamp(row, "exchange_timestamp"));
    }
    private BrokerPosition position(JsonNode row, InstrumentSnapshot snapshot) {
        return new BrokerPosition(instrument(row, snapshot), product(row), integer(row, "quantity"),
                integer(row, "overnight_quantity"), BigDecimal.valueOf(quantity(row, "multiplier", true)), price(row, "average_price"),
                price(row, "close_price"), price(row, "last_price"), decimal(row, "value"),
                decimal(row, "pnl"), decimal(row, "m2m"), decimal(row, "unrealised"),
                decimal(row, "realised"), sideTotals(row, "buy"), sideTotals(row, "sell"),
                sideTotals(row, "day_buy"), sideTotals(row, "day_sell"));
    }
    private BrokerPosition.SideTotals sideTotals(JsonNode row, String prefix) {
        return new BrokerPosition.SideTotals(quantity(row, prefix + "_quantity", false),
                price(row, prefix + "_price"), decimal(row, prefix + "_value"));
    }
    private BrokerHolding holding(JsonNode row, InstrumentSnapshot snapshot) {
        Optional<BrokerHolding.MarginFundedHolding> funded = Optional.empty();
        if (row.hasNonNull("mtf")) {
            JsonNode mtf = holdingObject(row.get("mtf"), "mtf");
            funded = Optional.of(new BrokerHolding.MarginFundedHolding(holdingQuantity(mtf, "quantity"),
                    holdingQuantity(mtf, "used_quantity"), holdingPrice(mtf, "average_price"),
                    holdingDecimal(mtf, "value"), holdingDecimal(mtf, "initial_margin")));
        }
        return new BrokerHolding(holdingInstrument(row, snapshot), holdingText(row, "isin"), holdingProduct(row),
                holdingQuantity(row, "quantity"), holdingQuantity(row, "used_quantity"),
                holdingQuantity(row, "t1_quantity"), holdingQuantity(row, "realised_quantity"),
                holdingQuantity(row, "authorised_quantity"), holdingQuantity(row, "opening_quantity"),
                holdingQuantity(row, "collateral_quantity"), holdingPrice(row, "average_price"),
                holdingPrice(row, "last_price"), holdingPrice(row, "close_price"), holdingDecimal(row, "pnl"),
                holdingDecimal(row, "day_change"), holdingDecimal(row, "day_change_percentage"),
                holdingBoolean(row, "discrepancy"), funded);
    }

    private InstrumentId holdingInstrument(JsonNode row, InstrumentSnapshot snapshot) {
        JsonNode token = row.get("instrument_token");
        boolean tokenPresent = token != null && !token.isNull();
        boolean tokenValid = false;
        long value;
        try {
            if (token.isIntegralNumber() && token.canConvertToLong()) value = token.longValue();
            else if (token.isTextual() && token.textValue().matches("[0-9]{1,10}"))
                value = Long.parseLong(token.textValue());
            else throw new IllegalArgumentException();
            if (value <= 0 || value > 0xffff_ffffL) throw new IllegalArgumentException();
            tokenValid = true;
        } catch (RuntimeException invalid) {
            value = 0;
        }
        boolean exchangePresent = validHoldingText(row.get("exchange"), 32);
        boolean symbolPresent = validHoldingText(row.get("tradingsymbol"), 128);
        var instrument = tokenValid
                ? snapshot.byBrokerId().get(new BrokerInstrumentId(BROKER_ID, Long.toString(value))) : null;
        var bySymbol = exchangePresent && symbolPresent
                ? snapshot.byExchangeAndSymbol().get(new ExchangeSymbol(row.get("exchange").textValue(),
                row.get("tradingsymbol").textValue())) : null;
        boolean brokerTokenMatch = instrument != null;
        boolean exchangeSymbolMatch = bySymbol != null;
        boolean identityConflict = brokerTokenMatch && (!exchangeSymbolMatch || !instrument.equals(bySymbol));
        boolean registryVersionPresent = snapshot.version() > 0;
        if (!tokenValid || !brokerTokenMatch || !exchangeSymbolMatch || identityConflict) {
            LOG.warn("Kite holdings instrument resolution failed: tokenPresent={}, tokenValid={}, "
                            + "exchangePresent={}, symbolPresent={}, brokerTokenMatch={}, "
                            + "exchangeSymbolMatch={}, identityConflict={}, registryVersionPresent={}",
                    tokenPresent, tokenValid, exchangePresent, symbolPresent, brokerTokenMatch,
                    exchangeSymbolMatch, identityConflict, registryVersionPresent);
        }
        if (!tokenPresent) throw holdingFailure("MISSING_REQUIRED_FIELD", "instrument_token");
        if (!tokenValid) throw holdingFailure("INVALID_NUMERIC_FIELD", "instrument_token");
        if (!brokerTokenMatch) throw holdingFailure("UNRESOLVED_INSTRUMENT", "instrument_token");
        if (!exchangeSymbolMatch || identityConflict) throw holdingFailure("CONFLICTING_INSTRUMENT", "exchange");
        return instrument.id();
    }

    private static boolean validHoldingText(JsonNode node, int max) {
        if (node == null || !node.isTextual()) return false;
        String value = node.textValue();
        return !value.isBlank() && value.length() <= max
                && value.chars().noneMatch(Character::isISOControl);
    }

    private Product holdingProduct(JsonNode row) {
        String value = holdingText(row, "product");
        return switch (value) {
            case "CNC" -> Product.DELIVERY; case "MIS" -> Product.INTRADAY;
            case "NRML" -> Product.CARRY_FORWARD; case "CO" -> Product.COVER;
            case "BO" -> Product.BRACKET; case "MTF" -> Product.MARGIN_FUNDING;
            default -> Product.UNKNOWN;
        };
    }

    private String holdingText(JsonNode row, String field) {
        try { return text(row, field, 128); }
        catch (BrokerReadException invalid) {
            JsonNode node = row.get(field);
            throw holdingFailure(node == null || node.isNull() ? "MISSING_REQUIRED_FIELD" : "INVALID_STRUCTURE", field);
        }
    }
    private long holdingQuantity(JsonNode row, String field) {
        try { return quantity(row, field, false); }
        catch (BrokerReadException invalid) {
            JsonNode node = row.get(field);
            throw holdingFailure(node == null || node.isNull() ? "MISSING_REQUIRED_FIELD" : "INVALID_NUMERIC_FIELD", field);
        }
    }
    private BigDecimal holdingDecimal(JsonNode row, String field) {
        try { return decimal(row, field); }
        catch (BrokerReadException invalid) {
            JsonNode node = row.get(field);
            throw holdingFailure(node == null || node.isNull() ? "MISSING_REQUIRED_FIELD" : "INVALID_NUMERIC_FIELD", field);
        }
    }
    private BigDecimal holdingPrice(JsonNode row, String field) {
        try { return price(row, field); }
        catch (BrokerReadException invalid) {
            JsonNode node = row.get(field);
            throw holdingFailure(node == null || node.isNull() ? "MISSING_REQUIRED_FIELD" : "INVALID_NUMERIC_FIELD", field);
        }
    }
    private boolean holdingBoolean(JsonNode row, String field) {
        try { return bool(row, field); }
        catch (BrokerReadException invalid) {
            JsonNode node = row.get(field);
            throw holdingFailure(node == null || node.isNull() ? "MISSING_REQUIRED_FIELD" : "INVALID_STRUCTURE", field);
        }
    }
    private JsonNode holdingObject(JsonNode node, String field) {
        try { return object(node); }
        catch (BrokerReadException invalid) { throw holdingFailure("INVALID_STRUCTURE", field); }
    }
    private static HoldingNormalizationFailure holdingFailure(String reason, String field) {
        return new HoldingNormalizationFailure(reason, field);
    }
    private static final class HoldingNormalizationFailure extends RuntimeException {
        private final String reason;
        private final String field;
        private HoldingNormalizationFailure(String reason, String field) {
            super(null, null, false, false);
            this.reason = reason;
            this.field = field;
        }
    }
    private BrokerMargins.SegmentMargin margin(JsonNode row) {
        object(row);
        JsonNode available = object(required(row, "available"));
        JsonNode utilised = object(required(row, "utilised"));
        return new BrokerMargins.SegmentMargin(bool(row, "enabled"), decimal(row, "net"),
                new BrokerMargins.AvailableMargin(decimal(available, "adhoc_margin"), decimal(available, "cash"),
                        decimal(available, "opening_balance"), decimal(available, "live_balance"),
                        decimal(available, "collateral"), decimal(available, "intraday_payin")),
                new BrokerMargins.UtilisedMargin(decimal(utilised, "debits"), decimal(utilised, "exposure"),
                        decimal(utilised, "m2m_realised"), decimal(utilised, "m2m_unrealised"),
                        decimal(utilised, "option_premium"), decimal(utilised, "payout"), decimal(utilised, "span"),
                        decimal(utilised, "holding_sales"), decimal(utilised, "turnover"),
                        decimal(utilised, "liquid_collateral"), decimal(utilised, "stock_collateral"),
                        decimal(utilised, "delivery")));
    }

    private InstrumentId instrument(JsonNode row, InstrumentSnapshot snapshot) {
        JsonNode token = required(row, "instrument_token");
        long value;
        if (token.isIntegralNumber() && token.canConvertToLong()) value = token.longValue();
        else if (token.isTextual() && token.textValue().matches("[0-9]{1,10}"))
            value = Long.parseLong(token.textValue());
        else throw invalid();
        if (value <= 0 || value > 0xffff_ffffL) throw invalid();
        var instrument = snapshot.byBrokerId().get(new BrokerInstrumentId(BROKER_ID, Long.toString(value)));
        var symbol = new ExchangeSymbol(text(row, "exchange", 32), text(row, "tradingsymbol", 128));
        if (instrument == null || !instrument.equals(snapshot.byExchangeAndSymbol().get(symbol))) throw invalid();
        return instrument.id();
    }

    private static <T> T map(String body, Function<JsonNode, T> normalization) {
        try {
            if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARS) throw invalid();
            JsonNode root = object(JSON.readTree(body));
            validateTree(root, new int[]{0});
            String status = text(root, "status", 64);
            if (status.equals("error")) throw new BrokerReadException(BROKER_API);
            if (!status.equals("success")) throw invalid();
            return normalization.apply(required(root, "data"));
        } catch (BrokerReadException safe) { throw safe; }
        catch (HoldingNormalizationFailure diagnostic) { throw diagnostic; }
        catch (IOException | RuntimeException malformed) { throw invalid(); }
    }
    private static void validateTree(JsonNode node, int[] count) {
        if (++count[0] > 500_000 || (node.isTextual() && node.textValue().length() > 1024)
                || (node.isArray() && node.size() > MAX_ROWS)) throw invalid();
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getKey().length() > 128) throw invalid();
                validateTree(field.getValue(), count);
            }
        } else if (node.isArray()) node.forEach(child -> validateTree(child, count));
    }
    private static <T> List<T> rows(JsonNode node, Function<JsonNode, T> mapper) {
        if (!node.isArray() || node.size() > MAX_ROWS) throw invalid();
        var result = new ArrayList<T>(node.size());
        for (JsonNode row : node) result.add(mapper.apply(object(row)));
        return List.copyOf(result);
    }
    private static <T> List<T> distinct(List<T> rows, Function<T, Object> identity) {
        var seen = new HashSet<>();
        for (T row : rows) if (!seen.add(identity.apply(row))) throw invalid();
        return rows;
    }
    private static JsonNode object(JsonNode node) {
        if (node == null || !node.isObject()) throw invalid();
        return node;
    }
    private static JsonNode required(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) throw invalid();
        return node;
    }
    private static String text(JsonNode row, String field, int max) {
        JsonNode node = required(row, field);
        if (!node.isTextual()) throw invalid();
        String value = node.textValue();
        if (value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw invalid();
        return value;
    }
    private static Optional<String> optionalText(JsonNode row, String field) {
        return row.hasNonNull(field) ? Optional.of(text(row, field, 128)) : Optional.empty();
    }
    private static boolean bool(JsonNode row, String field) {
        JsonNode node = required(row, field);
        if (!node.isBoolean()) throw invalid();
        return node.booleanValue();
    }
    private static long integer(JsonNode row, String field) {
        JsonNode node = required(row, field);
        if (!node.isIntegralNumber() || !node.canConvertToLong()) throw invalid();
        return node.longValue();
    }
    private static long quantity(JsonNode row, String field, boolean positive) {
        long value = integer(row, field);
        if (value < 0 || (positive && value == 0)) throw invalid();
        return value;
    }
    private static BigDecimal decimal(JsonNode row, String field) {
        JsonNode node = required(row, field);
        if (!node.isNumber()) throw invalid();
        BigDecimal value = node.decimalValue();
        if (value.precision() > 36 || value.scale() > 18 || value.scale() < -18
                || value.abs().compareTo(AMOUNT_LIMIT) >= 0) throw invalid();
        return value;
    }
    private static BigDecimal price(JsonNode row, String field) {
        BigDecimal value = decimal(row, field);
        if (value.signum() < 0) throw invalid();
        return value;
    }
    private static Instant timestamp(JsonNode node) {
        if (!node.isTextual() || node.textValue().length() != 19) throw invalid();
        return LocalDateTime.parse(node.textValue(), TIMESTAMP).atZone(KITE_TIMEZONE).toInstant();
    }
    private static Optional<Instant> optionalTimestamp(JsonNode row, String field) {
        return row.hasNonNull(field) ? Optional.of(timestamp(row.get(field))) : Optional.empty();
    }
    private static Side side(JsonNode row) {
        return switch (text(row, "transaction_type", 64)) {
            case "BUY" -> Side.BUY; case "SELL" -> Side.SELL; default -> Side.UNKNOWN;
        };
    }
    private static Product product(JsonNode row) {
        return switch (text(row, "product", 64)) {
            case "CNC" -> Product.DELIVERY; case "MIS" -> Product.INTRADAY;
            case "NRML" -> Product.CARRY_FORWARD; case "CO" -> Product.COVER;
            case "BO" -> Product.BRACKET; case "MTF" -> Product.MARGIN_FUNDING;
            default -> Product.UNKNOWN;
        };
    }
    private static OrderType orderType(String value) {
        return switch (value) {
            case "MARKET" -> OrderType.MARKET; case "LIMIT" -> OrderType.LIMIT;
            case "SL" -> OrderType.STOP_LIMIT; case "SL-M" -> OrderType.STOP_MARKET;
            default -> OrderType.UNKNOWN;
        };
    }
    private static Validity validity(String value) {
        return switch (value) {
            case "DAY" -> Validity.DAY; case "IOC" -> Validity.IMMEDIATE_OR_CANCEL;
            case "TTL" -> Validity.TIME_TO_LIVE; default -> Validity.UNKNOWN;
        };
    }
    private static Variety variety(String value) {
        return switch (value) {
            case "regular" -> Variety.REGULAR; case "amo" -> Variety.AFTER_MARKET;
            case "co" -> Variety.COVER; case "iceberg" -> Variety.ICEBERG;
            case "auction" -> Variety.AUCTION; default -> Variety.UNKNOWN;
        };
    }
    private static OrderStatus status(String value, long filled) {
        return switch (value) {
            case "PUT ORDER REQ RECEIVED", "AMO REQ RECEIVED" -> OrderStatus.RECEIVED;
            case "VALIDATION PENDING" -> OrderStatus.VALIDATION_PENDING;
            case "OPEN PENDING" -> OrderStatus.OPEN_PENDING;
            case "OPEN" -> filled > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.OPEN;
            case "COMPLETE" -> OrderStatus.FILLED;
            case "MODIFY VALIDATION PENDING" -> OrderStatus.MODIFY_VALIDATION_PENDING;
            case "MODIFY PENDING" -> OrderStatus.MODIFY_PENDING;
            case "TRIGGER PENDING" -> OrderStatus.TRIGGER_PENDING;
            case "CANCEL PENDING" -> OrderStatus.CANCEL_PENDING;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> OrderStatus.UNKNOWN;
        };
    }
    private static BrokerReadException invalid() { return new BrokerReadException(INVALID_RESPONSE); }
}
