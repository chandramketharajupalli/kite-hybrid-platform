package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.bootstrap.OperatorConsoleApplication;
import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import java.io.Console;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated copy of real launcher bytecode: only System.console's result is substituted, in tests. */
public final class RehearsalTerminal {
    private static final ThreadLocal<Console> TERMINAL = new ThreadLocal<>();
    private RehearsalTerminal() {}
    public static Console controllingConsole() { return TERMINAL.get(); }
    static void run(Supplier<ConfigurableApplicationContext> boot, Supplier<String> input,
                    Consumer<String> output, boolean terminalPresent, String... args) throws Exception {
        var console=mock(Console.class);
        when(console.readLine()).thenAnswer(call->input.get());
        doAnswer(call->{ output.accept(((Object[])call.getRawArguments()[1])[0].toString()); return console; })
                .when(console).printf(eq("%s%n"),any(Object[].class));
        if (terminalPresent) TERMINAL.set(console);
        // A child loader leaves the real production class and controlling-terminal guard untouched.
        try (var spring=mockStatic(SpringApplication.class)) {
            spring.when(()->SpringApplication.run(TradingCoreApplication.class)).thenAnswer(call->boot.get());
            var launcher=new ByteBuddy().redefine(OperatorConsoleApplication.class)
                    .visit(MemberSubstitution.strict().method(named("console").and(isDeclaredBy(System.class)))
                            .replaceWith(RehearsalTerminal.class.getMethod("controllingConsole")).on(named("main")))
                    .make().load(OperatorConsoleApplication.class.getClassLoader(),ClassLoadingStrategy.Default.CHILD_FIRST).getLoaded();
            try { launcher.getMethod("main",String[].class).invoke(null,(Object)args); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException cause) throw cause;
                throw failure;
            }
            if (OperatorConsoleApplication.permitted(args,terminalPresent))
                spring.verify(()->SpringApplication.run(TradingCoreApplication.class));
            spring.verifyNoMoreInteractions(); // In particular: no execution flags/configuration passed to Boot.
        } finally { TERMINAL.remove(); }
    }
}
