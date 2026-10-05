package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.tngtech.archunit.core.importer.*;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class ConservativeValuationArchitectureTest {
    @Test void accountCapacityAndRevalidationCanOnlyUseReadPorts() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().haveSimpleName("CashAccountCapacity").should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..application..", "org.springframework..", "java.net..", "java.sql..")
                .check(classes);
        noClasses().that().haveSimpleName("CurrentAccountExecutionChecks").should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "org.springframework..", "java.net..", "java.sql..")
                .check(classes);
        var checks=classes.get("com.kitehybrid.platform.order.application.CurrentAccountExecutionChecks");
        for (var dependency:checks.getDirectDependenciesFromSelf())
            assertFalse(Set.of("OrderExecutionGateway","OrderApplicationService","OrderRepository","RiskDecisionStore",
                    "RuntimeExecutionArming","RuntimeTradingHalt").contains(dependency.getTargetClass().getSimpleName()),dependency.toString());
        var reads=new java.util.HashSet<String>();
        for(var call:checks.getMethodCallsFromSelf()) if(call.getTargetOwner().getPackageName().startsWith("com.kitehybrid.platform.broker.application")) {
            assertEquals("com.kitehybrid.platform.broker.application.read",call.getTargetOwner().getPackageName());
            reads.add(call.getTarget().getName());
        }
        assertEquals(Set.of("positions","holdings","margins","orders"),reads);
        for(var name:Set.of("com.kitehybrid.platform.risk.domain.CashOrderRiskRules",checks.getName()))
            assertTrue(classes.get(name).getMethodCallsFromSelf().stream().anyMatch(call->call.getTargetOwner().getSimpleName().equals("CashAccountCapacity")
                    && call.getTarget().getName().equals("check")));
    }
    @Test void candidateSizingCannotCreateOrdersOrReachInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().haveSimpleName("ConservativeOrderQuantitySizer").should().dependOnClassesThat()
                .resideInAnyPackage("..broker..", "..application..", "..infrastructure..",
                        "org.springframework..", "java.net..", "java.sql..", "java.io..").check(classes);
        noClasses().that().haveNameMatching(".*FirstLiveCandidatePlanner.*").should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "org.springframework..", "java.net..", "java.sql..", "java.io..")
                .check(classes);
        for (var type : classes) if (type.getName().contains("FirstLiveCandidatePlanner")) {
            for (var dependency : type.getDirectDependenciesFromSelf())
                assertFalse(Set.of("OrderId", "OrderRecord", "PlaceOrder", "BrokerCorrelationId", "OrderRepository",
                        "RiskDecisionStore", "RuntimeExecutionArming", "RuntimeTradingHalt", "OrderApplicationService")
                        .contains(dependency.getTargetClass().getSimpleName()), dependency.toString());
        }
    }
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
