package com.kitehybrid.platform.broker.domain.read;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** An observed broker fill; no durable platform trade identity is created. */
public record BrokerTrade(String brokerTradeId, String brokerOrderId, Optional<String> exchangeOrderId,
                          InstrumentId instrumentId, Side side, Product product, long quantity,
                          BigDecimal price, Instant filledAt, Optional<Instant> exchangeTimestamp) {
    public BrokerTrade {
        identifier(brokerTradeId); identifier(brokerOrderId);
        Objects.requireNonNull(exchangeOrderId).ifPresent(ReadModelValidation::identifier);
        Objects.requireNonNull(instrumentId); Objects.requireNonNull(side); Objects.requireNonNull(product);
        positive(quantity); ReadModelValidation.price(price);
        Objects.requireNonNull(filledAt); Objects.requireNonNull(exchangeTimestamp);
    }
}
