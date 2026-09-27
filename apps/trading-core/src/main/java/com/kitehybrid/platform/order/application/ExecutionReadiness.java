package com.kitehybrid.platform.order.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded evidence only; no broker/account payloads or session identifiers. */
public record ExecutionReadiness(Map<Gate, ExecutionDenialReason> gates) {
    public enum Gate {
        EXECUTION_CAPABILITY_CONFIGURED, AUTHENTICATED, RUNTIME_ARMED, SESSION_BOUND,
        EMERGENCY_STOP_CLEAR, ORDER_RISK_APPROVED, RISK_DECISION_CURRENT,
        INSTRUMENT_ALLOWED, QUANTITY_WITHIN_CAP, CORRELATION_PRESENT,
        RECONCILIATION_CLEAR, MARKET_DATA_HEALTHY, MARKET_DATA_FRESH, NOTIONAL_WITHIN_CAP,
        OPERATOR_CONTROL_ENABLED, LIVE_TEST_MODE_ENABLED, LIVE_TEST_INSTRUMENT_ALLOWED,
        LIVE_TEST_QUANTITY_WITHIN_CAP, LIVE_TEST_NOTIONAL_WITHIN_CAP,
        LIVE_TEST_ARM_DURATION_VALID, DATABASE_READY, RECONCILIATION_STORE_HEALTHY,
        RECONCILIATION_CONFLICT_CLEAR, TRADING_READ_AVAILABLE
    }
    public ExecutionReadiness { gates = Collections.unmodifiableMap(new LinkedHashMap<>(gates)); }
    public boolean ready() { return !gates.isEmpty() && gates.values().stream().allMatch(r -> r == ExecutionDenialReason.NONE); }
    public String status() { return ready() ? "READY" : "NOT_READY"; }
    public ExecutionDenialReason reason() {
        return gates.values().stream().filter(r -> r != ExecutionDenialReason.NONE).findFirst().orElse(ExecutionDenialReason.NONE);
    }
}
