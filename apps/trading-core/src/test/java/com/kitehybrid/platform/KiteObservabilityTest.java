package com.kitehybrid.platform;

import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.observability.KiteReadOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KiteObservabilityTest {
    @Test void countsSuccessAndFailureWithoutIdentityLabelsAndRetainsRegistryAfterFailure() {
        var registry = new InMemoryInstrumentRegistry();
        var instrument = Instrument.create(new BrokerInstrumentId("ZERODHA", "1"), "ABC", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
        var candidate = new java.util.concurrent.atomic.AtomicReference<>(List.of(instrument));
        var metrics = new SimpleMeterRegistry();
        try {
            var operations = new KiteReadOperations(
                    new ValidateBrokerProfileUseCase(() -> new BrokerProfile("ZERODHA", "DEMO", Set.of("NSE"))),
                    new RefreshInstrumentRegistryUseCase(candidate::get, registry, Clock.systemUTC()), registry, metrics);
            operations.validateProfile();
            operations.refreshInstruments();
            var snapshot = registry.snapshot();
            candidate.set(List.of(instrument, instrument));
            assertThatThrownBy(operations::refreshInstruments).isInstanceOf(BrokerReadException.class);
            assertThat(registry.snapshot()).isSameAs(snapshot);
            assertThat(metrics.get("instrument.registry.count").gauge().value()).isEqualTo(1);
            assertThat(metrics.get("kite.rest.operations").tags("operation", "profile", "result", "success")
                    .counter().count()).isEqualTo(1);
            assertThat(metrics.get("kite.rest.operations").tags("operation", "instruments", "result", "INVALID_RESPONSE")
                    .counter().count()).isEqualTo(1);
            metrics.getMeters().forEach(meter -> assertThat(meter.getId().getTags().toString())
                    .doesNotContain("DEMO", "ABC"));
        } finally {
            metrics.close();
        }
    }
}
