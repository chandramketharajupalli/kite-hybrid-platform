package com.kitehybrid.platform.bootstrap;

import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.kitehybrid.platform.operator.console.TrustedOperatorConsole;
import com.kitehybrid.platform.reconciliation.application.OrderReconciliationService;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.boot.SpringApplication;

/** Alternate, explicit terminal entrypoint. Normal TradingCoreApplication never invokes it. */
public final class OperatorConsoleApplication {
    private OperatorConsoleApplication() {}
    public static boolean permitted(String[] args, boolean controllingTerminal) {
        return controllingTerminal && Arrays.equals(args,new String[]{"--interactive-operator"});
    }
    public static void main(String[] args) {
        var console=System.console();
        if (!permitted(args,console != null)) throw new IllegalStateException("Explicit interactive operator terminal required");
        // No execution configuration override: all normal fail-closed defaults still apply.
        try (var context=SpringApplication.run(TradingCoreApplication.class)) {
            var host=new TrustedOperatorConsole(context.getBean(OperatorExecutionService.class),
                    Optional.ofNullable(context.getBeanProvider(OrderReconciliationService.class).getIfAvailable()));
            host.run(console::readLine, message -> console.printf("%s%n",message));
        } catch (RuntimeException unavailable) {
            // Never expose provider exception text (which can contain credentials or raw responses).
            console.printf("%s%n","OPERATOR_HOST_FAILED_DISARMED");
        }
    }
}
