package com.kitehybrid.platform.order.application;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.Instant;
public record ExecutionAuthorizationDecision(boolean allowed, ExecutionDenialReason reason, Instant evaluatedAt, OrderId orderId, long orderVersion, String policyVersion) {
 public ExecutionAuthorizationDecision { if (reason == null || evaluatedAt == null || orderId == null || policyVersion == null) throw new NullPointerException(); if (allowed != (reason == ExecutionDenialReason.NONE)) throw new IllegalArgumentException("Inconsistent execution authorization"); }
}
