package com.kitehybrid.platform;

import com.kitehybrid.platform.bootstrap.OperatorConsoleApplication;
import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.kitehybrid.platform.operator.console.TrustedOperatorConsole;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustedOperatorConsoleTest {
    @ParameterizedTest @ValueSource(strings={"execute", "execute-all", "execute-latest", "execute INFY", "execute synthetic-broker-1",
            "execute *", "execute 00000000-0000-0000-0000-000000000001 --force", "arm 00000000-0000-0000-0000-000000000001 0s",
            "arm 00000000-0000-0000-0000-000000000001 -1s", "execute 00000000-0000-0000-0000-000000000001 5", "--token secret", "status; execute *", " status"})
    void exactParserCannotReachExecution(String command) {
        var operator=mock(OperatorExecutionService.class); var output=new ArrayList<String>();
        var lines=new ArrayDeque<>(List.of(command));
        new TrustedOperatorConsole(operator,Optional.empty()).run(lines::poll,output::add);
        assertTrue(mockingDetails(operator).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("disarm")));
        assertTrue(output.contains("DENIED INVALID_COMMAND_OR_EVIDENCE"));
        assertTrue(output.stream().noneMatch(s->s.contains("secret")));
    }
    @Test void redirectedInputAndUnknownLauncherFlagsNeverBootAnApplication() {
        assertFalse(OperatorConsoleApplication.permitted(new String[]{"--interactive-operator"},false));
        assertFalse(OperatorConsoleApplication.permitted(new String[]{},true));
        assertFalse(OperatorConsoleApplication.permitted(new String[]{"--interactive-operator","--execute"},true));
        assertTrue(OperatorConsoleApplication.permitted(new String[]{"--interactive-operator"},true));
    }
    @Test void eofOrInputFailureAlwaysDisarmsAndNeverPrintsExceptionPayload() {
        var operator=mock(OperatorExecutionService.class); var output=new ArrayList<String>();
        new TrustedOperatorConsole(operator,Optional.empty()).run(()->null,output::add);
        verify(operator).disarm();
        assertThrows(IllegalStateException.class,()->new TrustedOperatorConsole(operator,Optional.empty())
                .run(()->{ throw new IllegalStateException("sensitive"); },output::add));
        verify(operator,times(2)).disarm(); assertTrue(output.stream().noneMatch(s->s.contains("sensitive")));
    }
}
