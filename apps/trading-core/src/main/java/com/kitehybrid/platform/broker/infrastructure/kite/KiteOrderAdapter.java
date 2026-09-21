package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Kite order protocol adapter. It is never wired unless the explicit execution flag is true. */
final class KiteOrderAdapter implements OrderExecutionGateway {
    private static final JsonMapper JSON = new JsonMapper();
    private final KiteRestTransport transport;
    private final InstrumentRegistry instruments;
    private final OrderExecutionProperties properties;
    KiteOrderAdapter(KiteRestTransport transport, InstrumentRegistry instruments, OrderExecutionProperties properties) {
        this.transport = transport; this.instruments = instruments; this.properties = properties;
    }
    @Override public String place(OrderRecord order) {
        requireEnabled(); var instrument = instrument(order); var c = order.command();
        var form = new LinkedHashMap<String, String>();
        form.put("exchange", instrument.exchange()); form.put("tradingsymbol", instrument.tradingSymbol());
        form.put("transaction_type", c.side() == OrderSide.BUY ? "BUY" : "SELL");
        form.put("order_type", wire(c.orderType())); form.put("quantity", Long.toString(c.quantity()));
        form.put("product", wire(c.product())); form.put("validity", wire(c.validity()));
        c.limitPrice().ifPresent(value -> form.put("price", value.toPlainString()));
        c.triggerPrice().ifPresent(value -> form.put("trigger_price", value.toPlainString()));
        if (c.disclosedQuantity() > 0) form.put("disclosed_quantity", Long.toString(c.disclosedQuantity()));
        order.brokerCorrelationId().ifPresent(value -> form.put("tag", value.value()));
        try { return response(transport.postRegularOrder(form)); }
        catch (BrokerReadException failure) { throw map(failure); }
    }
    @Override public void modify(OrderRecord order, ModifyOrder c) {
        requireEnabled(); var form = new LinkedHashMap<String, String>();
        form.put("order_type", wire(c.orderType())); form.put("quantity", Long.toString(c.quantity()));
        form.put("validity", wire(c.validity())); c.limitPrice().ifPresent(v -> form.put("price", v.toPlainString()));
        c.triggerPrice().ifPresent(v -> form.put("trigger_price", v.toPlainString()));
        if (c.disclosedQuantity() > 0) form.put("disclosed_quantity", Long.toString(c.disclosedQuantity()));
        try { response(transport.putRegularOrder(order.brokerOrderId().orElseThrow(), form)); }
        catch (BrokerReadException failure) { throw map(failure); }
    }
    @Override public void cancel(OrderRecord order, CancelOrder command) {
        requireEnabled();
        try { response(transport.deleteRegularOrder(order.brokerOrderId().orElseThrow())); }
        catch (BrokerReadException failure) { throw map(failure); }
    }
    private com.kitehybrid.platform.instrument.domain.Instrument instrument(OrderRecord order) {
        var instrument = instruments.snapshot().byId().get(order.command().instrumentId());
        if (instrument == null) throw new OrderExecutionException(OrderExecutionException.Category.MALFORMED_RESPONSE);
        return instrument;
    }
    private void requireEnabled() { if (!properties.enabled()) throw new OrderExecutionException(OrderExecutionException.Category.DISABLED); }
    private static OrderExecutionException map(BrokerReadException failure) {
        return switch (failure.category()) {
            case AUTHENTICATION -> new OrderExecutionException(OrderExecutionException.Category.AUTHENTICATION);
            case BROKER_API -> failure.httpStatus() >= 400 && failure.httpStatus() < 500
                    ? new OrderExecutionException(OrderExecutionException.Category.BROKER_REJECTED)
                    : new OrderExecutionException(OrderExecutionException.Category.AMBIGUOUS);
            default -> new OrderExecutionException(OrderExecutionException.Category.AMBIGUOUS);
        };
    }
    private static String response(String body) {
        try {
            if (body == null || body.length() > 64 * 1024) throw new IllegalArgumentException();
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.isObject() || !"success".equals(root.path("status").textValue())) throw new IllegalArgumentException();
            JsonNode id = root.path("data").path("order_id");
            if (!id.isTextual() || !id.textValue().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw new IllegalArgumentException();
            return id.textValue();
        } catch (Exception malformed) { throw new OrderExecutionException(OrderExecutionException.Category.MALFORMED_RESPONSE); }
    }
    private static String wire(Object value) {
        return switch (value) {
            case OrderType type -> switch (type) { case MARKET -> "MARKET"; case LIMIT -> "LIMIT"; case STOP_LIMIT -> "SL"; case STOP_MARKET -> "SL-M"; };
            case OrderProduct product -> switch (product) { case DELIVERY -> "CNC"; case INTRADAY -> "MIS"; case CARRY_FORWARD -> "NRML"; case MARGIN_FUNDING -> "MTF"; };
            case OrderValidity validity -> switch (validity) { case DAY -> "DAY"; case IMMEDIATE_OR_CANCEL -> "IOC"; case TIME_TO_LIVE -> "TTL"; };
            default -> throw new IllegalArgumentException();
        };
    }
}
