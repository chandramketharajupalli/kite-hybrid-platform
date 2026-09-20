package com.kitehybrid.platform.broker.domain.read;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** Broker-reported account balances. Both account segments must be supplied explicitly. */
public record BrokerMargins(Map<MarginSegment, SegmentMargin> segments) {
    public BrokerMargins {
        segments = Map.copyOf(segments);
        if (segments.size() != MarginSegment.values().length)
            throw new IllegalArgumentException("Incomplete margin segments");
    }
    public record SegmentMargin(boolean enabled, BigDecimal net, AvailableMargin available,
                                 UtilisedMargin utilised) {
        public SegmentMargin { amount(net); Objects.requireNonNull(available); Objects.requireNonNull(utilised); }
    }
    public record AvailableMargin(BigDecimal adhocMargin, BigDecimal cash, BigDecimal openingBalance,
                                   BigDecimal liveBalance, BigDecimal collateral, BigDecimal intradayPayin) {
        public AvailableMargin {
            amount(adhocMargin); amount(cash); amount(openingBalance); amount(liveBalance);
            amount(collateral); amount(intradayPayin);
        }
    }
    public record UtilisedMargin(BigDecimal debits, BigDecimal exposure, BigDecimal realisedMarkToMarket,
                                  BigDecimal unrealisedMarkToMarket, BigDecimal optionPremium,
                                  BigDecimal payout, BigDecimal span, BigDecimal holdingSales,
                                  BigDecimal turnover, BigDecimal liquidCollateral,
                                  BigDecimal stockCollateral, BigDecimal delivery) {
        public UtilisedMargin {
            amount(debits); amount(exposure); amount(realisedMarkToMarket); amount(unrealisedMarkToMarket);
            amount(optionPremium); amount(payout); amount(span); amount(holdingSales); amount(turnover);
            amount(liquidCollateral); amount(stockCollateral); amount(delivery);
        }
    }
}
