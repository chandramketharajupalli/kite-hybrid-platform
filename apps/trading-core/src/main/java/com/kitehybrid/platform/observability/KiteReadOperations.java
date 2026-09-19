package com.kitehybrid.platform.observability;

import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.INVALID_RESPONSE;

/** Small observable entry point; no automatic calls, per-instrument tags or raw exception logging. */
public final class KiteReadOperations {
    private static final Logger LOG = LoggerFactory.getLogger(KiteReadOperations.class);
    private final ValidateBrokerProfileUseCase profile;
    private final RefreshInstrumentRegistryUseCase refresh;
    private final MeterRegistry metrics;

    public KiteReadOperations(ValidateBrokerProfileUseCase profile, RefreshInstrumentRegistryUseCase refresh,
                              InstrumentRegistry registry, MeterRegistry metrics) {
        this.profile = profile;
        this.refresh = refresh;
        this.metrics = metrics;
        metrics.gauge("instrument.registry.count", registry, r -> r.snapshot().size());
        metrics.gauge("instrument.registry.version", registry, r -> r.snapshot().version());
    }
    public BrokerProfile validateProfile() { return observe("profile", profile::validate); }
    public RefreshInstrumentRegistryUseCase.RefreshResult refreshInstruments() {
        return observe("instruments", refresh::refresh);
    }
    private <T> T observe(String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(metrics);
        String result = "success";
        try {
            T value = action.get();
            LOG.info("Kite read operation={} result=success", operation);
            return value;
        } catch (BrokerReadException safe) {
            result = safe.category().name();
            LOG.warn("Kite read operation={} result={}", operation, result);
            throw safe;
        } catch (RuntimeException invalid) {
            result = INVALID_RESPONSE.name();
            LOG.warn("Kite read operation={} result={}", operation, result);
            throw new BrokerReadException(INVALID_RESPONSE);
        } finally {
            metrics.counter("kite.rest.operations", "operation", operation, "result", result).increment();
            sample.stop(metrics.timer("kite.rest.duration", "operation", operation, "result", result));
        }
    }
}
