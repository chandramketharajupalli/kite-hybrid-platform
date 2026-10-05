package com.kitehybrid.platform.operator.application;

import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;
import static com.kitehybrid.platform.order.application.ExecutionReadiness.Gate.*;

public final class LiveTestExecutionChecks implements AdditionalExecutionChecks {
    private final boolean operatorEnabled;
    private final LiveTestProperties p;
    private final RuntimeExecutionArming arm;
    private final OperationalReadiness operational;
    private final Clock clock;
    public LiveTestExecutionChecks(boolean operatorEnabled, LiveTestProperties p,
            RuntimeExecutionArming arm, OperationalReadiness operational, Clock clock) {
        this.operatorEnabled=operatorEnabled; this.p=p; this.arm=arm; this.operational=operational; this.clock=clock;
    }
    @Override public java.util.function.Function<Optional<ConservativeOrderValuation>, Map<ExecutionReadiness.Gate, ExecutionDenialReason>> prepare(OrderRecord order) {
        var evidence = operational.inspect();
        return valuation -> evaluate(order, valuation, evidence);
    }
    private Map<ExecutionReadiness.Gate, ExecutionDenialReason> evaluate(OrderRecord order, Optional<ConservativeOrderValuation> valuation, OperationalReadiness.Evidence evidence) {
        var result = new LinkedHashMap<ExecutionReadiness.Gate, ExecutionDenialReason>();
        var armed = arm.status(clock.instant());
        result.put(SESSION_BOUND, arm.bindingReason(order, clock.instant()));
        result.put(OPERATOR_CONTROL_ENABLED, operatorEnabled ? NONE : OPERATOR_CONTROL_DISABLED);
        result.put(LIVE_TEST_MODE_ENABLED, p.enabled() ? NONE : LIVE_TEST_DISABLED);
        result.put(LIVE_TEST_INSTRUMENT_ALLOWED, p.allowedInstruments().contains(order.command().instrumentId()) ? NONE : LIVE_TEST_INSTRUMENT_DENIED);
        result.put(LIVE_TEST_QUANTITY_WITHIN_CAP, p.maxQuantity() > 0 && order.command().quantity() <= p.maxQuantity() ? NONE : LIVE_TEST_QUANTITY_CAP);
        result.put(LIVE_TEST_NOTIONAL_WITHIN_CAP, valuation.filter(v -> v.within(p.maxNotional())).isPresent() ? NONE : LIVE_TEST_NOTIONAL_CAP);
        result.put(LIVE_TEST_ARM_DURATION_VALID, !p.armMaxDuration().isZero() && (!armed.armed()
                || Duration.between(armed.armedAt(), armed.expiresAt()).compareTo(p.armMaxDuration()) <= 0) ? NONE : ARM_DURATION_INVALID);
        result.put(DATABASE_READY, evidence.databaseReady() ? NONE : DATABASE_NOT_READY);
        result.put(RECONCILIATION_STORE_HEALTHY, evidence.reconciliationStoreHealthy() ? NONE : RECONCILIATION_STORE_UNAVAILABLE);
        result.put(RECONCILIATION_CONFLICT_CLEAR, evidence.reconciliationConflictClear() ? NONE : RECONCILIATION_CONFLICT);
        result.put(TRADING_READ_AVAILABLE, evidence.tradingReadAvailable() ? NONE : TRADING_READ_UNAVAILABLE);
        return result;
    }
}
