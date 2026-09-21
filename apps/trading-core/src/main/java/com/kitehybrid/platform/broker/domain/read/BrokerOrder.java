package com.kitehybrid.platform.broker.domain.read;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.BrokerCorrelationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** Broker observation only: not a platform order aggregate or execution authorization. */
public record BrokerOrder(String brokerOrderId, Optional<String> exchangeOrderId,
                          Optional<String> parentOrderId, InstrumentId instrumentId,
                          Side side, OrderType orderType, Product product, Validity validity,
                          Variety variety, OrderStatus status, long quantity, long filledQuantity,
                          long pendingQuantity, long cancelledQuantity, long disclosedQuantity,
                          BigDecimal price, BigDecimal triggerPrice, BigDecimal averagePrice,
                          Instant orderedAt, Optional<Instant> exchangeTimestamp,
                          Optional<BrokerCorrelationId> correlationId,
                          Optional<Instant> exchangeUpdatedAt) {
    public BrokerOrder {
        identifier(brokerOrderId);
        Objects.requireNonNull(exchangeOrderId).ifPresent(ReadModelValidation::identifier);
        Objects.requireNonNull(parentOrderId).ifPresent(ReadModelValidation::identifier);
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(side);
        Objects.requireNonNull(orderType);
        Objects.requireNonNull(product);
        Objects.requireNonNull(validity);
        Objects.requireNonNull(variety);
        Objects.requireNonNull(status);
        positive(quantity);
        nonnegative(filledQuantity); nonnegative(pendingQuantity);
        nonnegative(cancelledQuantity); nonnegative(disclosedQuantity);
        if (filledQuantity > quantity || pendingQuantity > quantity || cancelledQuantity > quantity
                || disclosedQuantity > quantity || pendingQuantity > quantity - filledQuantity)
            throw new IllegalArgumentException("Order quantities exceed total");
        if ((status == OrderStatus.FILLED && filledQuantity != quantity)
                || (status == OrderStatus.PARTIALLY_FILLED && (filledQuantity == 0 || filledQuantity == quantity))
                || (status == OrderStatus.OPEN && filledQuantity != 0))
            throw new IllegalArgumentException("Order status contradicts filled quantity");
        ReadModelValidation.price(price); ReadModelValidation.price(triggerPrice);
        ReadModelValidation.price(averagePrice);
        Objects.requireNonNull(orderedAt);
        Objects.requireNonNull(exchangeTimestamp); Objects.requireNonNull(correlationId);
        Objects.requireNonNull(exchangeUpdatedAt);
    }
    public BrokerOrder(String brokerOrderId, Optional<String> exchangeOrderId, Optional<String> parentOrderId,
                       InstrumentId instrumentId, Side side, OrderType orderType, Product product, Validity validity,
                       Variety variety, OrderStatus status, long quantity, long filledQuantity, long pendingQuantity,
                       long cancelledQuantity, long disclosedQuantity, BigDecimal price, BigDecimal triggerPrice,
                       BigDecimal averagePrice, Instant orderedAt, Optional<Instant> exchangeTimestamp,
                       Optional<Instant> exchangeUpdatedAt) {
        this(brokerOrderId, exchangeOrderId, parentOrderId, instrumentId, side, orderType, product, validity, variety,
                status, quantity, filledQuantity, pendingQuantity, cancelledQuantity, disclosedQuantity, price,
                triggerPrice, averagePrice, orderedAt, exchangeTimestamp, Optional.empty(), exchangeUpdatedAt);
    }
}
