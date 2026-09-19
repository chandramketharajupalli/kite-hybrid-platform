package com.kitehybrid.platform.health;

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
    public TradingStatusEndpoint(TradingProperties properties) { this.properties = properties; }
    @ReadOperation public Map<String, Object> status() {
        return Map.of("ready", false, "mode", properties.mode().name(),
                "emergencyStop", properties.emergencyStop(),
                "reason", "PHASE_1_EXECUTION_UNAVAILABLE");
    }
}
