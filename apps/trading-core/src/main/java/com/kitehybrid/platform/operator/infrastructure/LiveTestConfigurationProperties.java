package com.kitehybrid.platform.operator.infrastructure;

import com.kitehybrid.platform.operator.application.LiveTestProperties;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("kite.live-test")
public record LiveTestConfigurationProperties(@DefaultValue("false") boolean enabled,
        Set<UUID> allowedInstruments, @DefaultValue("0") long maxQuantity,
        @DefaultValue("0") BigDecimal maxNotional, @DefaultValue("0s") Duration armMaxDuration) {
    public LiveTestProperties domain() {
        return new LiveTestProperties(enabled, allowedInstruments == null ? Set.of() : allowedInstruments.stream()
                .map(InstrumentId::new).collect(Collectors.toSet()), maxQuantity, maxNotional, armMaxDuration);
    }
}
