package com.kitehybrid.platform;

import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.shared.domain.TradingMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class SafetyConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TradingProperties.class)
    static class Binding {}
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Binding.class);

    @Test void missingConfigurationDefaultsSafe() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(TradingProperties.class);
            assertThat(config.mode()).isEqualTo(TradingMode.PAPER);
            assertThat(config.enableLiveTrading()).isFalse();
            assertThat(config.emergencyStop()).isTrue();
        });
    }
    @Test void liveFlagOrLiveModeOrInvalidModePreventsStartup() {
        for (String property : new String[]{"trading.enable-live-trading=true", "trading.mode=LIVE", "trading.mode=INVALID"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }
}
