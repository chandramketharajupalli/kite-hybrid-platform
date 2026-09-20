package com.kitehybrid.platform.broker.infrastructure.kite;

final class KiteTradingReadFixtures {
    private KiteTradingReadFixtures() {}
    static String envelope(String data) { return "{\"status\":\"success\",\"data\":" + data + "}"; }
    static final String ORDER = """
            {"order_id":"order-1","exchange_order_id":"exchange-1","parent_order_id":null,
             "instrument_token":256265,"exchange":"NSE","tradingsymbol":"INFY",
             "transaction_type":"BUY","order_type":"LIMIT","product":"CNC","validity":"DAY",
             "variety":"regular","status":"OPEN","quantity":10,"filled_quantity":2,
             "pending_quantity":8,"cancelled_quantity":0,"disclosed_quantity":0,
             "price":100.1250,"trigger_price":0,"average_price":100.1250,
             "order_timestamp":"2026-09-18 09:15:30","exchange_timestamp":"2026-09-18 09:15:31",
             "exchange_update_timestamp":null}
            """;
    static final String TRADE = """
            {"trade_id":"trade-1","order_id":"order-1","exchange_order_id":"exchange-1",
             "instrument_token":256265,"exchange":"NSE","tradingsymbol":"INFY",
             "transaction_type":"BUY","product":"CNC","quantity":2,"average_price":100.1250,
             "fill_timestamp":"2026-09-18 09:15:31","order_timestamp":"09:15:30",
             "exchange_timestamp":"2026-09-18 09:15:31"}
            """;
    static final String POSITION = """
            {"instrument_token":256265,"exchange":"NSE","tradingsymbol":"INFY","product":"MIS",
             "quantity":-3,"overnight_quantity":-1,"multiplier":1,"average_price":100.25,
             "close_price":100,"last_price":101,"value":300.75,"pnl":-2.25,"m2m":-3,
             "unrealised":-2.25,"realised":0,
             "buy_quantity":2,"buy_price":100.25,"buy_value":200.50,
             "sell_quantity":5,"sell_price":100.25,"sell_value":501.25,
             "day_buy_quantity":1,"day_buy_price":100.25,"day_buy_value":100.25,
             "day_sell_quantity":3,"day_sell_price":100.25,"day_sell_value":300.75}
            """;
    static final String HOLDING = """
            {"instrument_token":256265,"exchange":"NSE","tradingsymbol":"INFY","isin":"INE009A01021",
             "product":"CNC","quantity":10,"used_quantity":1,"t1_quantity":2,"realised_quantity":10,
             "authorised_quantity":9,"opening_quantity":11,"collateral_quantity":3,
             "average_price":100,"last_price":99,"close_price":99.1,"pnl":-10,
             "day_change":-0.09999999999999432,"day_change_percentage":-0.100908173562,
             "discrepancy":false}
            """;
    static final String SEGMENT = """
            {"enabled":true,"net":99725.05000000002,
             "available":{"adhoc_margin":0,"cash":245431.6,"opening_balance":245431.6,
               "live_balance":99725.05000000002,"collateral":0,"intraday_payin":0},
             "utilised":{"debits":145706.55,"exposure":38981.25,"m2m_realised":761.7,
               "m2m_unrealised":-5,"option_premium":0,"payout":0,"span":101989,
               "holding_sales":0,"turnover":0,"liquid_collateral":0,"stock_collateral":0,"delivery":0}}
            """;
    static final String MARGINS = envelope("{\"equity\":" + SEGMENT + ",\"commodity\":"
            + SEGMENT.replace("\"enabled\":true", "\"enabled\":false") + "}");
}
