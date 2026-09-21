package com.kitehybrid.platform.order.domain.command;

/** Broker-independent order command. Broker tokens and wire names stay in infrastructure. */
public sealed interface OrderCommand permits PlaceOrder, ModifyOrder, CancelOrder {
    String idempotencyKey();
}
