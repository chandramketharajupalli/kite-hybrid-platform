package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.infrastructure.kite.KiteRestDiagnostic;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
        noClasses().that().resideInAPackage("..broker.infrastructure.kite..").should()
                .dependOnClassesThat().resideInAnyPackage("..order..", "..execution..", "..marketdata..").check(classes);
    }
}
