package com.kitehybrid.platform.broker.domain.read;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static com.kitehybrid.platform.broker.domain.read.ReadModelValidation.*;

/** Transient exact-request calculation, never an order/authorization. receivedAt is LOCAL receipt time. */
public record OrderMarginQuote(Request request, BigDecimal requiredMargin, BigDecimal charges,
                               Optional<CollateralTerms> collateralTerms, Instant receivedAt) {
    public OrderMarginQuote {
        Objects.requireNonNull(request); price(requiredMargin); price(charges);
        if(requiredMargin.signum()==0) throw new IllegalArgumentException("Empty margin requirement");
        Objects.requireNonNull(collateralTerms); Objects.requireNonNull(receivedAt);
        collateralTerms.ifPresent(t -> { if(t.minimumCash().compareTo(requiredMargin)>0)
            throw new IllegalArgumentException("Inconsistent cash requirement"); });
    }
    /** Only an authoritative current provider may supply these terms. Never infer them from net,
     * utilised collateral, or a successful margin calculation. Kite's documented response omits them. */
    public record CollateralTerms(BigDecimal eligibleAdjustedCollateral, BigDecimal minimumCash) {
        public CollateralTerms { price(eligibleAdjustedCollateral); price(minimumCash); }
        @Override public String toString() { return "CollateralTerms[withheld]"; }
    }
    public record Request(InstrumentId instrumentId, String exchange, String symbol, Side side,
                          OrderType orderType, Product product, Validity validity, Variety variety, long quantity) {
        public Request {
            Objects.requireNonNull(instrumentId); Objects.requireNonNull(symbol); positive(quantity);
            if(!"NSE".equals(exchange) || symbol.isBlank() || symbol.length()>128 || !symbol.equals(symbol.strip())
                    || side!=Side.BUY || orderType!=OrderType.MARKET || product!=Product.INTRADAY
                    || validity!=Validity.DAY || variety!=Variety.REGULAR)
                throw new IllegalArgumentException("Unsupported margin request");
        }
    }
    @Override public String toString() { return "OrderMarginQuote[withheld]"; }
}
