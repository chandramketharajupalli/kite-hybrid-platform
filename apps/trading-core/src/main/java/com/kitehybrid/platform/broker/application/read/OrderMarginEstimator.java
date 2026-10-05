package com.kitehybrid.platform.broker.application.read;
import com.kitehybrid.platform.broker.domain.read.OrderMarginQuote;
/** Read-only calculation. No placement, reservation, authorization, retry or caching. */
@FunctionalInterface
public interface OrderMarginEstimator {
    OrderMarginQuote estimate(OrderMarginQuote.Request request);
    OrderMarginEstimator UNAVAILABLE = request -> { throw new IllegalStateException("MARGIN_ESTIMATE_UNAVAILABLE"); };
}
