package com.kitehybrid.platform;

import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class OperatorControlArchitectureTest {
    @Test void strategyRiskAndReconciliationCannotReachOperatorOrExecutionCapabilities() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        for (var type : new Class<?>[]{RuntimeExecutionArming.class, OrderExecutionGateway.class, OperatorExecutionService.class}) {
            noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..")
                    .should().dependOnClassesThat().areAssignableTo(type).check(classes);
        }
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..")
                .should().dependOnClassesThat().resideInAnyPackage("..operator..").check(classes);
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..")
                .should().dependOnClassesThat().haveSimpleName("KiteOrderAdapter").check(classes);
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..")
                .should().callMethod(OrderApplicationService.class, "executeRiskApproved", com.kitehybrid.platform.shared.domain.Identifiers.OrderId.class)
                .check(classes);
    }
    @Test void operatorHasNoHttpListenerOrSchedulerAndPolicyHasNoGateway() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAnyPackage("..operator..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.scheduling..")
                .check(classes);
        noClasses().that().haveSimpleName("ExecutionSafetyPolicy").should().dependOnClassesThat()
                .areAssignableTo(OrderExecutionGateway.class).check(classes);
        for (var type : classes) {
            for (var call : type.getMethodCallsFromSelf()) {
                if (call.getTargetOwner().isEquivalentTo(OperatorExecutionService.class)
                        && java.util.Set.of("arm", "execute").contains(call.getTarget().getName())) {
                    org.junit.jupiter.api.Assertions.fail("No automatic operator mutation caller is allowed: " + call);
                }
            }
            for (var call : type.getConstructorCallsFromSelf()) {
                if (call.getTargetOwner().isEquivalentTo(ExecutionSafetyPolicy.class)
                        && !type.isEquivalentTo(ExecutionSafetyPolicy.class)) {
                    org.junit.jupiter.api.Assertions.assertTrue(call.getTarget().getRawParameterTypes().stream()
                            .anyMatch(p -> p.isEquivalentTo(AdditionalExecutionChecks.class)), call.toString());
                }
            }
        }
    }
}
