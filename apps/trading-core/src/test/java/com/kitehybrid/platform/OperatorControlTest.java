package com.kitehybrid.platform;

import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.operator.infrastructure.*;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.shared.application.ExecutionSession;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatorControlTest {
    private final Instant now = Instant.parse("2026-09-27T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ExecutionSession session = mock(ExecutionSession.class);
    private final RuntimeExecutionArming arm = new RuntimeExecutionArming(new SimpleMeterRegistry(), session::executionIdentity);
    private final OrderApplicationService application = mock(OrderApplicationService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final InstrumentId instrument = new InstrumentId(UUID.randomUUID());
    private OperatorExecutionService service(boolean enabled, boolean capability, boolean stop) {
        return new OperatorExecutionService(enabled, new OrderExecutionProperties(capability),
                new LiveTestProperties(true, Set.of(instrument), 1, BigDecimal.TEN, Duration.ofSeconds(30)),
                arm, session, () -> stop, orders, mock(ExecutionSafetyPolicy.class), application, clock);
    }
    private void authenticate() {
        when(session.enabled()).thenReturn(true);
        when(session.executionIdentity()).thenReturn(Optional.of(new UUID(0,1)));
    }
    @Test void armingOnlyReturnsSafeMetadataAndDisarmIsIdempotent() {
        authenticate();
        var operator = service(true, true, false);
        var result = operator.arm(Duration.ofSeconds(20));
        assertTrue(result.armed());
        assertEquals(now, result.armedAt());
        assertEquals(now.plusSeconds(20), result.expiresAt());
        assertEquals(ExecutionDenialReason.NONE, result.reason());
        assertEquals(operator.disarm(), operator.disarm());
        assertFalse(arm.armed(now));
        verifyNoInteractions(application, orders);
    }
    @Test void durationBoundariesFailClosedAndRevokeExistingArm() {
        authenticate(); var operator=service(true,true,false);
        for (var duration : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(31), Duration.ofDays(1))) {
            assertTrue(operator.arm(Duration.ofSeconds(30)).armed());
            assertFalse(operator.arm(duration).armed());
            assertFalse(arm.armed(now));
        }
        assertFalse(operator.arm(null).armed());
        verifyNoInteractions(application, orders);
    }
    @Test void independentEnablementCapabilityAuthenticationAndStopRequired() {
        authenticate();
        assertEquals(ExecutionDenialReason.OPERATOR_CONTROL_DISABLED, service(false,true,false).arm(Duration.ofSeconds(1)).reason());
        assertEquals(ExecutionDenialReason.EXECUTION_DISABLED, service(true,false,false).arm(Duration.ofSeconds(1)).reason());
        assertEquals(ExecutionDenialReason.EMERGENCY_STOP, service(true,true,true).arm(Duration.ofSeconds(1)).reason());
        when(session.executionIdentity()).thenReturn(Optional.empty());
        assertEquals(ExecutionDenialReason.AUTHENTICATION_UNAVAILABLE, service(true,true,false).arm(Duration.ofSeconds(1)).reason());
        verifyNoInteractions(application, orders);
    }
    @Test void typedDefaultsDenyWithoutSelectingAnInstrument() {
        new ApplicationContextRunner().withUserConfiguration(OperatorControlConfiguration.class)
                .withPropertyValues("spring.profiles.active=test").run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.getBean(OperatorControlConfigurationProperties.class).enabled());
                    var live=context.getBean(LiveTestProperties.class);
                    assertFalse(live.configured()); assertFalse(live.enabled());
                    assertEquals(Set.of(), live.allowedInstruments());
                    assertEquals(BigDecimal.ZERO, live.maxNotional());
                    assertEquals(0, live.maxQuantity());
                });
    }
    @ParameterizedTest
    @ValueSource(strings={"kite.live-test.max-quantity=-1", "kite.live-test.max-notional=-1",
            "kite.live-test.max-notional=NaN", "kite.live-test.max-notional=1e100",
            "kite.live-test.arm-max-duration=-1s", "kite.live-test.arm-max-duration=2h",
            "kite.live-test.allowed-instruments=invalid"})
    void malformedConfigurationFailsStartup(String property) {
        new ApplicationContextRunner().withUserConfiguration(OperatorControlConfiguration.class)
                .withPropertyValues("spring.profiles.active=test", property)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void allowlistCannotBecomeTheWholeUniverse() {
        var ids = new HashSet<InstrumentId>();
        for (int i=0;i<6;i++) ids.add(new InstrumentId(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new LiveTestProperties(true,ids,1,BigDecimal.TEN,Duration.ofSeconds(1)));
    }
}
