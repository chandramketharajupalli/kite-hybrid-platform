package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.order.domain.command.*;
import java.math.BigDecimal;
import java.util.Objects;

/** Command shape and reference-data validation. Risk approval remains a separate phase. */
public final class OrderCommandValidator {
    private final InstrumentRegistry instruments;
    public OrderCommandValidator(InstrumentRegistry instruments) { this.instruments = Objects.requireNonNull(instruments); }

    public void validate(PlaceOrder command) {
        requireInstrument(command.instrumentId());
        if (command.disclosedQuantity() > command.quantity()) fail("DISCLOSED_QUANTITY_EXCEEDS_QUANTITY");
        validatePrices(command.orderType(), command.limitPrice(), command.triggerPrice());
        var instrument = instruments.snapshot().byId().get(command.instrumentId());
        if (instrument == null) fail("UNRESOLVED_INSTRUMENT");
        if (command.quantity() % instrument.lotSize() != 0) fail("QUANTITY_NOT_LOT_MULTIPLE");
    }
    public void validate(ModifyOrder command, com.kitehybrid.platform.order.domain.OrderRecord existing) {
        validatePrices(command.orderType(), command.limitPrice(), command.triggerPrice());
        if (command.disclosedQuantity() > command.quantity()) fail("DISCLOSED_QUANTITY_EXCEEDS_QUANTITY");
        if (existing == null) fail("ORDER_NOT_FOUND");
        if (existing.state().terminal()) fail("TERMINAL_ORDER");
        if (existing.brokerOrderId().isEmpty()) fail("BROKER_ORDER_ID_MISSING");
        var instrument = instruments.snapshot().byId().get(existing.command().instrumentId());
        if (instrument == null || command.quantity() % instrument.lotSize() != 0) fail("QUANTITY_NOT_LOT_MULTIPLE");
    }
    public void validate(CancelOrder command, com.kitehybrid.platform.order.domain.OrderRecord existing) {
        if (existing == null) fail("ORDER_NOT_FOUND");
        if (existing.state().terminal()) fail("TERMINAL_ORDER");
        if (existing.brokerOrderId().isEmpty()) fail("BROKER_ORDER_ID_MISSING");
    }
    private void requireInstrument(com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId id) {
        if (instruments.snapshot().byId().get(id) == null) fail("UNRESOLVED_INSTRUMENT");
    }
    private static void validatePrices(OrderType type, java.util.Optional<BigDecimal> limit,
                                       java.util.Optional<BigDecimal> trigger) {
        switch (type) {
            case MARKET -> { if (limit.isPresent() || trigger.isPresent()) fail("MARKET_PRICE_NOT_ALLOWED"); }
            case LIMIT -> { if (limit.isEmpty() || trigger.isPresent()) fail("INVALID_LIMIT_PRICES"); }
            case STOP_LIMIT -> { if (limit.isEmpty() || trigger.isEmpty()) fail("STOP_LIMIT_PRICES_REQUIRED"); }
            case STOP_MARKET -> { if (limit.isPresent() || trigger.isEmpty()) fail("INVALID_STOP_MARKET_PRICES"); }
        }
    }
    private static void fail(String reason) { throw new OrderCommandValidationException(reason); }
}
