package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CashRiskArithmeticTest {
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final Instrument instrument = Instrument.create(new BrokerInstrumentId("ZERODHA", "1"), "ABC", "NSE",
            "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);

    @Test void marketAndLimitUseMaximumReferenceTimesBufferAndInclusiveCap() {
        assertEquals(RiskReason.APPROVED, evaluate("60", null, "1", "60", margins("100","100","100","100","0","0","0")));
        assertEquals(RiskReason.ORDER_VALUE_LIMIT, evaluate("60", null, "1", "59.99", margins("100","100","100","100","0","0","0")));
        assertEquals(RiskReason.APPROVED, evaluate("60", "80", "1.1", "88", margins("100","100","100","100","0","0","0")));
        assertEquals(RiskReason.ORDER_VALUE_LIMIT, evaluate("80", "60", "1.1", "87.99", margins("100","100","100","100","0","0","0")));
    }
    @Test void usableCashIsMinimumBucketLessNonnegativeLiabilitiesAndReserve() {
        var margin = margins("100","90","80","70","10","5","5");
        assertEquals(RiskReason.APPROVED, evaluate("49", null, "1", "1000", margin));
        assertEquals(RiskReason.INSUFFICIENT_MARGIN, evaluate("49.01", null, "1", "1000", margin));
        assertEquals(RiskReason.INSUFFICIENT_MARGIN, evaluate("1", null, "1", "1000", margins("0","100","100","100","0","0","0")));
    }
    @Test void negativeLiabilitiesNeverCreateCreditAndNegativeBalanceCannotApprove() {
        assertEquals(RiskReason.INSUFFICIENT_MARGIN, evaluate("100", null, "1", "1000", margins("100","100","100","100","-100","-100","-100")));
        assertEquals(RiskReason.INSUFFICIENT_MARGIN, evaluate("1", null, "1", "1000", margins("100","100","100","-1","0","0","0")));
    }
    private RiskReason evaluate(String price, String limit, String buffer, String cap, BrokerMargins margins) {
        var command = new PlaceOrder("arithmetic", instrument.id(), OrderSide.BUY, 1,
                limit == null ? OrderType.MARKET : OrderType.LIMIT, OrderProduct.DELIVERY, OrderValidity.DAY,
                Optional.ofNullable(limit).map(BigDecimal::new), Optional.empty(), 0, OrderVariety.REGULAR);
        var order = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.VALIDATED,
                Optional.empty(), Optional.empty(), NOW, NOW, 1);
        var input = new OrderRiskInput(InstrumentSnapshot.validated(List.of(instrument), 1, NOW),
                Map.of(instrument.id(), new Tick(instrument.id(), new BigDecimal(price), NOW)), true,
                new BrokerPositions(List.of(), List.of()), List.of(), margins, List.of());
        var limits = new RiskLimits(true, 1, new BigDecimal(cap), 1, new BigDecimal("1000"),
                Duration.ofSeconds(10), Duration.ofSeconds(10), new BigDecimal(buffer), BigDecimal.ONE);
        return CashOrderRiskRules.evaluate(order, input, limits, false, NOW);
    }
    private static BrokerMargins margins(String cash, String opening, String live, String net, String debit, String payout, String sales) {
        var segment = new BrokerMargins.SegmentMargin(true, new BigDecimal(net),
                new BrokerMargins.AvailableMargin(ZERO, new BigDecimal(cash), new BigDecimal(opening), new BigDecimal(live), ZERO, ZERO),
                new BrokerMargins.UtilisedMargin(new BigDecimal(debit), ZERO, ZERO, ZERO, ZERO, new BigDecimal(payout),
                        ZERO, new BigDecimal(sales), ZERO, ZERO, ZERO, ZERO));
        return new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY, segment, TradingReadTypes.MarginSegment.COMMODITY, segment));
    }
}
