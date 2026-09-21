package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class StrategyArchitectureTest {
    @Test void strategyApplicationAndDomainCannotReachBrokerOrExecutionInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.strategy");
        noClasses().that().resideInAnyPackage("..strategy.domain..", "..strategy.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..", "..reconciliation.infrastructure..")
                .check(classes);
    }
}
