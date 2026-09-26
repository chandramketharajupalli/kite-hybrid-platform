package com.kitehybrid.platform.order.application;
public interface ExecutionAuthorizationAuditStore { void record(ExecutionAuthorizationDecision decision); ExecutionAuthorizationAuditStore NOOP = decision -> {}; }
