package com.kitehybrid.platform;

import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class OperatorControlArchitectureTest {
    @Test void normalBootstrapCannotStartConsoleAndLauncherCannotBypassOperator() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().haveSimpleName("TradingCoreApplication").should().dependOnClassesThat()
                .resideInAnyPackage("..operator..", "..order.application..").check(classes);
        noClasses().that().haveSimpleName("OperatorConsoleApplication").should().dependOnClassesThat()
                .resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..", "..order.application..").check(classes);
        for (var type : classes) for (var call : type.getConstructorCallsFromSelf()) {
            if (call.getTargetOwner().getSimpleName().equals("TrustedOperatorConsole"))
                org.junit.jupiter.api.Assertions.assertEquals("com.kitehybrid.platform.bootstrap.OperatorConsoleApplication",type.getName());
        }
        var launcher=classes.get("com.kitehybrid.platform.bootstrap.OperatorConsoleApplication");
        org.junit.jupiter.api.Assertions.assertFalse(launcher.isAnnotatedWith(org.springframework.stereotype.Component.class));
        org.junit.jupiter.api.Assertions.assertFalse(launcher.isAnnotatedWith(org.springframework.context.annotation.Configuration.class));
    }
    @Test void preflightCallGraphCannotReachAuthorizationSubmissionOrBrokerTransport() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        var pending = new java.util.ArrayDeque<com.tngtech.archunit.core.domain.JavaMethod>();
        // Include concrete implementations behind preflight's interface/supplier boundaries.
        for (var root : java.util.Map.of(
                "com.kitehybrid.platform.operator.application.OperatorExecutionService", "preflight",
                "com.kitehybrid.platform.order.application.ExecutionSafetyPolicy", "inspect",
                "com.kitehybrid.platform.operator.application.LiveTestExecutionChecks", "prepare",
                "com.kitehybrid.platform.operator.infrastructure.PostgresOperationalReadiness", "inspect",
                "com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository", "find",
                "com.kitehybrid.platform.risk.infrastructure.PostgresRiskDecisionStore", "find",
                "com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore", "latest",
                "com.kitehybrid.platform.broker.infrastructure.kite.KiteSession", "executionIdentity").entrySet()) {
            classes.get(root.getKey()).getMethods().stream().filter(m -> m.getName().equals(root.getValue())).forEach(pending::add);
        }
        classes.get("com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository").getMethods().stream()
                .filter(m -> java.util.Set.of("hasDangerousUnresolvedOrders", "hasBlockingExposureExcept").contains(m.getName()))
                .forEach(pending::add);
        classes.get("com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataAdapter").getMethods().stream()
                .filter(m -> m.getName().equals("health")).forEach(pending::add);
        classes.get("com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry").getMethods().stream()
                .filter(m -> m.getName().equals("snapshot")).forEach(pending::add);
        var visited = new java.util.HashSet<com.tngtech.archunit.core.domain.JavaMethod>();
        while (!pending.isEmpty()) {
            var method = pending.remove();
            if (!visited.add(method)) continue;
            for (var call : method.getMethodCallsFromSelf()) {
                String owner = call.getTargetOwner().getName();
                String target = call.getTarget().getName();
                org.junit.jupiter.api.Assertions.assertFalse(owner.startsWith("com.kitehybrid.platform.") && java.util.Set.of("execute", "executeRiskApproved",
                        "beginSubmission", "attachBrokerOrderId", "transitionTo", "validateDispatch").contains(target), call.toString());
                org.junit.jupiter.api.Assertions.assertFalse(java.util.Set.of("OrderExecutionGateway", "KiteOrderAdapter",
                        "KiteRestTransport", "ExecutionAuthorizationAuditStore", "PostgresExecutionAuthorizationAuditStore")
                        .contains(call.getTargetOwner().getSimpleName()), call.toString());
                if (owner.startsWith("com.kitehybrid.platform.")) call.getTarget().resolveMember().ifPresent(pending::add);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(visited.size() > 20, "Traverse actual evidence implementations");
    }

    @Test void strategyRiskAndReconciliationCannotReachOperatorOrExecutionCapabilities() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        for (var type : new Class<?>[]{RuntimeExecutionArming.class, OrderExecutionGateway.class, OperatorExecutionService.class}) {
            noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..", "..marketdata..")
                    .should().dependOnClassesThat().areAssignableTo(type).check(classes);
        }
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..", "..marketdata..")
                .should().dependOnClassesThat().resideInAnyPackage("..operator..").check(classes);
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..", "..marketdata..")
                .should().dependOnClassesThat().haveSimpleName("KiteOrderAdapter").check(classes);
        noClasses().that().resideInAnyPackage("..strategy..", "..risk..", "..reconciliation..", "..marketdata..")
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
                    org.junit.jupiter.api.Assertions.assertEquals("com.kitehybrid.platform.operator.console.TrustedOperatorConsole",
                            type.getName(), "Only the reviewed interactive host may invoke operator mutation: " + call);
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

    @Test void onlyReviewedExecutionEdgesAndExactOrderApiExist() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.kitehybrid.platform");
        for (var type : classes) for (var call : type.getMethodCallsFromSelf()) {
            String target=call.getTarget().getName();
            if (call.getTargetOwner().isEquivalentTo(OrderApplicationService.class) && target.equals("executeRiskApproved"))
                org.junit.jupiter.api.Assertions.assertTrue(type.isEquivalentTo(OperatorExecutionService.class),call.toString());
            if (call.getTargetOwner().isEquivalentTo(OrderExecutionGateway.class) && java.util.Set.of("place","modify","cancel").contains(target))
                org.junit.jupiter.api.Assertions.assertTrue(type.isEquivalentTo(OrderApplicationService.class),call.toString());
            if (call.getTargetOwner().getSimpleName().equals("TrustedOperatorConsole") && target.equals("run"))
                org.junit.jupiter.api.Assertions.assertEquals("com.kitehybrid.platform.bootstrap.OperatorConsoleApplication",type.getName(),call.toString());
            if (call.getTargetOwner().isEquivalentTo(RuntimeExecutionArming.class) && java.util.Set.of("arm","claim","complete").contains(target))
                org.junit.jupiter.api.Assertions.assertTrue(type.isEquivalentTo(OperatorExecutionService.class),call.toString());
        }
        noClasses().that().resideInAnyPackage("..operator.console..").should().dependOnClassesThat()
                .resideInAnyPackage("..broker.infrastructure..", "..order.infrastructure..", "org.springframework.web..", "org.springframework.context.event..").check(classes);
        for (var runner : new Class<?>[]{org.springframework.boot.ApplicationRunner.class,org.springframework.boot.CommandLineRunner.class})
            noClasses().that().resideInAnyPackage("..operator..").should().dependOnClassesThat().areAssignableTo(runner).check(classes);
        var execute = java.util.Arrays.stream(OperatorExecutionService.class.getDeclaredMethods()).filter(m -> m.getName().equals("execute")).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1,execute.size());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new Class<?>[]{com.kitehybrid.platform.shared.domain.Identifiers.OrderId.class},execute.getFirst().getParameterTypes());
        for (var method : classes.get(OperatorExecutionService.class).getMethods())
            org.junit.jupiter.api.Assertions.assertFalse(method.isAnnotatedWith(org.springframework.context.event.EventListener.class)
                    || method.isAnnotatedWith(org.springframework.scheduling.annotation.Scheduled.class));
    }
}
