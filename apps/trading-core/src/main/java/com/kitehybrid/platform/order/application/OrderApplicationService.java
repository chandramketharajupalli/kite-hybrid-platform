package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates and durably records commands; explicit risk-approved submission is separately gated. */
public final class OrderApplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderApplicationService.class);
    private final OrderRepository repository;
    private final OrderCommandValidator validator;
    private final OrderExecutionGateway gateway;
    private final OrderExecutionProperties execution;
    private final Clock clock;
    private final MeterRegistry metrics;
    public OrderApplicationService(OrderRepository repository, OrderCommandValidator validator,
                                   OrderExecutionGateway gateway, OrderExecutionProperties execution,
                                   Clock clock, MeterRegistry metrics) {
        this.repository = Objects.requireNonNull(repository); this.validator = Objects.requireNonNull(validator);
        this.gateway = Objects.requireNonNull(gateway); this.execution = Objects.requireNonNull(execution);
        this.clock = Objects.requireNonNull(clock); this.metrics = Objects.requireNonNull(metrics);
    }
    public OrderRecord place(PlaceOrder command) {
        validator.validate(command); Instant now = clock.instant();
        OrderId proposed = new OrderId(UUID.randomUUID());
        var record = new OrderRecord(proposed, command, OrderState.CREATED, Optional.empty(),
                Optional.of(com.kitehybrid.platform.shared.domain.BrokerCorrelationId.generate()), Optional.empty(), now, now, 0)
                .transitionTo(OrderState.VALIDATED, now);
        var claim = repository.createIfAbsent(record, fingerprint(command));
        if (claim == OrderRepository.IdempotencyClaim.CONFLICT) throw new OrderCommandValidationException("IDEMPOTENCY_CONFLICT");
        if (claim == OrderRepository.IdempotencyClaim.EXISTING)
            return repository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new OrderExecutionException(OrderExecutionException.Category.TRANSPORT));
        transitionMetric("CREATED", "VALIDATED");
        return record;
    }
    /** Executes an order only after the risk subsystem has durably persisted RISK_APPROVED. */
    public OrderRecord executeRiskApproved(OrderId id) {
        if (!execution.enabled()) throw new OrderExecutionException(OrderExecutionException.Category.DISABLED);
        var current = require(id);
        if (current.state() != OrderState.RISK_APPROVED) throw new OrderCommandValidationException("ORDER_NOT_RISK_APPROVED");
        var submitting = current.transitionTo(OrderState.SUBMITTING, clock.instant());
        if (!repository.compareAndSet(current, submitting)) throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
        try {
            String brokerId = gateway.place(submitting);
            var attached = submitting.withBrokerOrderId(brokerId, clock.instant());
            var submitted = attached.transitionTo(OrderState.SUBMITTED, clock.instant());
            if (!repository.attachBrokerOrderId(submitting, submitted))
                throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
            metrics.counter("order.execution.attempts", "operation", "place", "result", "success").increment();
            return submitted;
        } catch (OrderExecutionException failure) {
            metrics.counter("order.execution.failures", "operation", "place", "category", failure.category().name()).increment();
            if (failure.category() == OrderExecutionException.Category.AMBIGUOUS
                    || failure.category() == OrderExecutionException.Category.TRANSPORT) throw failure;
            var failed = submitting.failed(failure.category().name(), clock.instant());
            repository.compareAndSet(submitting, failed);
            throw failure;
        }
    }
    public OrderRecord modify(ModifyOrder command) {
        var current = require(command.orderId());
        var prior = repository.findByIdempotencyKey(command.idempotencyKey());
        if (prior.isPresent()) {
            if (!prior.get().id().equals(current.id())) throw new OrderCommandValidationException("IDEMPOTENCY_CONFLICT");
            return current;
        }
        validator.validate(command, current);
        if (claim(command, current.id()) == OrderRepository.IdempotencyClaim.EXISTING) return current;
        ensureEnabled(); gateway.modify(current, command); return current;
    }
    public OrderRecord cancel(CancelOrder command) {
        var current = require(command.orderId());
        var prior = repository.findByIdempotencyKey(command.idempotencyKey());
        if (prior.isPresent()) {
            if (!prior.get().id().equals(current.id())) throw new OrderCommandValidationException("IDEMPOTENCY_CONFLICT");
            return current;
        }
        validator.validate(command, current);
        var claim = claim(command, current.id());
        if (claim == OrderRepository.IdempotencyClaim.EXISTING) return current;
        ensureEnabled();
        var pending = current.transitionTo(OrderState.CANCEL_PENDING, clock.instant());
        if (!repository.compareAndSet(current, pending)) throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
        try {
            gateway.cancel(pending, command);
            var cancelled = pending.transitionTo(OrderState.CANCELLED, clock.instant());
            if (!repository.compareAndSet(pending, cancelled)) throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
            return cancelled;
        } catch (OrderExecutionException failure) {
            if (failure.category() == OrderExecutionException.Category.AMBIGUOUS
                    || failure.category() == OrderExecutionException.Category.TRANSPORT) throw failure;
            throw failure;
        }
    }
    private OrderRepository.IdempotencyClaim claim(OrderCommand command, OrderId orderId) {
        var claim = repository.claimIdempotency(command.idempotencyKey(), fingerprint(command), orderId);
        if (claim == OrderRepository.IdempotencyClaim.CONFLICT) throw new OrderCommandValidationException("IDEMPOTENCY_CONFLICT");
        return claim;
    }
    private void ensureEnabled() { if (!execution.enabled()) throw new OrderExecutionException(OrderExecutionException.Category.DISABLED); }
    private OrderRecord require(OrderId id) { return repository.find(id).orElseThrow(() -> new OrderCommandValidationException("ORDER_NOT_FOUND")); }
    private void transitionMetric(String from, String to) {
        metrics.counter("order.transitions", "from", from, "to", to).increment();
        LOG.info("Order transition from={} to={}", from, to);
    }
    private static String fingerprint(OrderCommand command) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(command.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
