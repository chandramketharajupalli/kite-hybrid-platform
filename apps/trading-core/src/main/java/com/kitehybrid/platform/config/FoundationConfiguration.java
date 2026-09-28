package com.kitehybrid.platform.config;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;

import com.kitehybrid.platform.risk.domain.*;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FoundationConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean RiskEngine riskEngine(RuntimeTradingHalt halt, Clock clock) {
        return new RiskEngine(List.of(
                new EmergencyStopRiskRule(halt),
                new PositiveReferencePriceRiskRule()), clock);
    }
}
