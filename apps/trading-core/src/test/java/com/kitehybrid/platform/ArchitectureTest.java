package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test void domainIsIndependentAndTradingCoreHasNoBrokerSdkDependency() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAPackage("..domain..").should()
                .dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework..",
                        "com.fasterxml.jackson..").check(classes);
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.zerodhatech..", "com.rainmatter..").check(classes);
    }
}
