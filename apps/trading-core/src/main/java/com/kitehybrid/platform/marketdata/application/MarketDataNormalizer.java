package com.kitehybrid.platform.marketdata.application;

import com.kitehybrid.platform.marketdata.domain.Tick;

/** Implementations live in broker infrastructure; SDK types must not escape this boundary. */
public interface MarketDataNormalizer<T> {
    Tick normalize(T source);
}
