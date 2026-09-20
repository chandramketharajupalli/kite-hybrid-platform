package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class TradingReadArchitectureTest {
    @Test
    void readPortsAndModelsStayIndependentOfKiteTransportAndTradingDecisions() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAnyPackage("..broker.application.read..", "..broker.domain.read..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework..",
                        "com.fasterxml.jackson..", "java.net..", "com.zerodhatech..", "com.rainmatter..",
                        "..order..", "..position..", "..risk..", "..execution..", "..strategy..")
                .check(classes);
        noClasses().that().haveSimpleNameStartingWith("KiteTradingRead")
                .should().dependOnClassesThat().resideInAnyPackage("..order..", "..position..", "..risk..",
                        "..execution..", "..strategy..", "org.springframework.jdbc..", "org.springframework.data.redis..",
                        "org.springframework.scheduling..", "java.util.concurrent..")
                .check(classes);
    }

    @Test
    void allFivePortsExposeExactlyOneParameterlessReadAndImmutableModelTypes() {
        Map<Class<?>, String> ports = Map.of(BrokerOrdersProvider.class, "orders", BrokerTradesProvider.class, "trades",
                BrokerPositionsProvider.class, "positions", BrokerHoldingsProvider.class, "holdings",
                BrokerMarginsProvider.class, "margins");
        ports.forEach((port, methodName) -> {
            assertThat(port.isInterface()).isTrue();
            assertThat(port.getDeclaredMethods()).hasSize(1);
            Method method = port.getDeclaredMethods()[0];
            assertThat(method.getName()).isEqualTo(methodName);
            assertThat(method.getParameterCount()).isZero();
            assertThat(method.getGenericReturnType().getTypeName()).contains("com.kitehybrid.platform.broker.domain.read.");
        });
        List.of(BrokerOrder.class, BrokerTrade.class, BrokerPositions.class, BrokerPosition.class,
                BrokerHolding.class, BrokerMargins.class).forEach(model -> assertThat(model.isRecord()).isTrue());
    }

    @Test
    void developmentDiagnosticsRequireBothExplicitFlagsAndNeverEnableInProduction() throws Exception {
        Class<?> controller = Class.forName(
                "com.kitehybrid.platform.broker.infrastructure.kite.KiteTradingReadDiagnosticController");
        Profile profile = controller.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        Profiles active = Profiles.of(profile.value());
        assertThat(active.matches(Set.of("development")::contains)).isTrue();
        assertThat(active.matches(Set.of("production")::contains)).isFalse();
        assertThat(active.matches(Set.of("development", "production")::contains)).isFalse();
        assertThat(active.matches(Set.of("test")::contains)).isFalse();
        ConditionalOnProperty guard = controller.getAnnotation(ConditionalOnProperty.class);
        assertThat(guard).isNotNull();
        assertThat(guard.prefix()).isEqualTo("kite.trading-read");
        assertThat(guard.name()).containsExactlyInAnyOrder("enabled", "diagnostic-enabled");
        assertThat(guard.havingValue()).isEqualTo("true");
        assertThat(guard.matchIfMissing()).isFalse();
        assertThat(Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())))
                .containsExactlyInAnyOrder("/orders", "/trades", "/positions", "/holdings", "/margins");
        for (Method method : controller.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(PostMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(PutMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(PatchMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(DeleteMapping.class)).isFalse();
        }
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("kite.trading-read.enabled")).isEqualTo("${KITE_TRADING_READ_ENABLED:false}");
        assertThat(properties.getProperty("kite.trading-read.diagnostic-enabled"))
                .isEqualTo("${KITE_TRADING_READ_DIAGNOSTIC_ENABLED:false}");
        assertThat(properties.getProperty("server.address")).isEqualTo("${SERVER_ADDRESS:127.0.0.1}");
    }
}
