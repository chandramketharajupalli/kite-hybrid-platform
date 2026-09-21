package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ReconciliationArchitectureTest {
    @Test void applicationAndDomainReconciliationDoNotDependOnKiteOrExecutionInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.reconciliation");
        noClasses().that().resideInAnyPackage("..reconciliation.application..", "..reconciliation.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..")
                .check(classes);
    }
}
