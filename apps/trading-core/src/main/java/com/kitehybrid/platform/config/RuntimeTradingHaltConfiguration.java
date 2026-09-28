package com.kitehybrid.platform.config;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeTradingHaltConfiguration {
    @Bean(destroyMethod = "halt") RuntimeTradingHalt runtimeTradingHalt(TradingProperties trading) {
        return new RuntimeTradingHalt(trading::emergencyStop);
    }
}
