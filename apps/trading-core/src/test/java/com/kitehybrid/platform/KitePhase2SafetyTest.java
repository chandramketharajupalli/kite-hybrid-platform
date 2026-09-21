package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.infrastructure.kite.KiteRestDiagnostic;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class KitePhase2SafetyTest {
    @Test void missingOrDisabledCredentialsMakeDiagnosticFailBeforeNetwork() {
        for (var environment : java.util.List.of(Map.<String, String>of(),
                Map.of("KITE_API_KEY", "dummykey", "KITE_ACCESS_TOKEN", "dummytoken", "KITE_REST_ENABLED", "false"))) {
            var buffer = new ByteArrayOutputStream();
            int code = KiteRestDiagnostic.run(new String[]{"profile"}, environment, new PrintStream(buffer));
            assertThat(code).isEqualTo(1);
            assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("CONFIGURATION").doesNotContain("dummytoken");
        }
    }
    @Test void diagnosticAcceptsOnlyTheTwoReadOnlyCommands() {
        var output = new ByteArrayOutputStream();
        assertThat(KiteRestDiagnostic.run(new String[]{"orders"}, Map.of(), new PrintStream(output))).isEqualTo(2);
    }
    @Test void domainAndApplicationCannotDependOnKiteOrHttpAndAdapterCannotReachExecution() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        noClasses().that().resideInAnyPackage("..domain..", "..application..").should()
                .dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework.web.client..",
                        "java.net.http..", "com.zerodhatech..").check(classes);
        // The Phase 5B gateway is the deliberate infrastructure bridge. Other Kite
        // infrastructure remains unable to reach the order command/application layer.
        noClasses().that(new DescribedPredicate<JavaClass>("Kite infrastructure outside the order bridge") {
                    @Override public boolean test(JavaClass type) {
                        return type.getPackageName().matches(".*broker\\.infrastructure\\.kite.*")
                                && !type.getName().startsWith("com.kitehybrid.platform.broker.infrastructure.kite.KiteOrderAdapter")
                                && !type.getName().startsWith("com.kitehybrid.platform.broker.infrastructure.kite.KiteOrderConfiguration");
                    }
                }).should()
                .dependOnClassesThat().resideInAnyPackage("..order..", "..execution..", "..risk..", "..position..").check(classes);
    }
}
