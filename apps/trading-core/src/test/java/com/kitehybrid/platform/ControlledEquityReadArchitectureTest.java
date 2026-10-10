package com.kitehybrid.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class ControlledEquityReadArchitectureTest {
    @Test void isolatedHarnessCannotReachExecutionAuthenticationLifecycleOrStartup() {
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.kitehybrid.platform");
        noClasses().that().haveNameMatching(".*KiteEquityRead.*").should().dependOnClassesThat()
                .resideInAnyPackage("..order..","..operator..","..risk..","..strategy..","com.kitehybrid.platform.config..","..bootstrap..",
                        "org.springframework.boot..","org.springframework.context..","org.springframework.scheduling..")
                .check(classes);
        for(var type:classes)if(type.getSimpleName().startsWith("KiteEquityRead")) {
            for(var dependency:type.getDirectDependenciesFromSelf())
                assertThat(Set.of("KiteAuthenticationUseCase","KiteAccessTokenStore","PostgresKiteAccessTokenStore",
                        "KiteAuthenticationAdapter","KiteMarketDataGateway","OrderExecutionGateway","DataSource"))
                        .doesNotContain(dependency.getTargetClass().getSimpleName());
            for(var call:type.getMethodCallsFromSelf()) {
                assertThat(Set.of("resume","arm","claim","install","profileValidated","restore","reset","save",
                        "postRegularOrder","putRegularOrder","deleteRegularOrder","calculateOrderMargin",
                        "executeUpdate","executeBatch","executeLargeUpdate","setReadOnly","commit","rollback"))
                        .doesNotContain(call.getTarget().getName());
                if(call.getTargetOwner().getSimpleName().equals("KiteRestTransport"))
                    assertThat(call.getTarget().getName()).isEqualTo("controlledEquity");
                if(call.getTargetOwner().getName().startsWith("java.sql."))
                    assertThat(call.getTarget().getName()).isNotEqualTo("execute");
            }
        }
    }
}
