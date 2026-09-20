package com.kitehybrid.platform.broker.domain.read;

/** Normalized observation types. UNKNOWN never authorizes a trading action. */
public final class TradingReadTypes {
    private TradingReadTypes() {}
    public enum Side { BUY, SELL, UNKNOWN }
    public enum OrderType { MARKET, LIMIT, STOP_LIMIT, STOP_MARKET, UNKNOWN }
    public enum Product { DELIVERY, INTRADAY, CARRY_FORWARD, COVER, BRACKET, MARGIN_FUNDING, UNKNOWN }
    public enum Validity { DAY, IMMEDIATE_OR_CANCEL, TIME_TO_LIVE, UNKNOWN }
    public enum Variety { REGULAR, AFTER_MARKET, COVER, ICEBERG, AUCTION, UNKNOWN }
    public enum OrderStatus {
        RECEIVED, VALIDATION_PENDING, OPEN_PENDING, OPEN, PARTIALLY_FILLED, FILLED,
        MODIFY_VALIDATION_PENDING, MODIFY_PENDING, TRIGGER_PENDING, CANCEL_PENDING,
        CANCELLED, REJECTED, UNKNOWN
    }
    public enum MarginSegment { EQUITY, COMMODITY }
}
