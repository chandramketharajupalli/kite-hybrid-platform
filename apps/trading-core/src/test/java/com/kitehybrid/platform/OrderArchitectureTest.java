package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import org.junit.jupiter.api.Test;

class OrderArchitectureTest {
    @Test
    void orderDomainAndApplicationDoNotDependOnBrokerOrSpringInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.order");
        noClasses().that().resideInAnyPackage("..order.domain..", "..order.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "org.springframework..")
                .check(classes);
    }
}
