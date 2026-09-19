package com.kitehybrid.platform.bootstrap;

import com.kitehybrid.platform.config.TradingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.kitehybrid.platform")
@EnableConfigurationProperties(TradingProperties.class)
public class TradingCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingCoreApplication.class, args);
    }
}
