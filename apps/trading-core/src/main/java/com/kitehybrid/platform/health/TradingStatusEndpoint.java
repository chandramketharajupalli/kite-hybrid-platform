package com.kitehybrid.platform.health;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;

import com.kitehybrid.platform.config.TradingProperties;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Separate from application health; not exposed over HTTP by default. */
@Component
@Endpoint(id = "tradingstatus")
public class TradingStatusEndpoint {
    private final TradingProperties properties;
    private final RuntimeTradingHalt halt;
    public TradingStatusEndpoint(TradingProperties properties, RuntimeTradingHalt halt) { this.properties = properties; this.halt = halt; }
    @ReadOperation public Map<String, Object> status() {
        return Map.of("ready", false, "mode", properties.mode().name(),
                "emergencyStop", halt.getAsBoolean(),
                "runtimeHalt", halt.status().runtimeState().name(),
                "reason", "PHASE_1_EXECUTION_UNAVAILABLE");
    }
}
