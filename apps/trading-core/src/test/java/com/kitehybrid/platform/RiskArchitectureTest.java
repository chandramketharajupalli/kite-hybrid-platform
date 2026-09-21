package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class RiskArchitectureTest {
    @Test void riskApplicationAndDomainDoNotDependOnKiteInfrastructureOrExecution() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.risk");
        noClasses().that().resideInAnyPackage("..risk.domain..", "..risk.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..")
                .check(classes);
    }
}
