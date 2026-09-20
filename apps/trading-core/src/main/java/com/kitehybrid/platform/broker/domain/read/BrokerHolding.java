package com.kitehybrid.platform.broker.domain.read;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** Quantities are distinct broker-reported buckets, not an inferred available-to-sell quantity. */
public record BrokerHolding(InstrumentId instrumentId, String isin, Product product,
                            long quantity, long usedQuantity, long unsettledQuantity,
                            long realisedQuantity, long authorisedQuantity, long openingQuantity,
                            long collateralQuantity, BigDecimal averagePrice, BigDecimal lastPrice,
                            BigDecimal closePrice, BigDecimal pnl, BigDecimal dayChange,
                            BigDecimal dayChangePercentage, boolean discrepancy,
                            Optional<MarginFundedHolding> marginFunded) {
    public BrokerHolding {
        Objects.requireNonNull(instrumentId); identifier(isin); Objects.requireNonNull(product);
        nonnegative(quantity); nonnegative(usedQuantity); nonnegative(unsettledQuantity);
        nonnegative(realisedQuantity); nonnegative(authorisedQuantity);
        nonnegative(openingQuantity); nonnegative(collateralQuantity);
        price(averagePrice); price(lastPrice); price(closePrice);
        amount(pnl); amount(dayChange); amount(dayChangePercentage);
        Objects.requireNonNull(marginFunded);
    }
    public record MarginFundedHolding(long quantity, long usedQuantity, BigDecimal averagePrice,
                                      BigDecimal value, BigDecimal initialMargin) {
        public MarginFundedHolding {
            nonnegative(quantity); nonnegative(usedQuantity); price(averagePrice);
            amount(value); amount(initialMargin);
        }
    }
}
