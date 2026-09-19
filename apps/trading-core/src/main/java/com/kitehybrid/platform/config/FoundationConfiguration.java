package com.kitehybrid.platform.config;

import com.kitehybrid.platform.risk.domain.*;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FoundationConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean RiskEngine riskEngine(TradingProperties properties, Clock clock) {
        return new RiskEngine(List.of(
                new EmergencyStopRiskRule(properties::emergencyStop),
                new PositiveReferencePriceRiskRule()), clock);
    }
}
