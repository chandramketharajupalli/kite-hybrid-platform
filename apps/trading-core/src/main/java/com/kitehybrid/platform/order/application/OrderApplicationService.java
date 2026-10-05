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
    private final Clock clock;
    private final MeterRegistry metrics;
    private final ExecutionSafetyPolicy safety;
    public OrderApplicationService(OrderRepository repository, OrderCommandValidator validator,
                                   OrderExecutionGateway gateway, OrderExecutionProperties execution,
                                   Clock clock, MeterRegistry metrics, ExecutionSafetyPolicy safety) {
        this.repository = Objects.requireNonNull(repository); this.validator = Objects.requireNonNull(validator);
        this.gateway = Objects.requireNonNull(gateway); Objects.requireNonNull(execution);
        this.clock = Objects.requireNonNull(clock); this.metrics = Objects.requireNonNull(metrics); this.safety = Objects.requireNonNull(safety);
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
        repository.requireIndependentExecution();
        var current = require(id);
        var decision = safety.evaluate(current);
        if (!decision.allowed()) throw new OrderCommandValidationException(decision.reason().name());
        var submitting = current.transitionTo(OrderState.SUBMITTING, clock.instant());
        boolean admitted;
        try { admitted = repository.beginSubmission(current, submitting, () -> safety.validateAdmission(current)); }
        catch (OrderCommandValidationException denied) {
            // Record only after the admission transaction has rolled back; halt itself never writes audit.
            // Admission now repeats the complete policy, so retain every bounded denial reason.
            ExecutionDenialReason reason;
            try { reason = ExecutionDenialReason.valueOf(denied.getMessage()); }
            catch (IllegalArgumentException | NullPointerException unrecognized) {
                reason = ExecutionDenialReason.EVIDENCE_UNAVAILABLE;
            }
            if (reason == ExecutionDenialReason.NONE) reason = ExecutionDenialReason.EVIDENCE_UNAVAILABLE;
            safety.deny(current, reason);
            throw denied;
        }
        if (!admitted) {
            var reason = repository.find(id).filter(current::equals).isPresent()
                    ? ExecutionDenialReason.RECONCILIATION_REQUIRED : ExecutionDenialReason.ORDER_VERSION_CHANGED;
            safety.deny(current, reason);
            throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
        }
        try {
            safety.validateDispatch(current, submitting);
            String brokerId = gateway.place(submitting, () -> safety.validateDispatch(current, submitting));
            var attached = submitting.withBrokerOrderId(brokerId, clock.instant());
            var submitted = attached.transitionTo(OrderState.SUBMITTED, clock.instant());
            if (!repository.attachBrokerOrderId(submitting, submitted))
                throw new OrderExecutionException(OrderExecutionException.Category.TRANSPORT);
            metrics.counter("order.execution.attempts", "operation", "place", "result", "success").increment();
            return submitted;
        } catch (OrderCommandValidationException denied) {
            // HALT never repairs a committed submission or makes it eligible for another attempt.
            // Keep the durable checkpoint for explicit inspection/reconciliation.
            if (!ExecutionDenialReason.EMERGENCY_STOP.name().equals(denied.getMessage()))
                repository.compareAndSet(submitting, submitting.failed("PRE_DISPATCH_DENIED", clock.instant()));
            throw denied;
        } catch (OrderExecutionException failure) {
            metrics.counter("order.execution.failures", "operation", "place", "category", failure.category().name()).increment();
            if (failure.category() == OrderExecutionException.Category.AMBIGUOUS
                    || failure.category() == OrderExecutionException.Category.MALFORMED_RESPONSE
                    || failure.category() == OrderExecutionException.Category.TRANSPORT) throw failure;
            var failed = submitting.failed(failure.category().name(), clock.instant());
            repository.compareAndSet(submitting, failed);
            throw failure;
        }
    }
    /** Command-specific risk, authorization and reconciliation are not implemented for mutations. */
    public OrderRecord modify(ModifyOrder command) {
        throw new OrderExecutionException(OrderExecutionException.Category.DISABLED);
    }
    public OrderRecord cancel(CancelOrder command) {
        throw new OrderExecutionException(OrderExecutionException.Category.DISABLED);
    }
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
