package com.kitehybrid.platform;

import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OneOrderPermitTest {
    private static final Instant NOW=Instant.parse("2026-09-28T06:00:00Z");
    private final AtomicReference<UUID> identity=new AtomicReference<>(new UUID(0,1));
    private final RuntimeExecutionArming arm=new RuntimeExecutionArming(new SimpleMeterRegistry(),()->Optional.ofNullable(identity.get()));
    private OrderRecord order(long id) {
        var order=mock(OrderRecord.class);
        when(order.id()).thenReturn(new OrderId(new UUID(0,id))); when(order.version()).thenReturn(2L);
        return order;
    }
    @Test void orderVersionSessionExpiryAndConsumedPermitCannotBeSubstituted() {
        var a=order(1); var b=order(2);
        arm.arm(a,Duration.ofSeconds(30),NOW);
        assertEquals(ExecutionDenialReason.NONE,arm.bindingReason(a,NOW));
        assertEquals(ExecutionDenialReason.ORDER_NOT_ARMED,arm.bindingReason(b,NOW));
        when(a.version()).thenReturn(3L);
        assertEquals(ExecutionDenialReason.ORDER_VERSION_CHANGED,arm.bindingReason(a,NOW));
        identity.set(new UUID(0,2)); assertFalse(arm.armed(NOW));
        arm.arm(a,Duration.ofSeconds(30),NOW);
        assertTrue(arm.armed(NOW.plusSeconds(30).minusNanos(1))); assertFalse(arm.armed(NOW.plusSeconds(30)));
        arm.arm(a,Duration.ofSeconds(30),NOW);
        var claim=arm.claim(a.id(),NOW);
        arm.disarm(); assertFalse(arm.armed(NOW));
        assertEquals(RuntimeExecutionArming.PermitState.CLAIMED,arm.status(NOW).permitState());
        assertThrows(IllegalStateException.class,()->arm.arm(b,Duration.ofSeconds(30),NOW));
        arm.complete(claim);
        assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,arm.status(NOW).permitState());
        assertThrows(RuntimeException.class,()->arm.claim(a.id(),NOW));
        arm.arm(b,Duration.ofSeconds(30),NOW); arm.complete(claim);
        assertEquals(ExecutionDenialReason.NONE,arm.bindingReason(b,NOW),"An old completion cannot revoke a new authorization");
    }
    @Test void exactlyOneAtomicClaimWinsAcrossThreads() throws Exception {
        var order=order(1); arm.arm(order,Duration.ofSeconds(30),NOW);
        try (var pool=Executors.newFixedThreadPool(8)) {
            var start=new CountDownLatch(1); var futures=new ArrayList<Future<Boolean>>();
            for (int i=0;i<8;i++) futures.add(pool.submit(()->{ start.await(); try { arm.claim(order.id(),NOW); return true; } catch (RuntimeException denied) { return false; } }));
            start.countDown(); int wins=0;
            for (var future:futures) if (future.get(10,TimeUnit.SECONDS)) wins++;
            assertEquals(1,wins);
        }
    }
    @Test void unboundLegacyArmCannotAuthorizeFirstLiveOrder() {
        arm.arm(Duration.ofSeconds(30),NOW);
        assertEquals(ExecutionDenialReason.ORDER_NOT_ARMED,arm.bindingReason(order(1),NOW));
        assertThrows(RuntimeException.class,()->arm.claim(order(1).id(),NOW));
    }
}
