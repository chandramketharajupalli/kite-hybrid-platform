package com.kitehybrid.platform.instrument.domain;

/** Exchange-scoped lookup key; spaces within symbols (for example indices) are preserved. */
public record ExchangeSymbol(String exchange, String tradingSymbol) {
    public ExchangeSymbol {
        exchange = InstrumentText.canonical(exchange, 32);
        tradingSymbol = InstrumentText.canonical(tradingSymbol, 128);
    }
}
