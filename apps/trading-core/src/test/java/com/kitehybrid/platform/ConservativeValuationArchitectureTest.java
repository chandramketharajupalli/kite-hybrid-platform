package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.tngtech.archunit.core.importer.*;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class ConservativeValuationArchitectureTest {
    @Test void valuationIsPureAndRiskAndExecutionShareItsImplementation() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().haveSimpleName("ConservativeOrderValuation").should().dependOnClassesThat()
                .resideInAnyPackage("..broker..", "..application..", "..infrastructure..", "..risk..",
                        "org.springframework..", "java.net..", "java.sql..", "java.io..").check(classes);
        for (var name : Set.of("com.kitehybrid.platform.risk.domain.CashOrderRiskRules",
                "com.kitehybrid.platform.order.application.ExecutionSafetyPolicy")) {
            assertTrue(classes.get(name).getMethodCallsFromSelf().stream().anyMatch(call ->
                    call.getTargetOwner().isEquivalentTo(ConservativeOrderValuation.class)
                            && call.getTarget().getName().equals("evaluate")), name);
        }
        for (var type : classes) {
            if (Set.of("ExecutionSafetyPolicy", "LiveTestExecutionChecks", "KiteOrderAdapter", "TrustedOperatorConsole")
                    .contains(type.getSimpleName())) {
                for (var call : type.getMethodCallsFromSelf()) {
                    assertFalse(call.getTargetOwner().isEquivalentTo(BigDecimal.class)
                            && Set.of("multiply", "divide", "setScale", "doubleValue", "floatValue").contains(call.getTarget().getName()),
                            "No independent order valuation: " + call);
                }
            }
        }
        assertTrue(classes.get("com.kitehybrid.platform.operator.application.LiveTestExecutionChecks")
                .getMethodCallsFromSelf().stream().anyMatch(call -> call.getTargetOwner().isEquivalentTo(ConservativeOrderValuation.class)
                        && call.getTarget().getName().equals("within")));
    }
}
