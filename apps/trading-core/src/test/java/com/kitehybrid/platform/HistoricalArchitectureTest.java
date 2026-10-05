package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalArchitectureTest {
    @Test void researchCannotReachTradingAndKiteAdapterHasOnlyHistoricalTransportCalls() {
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAPackage("..historical..").should().dependOnClassesThat()
                .resideInAnyPackage("..order..","..risk..","..strategy..","..operator..","..broker..").check(classes);
        noClasses().that().resideInAnyPackage("..historical.domain..","..historical.application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..","..infrastructure..").check(classes);
        for(var type:classes) if(type.getPackageName().contains(".historical.") || type.getSimpleName().startsWith("KiteHistorical")) {
            for(var dep:type.getDirectDependenciesFromSelf()) assertFalse(java.util.Set.of("RuntimeTradingHalt","RuntimeExecutionArming",
                    "OperatorExecutionService","OrderExecutionGateway","KiteOrderAdapter").contains(dep.getTargetClass().getSimpleName()),dep.toString());
            for(var call:type.getMethodCallsFromSelf()) if(call.getTargetOwner().getSimpleName().equals("KiteRestTransport"))
                assertTrue(java.util.Set.of("production","historicalMinute").contains(call.getTarget().getName()),call.toString());
        }
        var transport=classes.get("com.kitehybrid.platform.broker.infrastructure.kite.KiteRestTransport");
        for(var method:transport.getMethods()) if(java.util.Set.of("historicalMinute","readWithSession").contains(method.getName()))
            for(var call:method.getMethodCallsFromSelf()) assertFalse(java.util.Set.of("orderRequest","postRegularOrder","putRegularOrder","deleteRegularOrder").contains(call.getTarget().getName()));
        var exporter=classes.get("com.kitehybrid.platform.historical.infrastructure.HistoricalResearchExporter");
        for(var call:exporter.getMethodCallsFromSelf()) {
            assertNotEquals("HistoricalMarketDataProvider",call.getTargetOwner().getSimpleName(),call.toString());
            if(call.getTargetOwner().getSimpleName().equals("HistoricalBarRepository"))
                assertTrue(java.util.Set.of("replay","evidence").contains(call.getTarget().getName()),call.toString());
        }
    }
}
