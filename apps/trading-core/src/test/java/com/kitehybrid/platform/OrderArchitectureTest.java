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
    void onlyExplicitApplicationBoundaryCallsExecutionGatewayAndNoProductionCallerAutoExecutes() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        for (var type : classes) {
            for (var call : type.getMethodCallsFromSelf()) {
                if (call.getTargetOwner().isAssignableTo(com.kitehybrid.platform.order.application.OrderExecutionGateway.class)
                        && java.util.Set.of("place", "modify", "cancel").contains(call.getTarget().getName())) {
                    assertEquals(OrderApplicationService.class.getName(), type.getName(), call.toString());
                    assertEquals("executeRiskApproved", call.getOrigin().getName(), call.toString());
                }
                if ((call.getTargetOwner().getName().equals("com.kitehybrid.platform.order.application.RuntimeExecutionArming")
                        && call.getTarget().getName().equals("arm")) || (call.getTargetOwner().isEquivalentTo(OrderApplicationService.class)
                        && call.getTarget().getName().equals("executeRiskApproved"))) {
                    assertEquals(com.kitehybrid.platform.operator.application.OperatorExecutionService.class.getName(), type.getName(), call.toString());
                    assertEquals(call.getTarget().getName().equals("arm") ? "arm" : "execute", call.getOrigin().getName());
                }
            }
        }
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..", "..bootstrap..")
                .should().dependOnClassesThat().areAssignableTo(com.kitehybrid.platform.order.application.OrderExecutionGateway.class)
                .check(classes);
        noClasses().that().areAssignableTo(ExecutionSafetyPolicy.class)
                .should().dependOnClassesThat().resideInAnyPackage("..broker..", "org.springframework..")
                .check(classes);
    }
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
