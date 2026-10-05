package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class StrategyArchitectureTest {
    @Test void strategyMayProposeOrdersButCannotAuthorizeOrDispatchThem() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.strategy");
        for (var type : classes) {
            if (!type.getPackageName().contains(".application") && !type.getPackageName().contains(".domain")) continue;
            for (var dependency : type.getDirectDependenciesFromSelf())
                assertFalse(java.util.Set.of("OrderExecutionGateway", "RuntimeExecutionArming", "RuntimeTradingHalt",
                        "OperatorExecutionService", "ExecutionSafetyPolicy").contains(dependency.getTargetClass().getSimpleName()),
                        dependency.toString());
            for (var call : type.getMethodCallsFromSelf())
                if (call.getTargetOwner().getName().equals("com.kitehybrid.platform.order.application.OrderApplicationService"))
                    assertEquals("place", call.getTarget().getName(), "Strategy boundary permits proposals only: " + call);
        }
    }

    @Test void strategyApplicationAndDomainCannotReachBrokerOrExecutionInfrastructure() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.strategy");
        noClasses().that().resideInAnyPackage("..strategy.domain..", "..strategy.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..", "..reconciliation.infrastructure..")
                .check(classes);
    }
}
