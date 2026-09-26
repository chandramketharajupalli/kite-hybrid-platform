package com.kitehybrid.platform;

import com.kitehybrid.platform.instrument.application.UniverseValidationService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class UniverseValidationArchitectureTest {
    @Test void validationHasNoTradingOrMarketSideEffects() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform.instrument");
        noClasses().that().haveSimpleNameContaining("Universe")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..marketdata..", "..strategy..", "..risk..", "..order..", "..broker.infrastructure..")
                .check(classes);
    }
}
