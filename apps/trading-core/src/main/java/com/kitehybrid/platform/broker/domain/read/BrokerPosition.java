package com.kitehybrid.platform.broker.domain.read;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.util.Objects;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** Signed broker exposure and broker-computed values; never an authoritative position projection. */
public record BrokerPosition(InstrumentId instrumentId, Product product, long quantity,
                             long overnightQuantity, BigDecimal multiplier, BigDecimal averagePrice,
                             BigDecimal closePrice, BigDecimal lastPrice, BigDecimal value,
                             BigDecimal pnl, BigDecimal markToMarket, BigDecimal unrealisedPnl,
                             BigDecimal realisedPnl, SideTotals buy, SideTotals sell,
                             SideTotals dayBuy, SideTotals daySell) {
    public BrokerPosition {
        Objects.requireNonNull(instrumentId); Objects.requireNonNull(product);
        amount(multiplier);
        positive(multiplier.longValueExact());
        price(averagePrice); price(closePrice); price(lastPrice);
        amount(value); amount(pnl); amount(markToMarket); amount(unrealisedPnl); amount(realisedPnl);
        Objects.requireNonNull(buy); Objects.requireNonNull(sell);
        Objects.requireNonNull(dayBuy); Objects.requireNonNull(daySell);
    }
    public record SideTotals(long quantity, BigDecimal price, BigDecimal value) {
        public SideTotals { nonnegative(quantity); ReadModelValidation.price(price); amount(value); }
    }
}
