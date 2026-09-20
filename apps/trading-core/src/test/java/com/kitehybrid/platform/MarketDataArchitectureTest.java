package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class MarketDataArchitectureTest {
    @Test void normalizedMarketDataAndPortsAreIndependentOfBrokerTransportAndTradingDecisions() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAnyPackage("..marketdata.domain..", "..marketdata.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "java.net.http..",
                        "org.springframework..", "com.zerodhatech..", "com.rainmatter..",
                        "..order..", "..risk..", "..strategy..", "..position..", "..execution..")
                .check(classes);
        noClasses().that().haveSimpleNameStartingWith("KiteMarketData")
                .or().haveSimpleName("JdkKiteWebSocketTransport")
                .should().dependOnClassesThat().resideInAnyPackage("..order..", "..risk..", "..strategy..",
                        "..position..", "..execution..", "org.springframework.jdbc..", "org.springframework.data.redis..")
                .check(classes);
    }
}
