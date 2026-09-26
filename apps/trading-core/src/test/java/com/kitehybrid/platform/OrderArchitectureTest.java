package com.kitehybrid.platform;

import com.kitehybrid.platform.order.application.ExecutionSafetyPolicy;
import com.kitehybrid.platform.order.application.OrderApplicationService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderArchitectureTest {
    @Test
    void orderDomainAndApplicationDoNotDependOnBrokerOrSpringInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.order");
        noClasses().that().resideInAnyPackage("..order.domain..", "..order.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "org.springframework..")
                .check(classes);
    }

    @Test
    void productionExecutionServiceHasNoPolicyBypassConstructor() {
        var constructors = OrderApplicationService.class.getConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Arrays.stream(constructors[0].getParameterTypes())
                .anyMatch(type -> type == ExecutionSafetyPolicy.class));
    }
}
