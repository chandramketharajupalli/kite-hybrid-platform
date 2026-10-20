package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.infrastructure.RegistryOperatorIdentityAdapter;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatorSessionLifecycleTest {
    private static final Instant START = Instant.parse("2026-10-20T00:00:00Z");
    private static final String HANDLE = "A".repeat(48), NEXT = "B".repeat(48);
    private static final class MutableClock extends Clock {
        Instant value = START;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        public Instant instant() { return value; }
    }
    private static Policy policy() { return new Policy("fixture-issuer", "fixture-subject", "fixture-audience", "v1", "local-registry",
            Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofSeconds(5), Set.of(RISK_READ), Set.of("fixture-account")); }
    private static Event event(Trust trust) { return new Event("fixture-issuer", "fixture-subject", "fixture-audience",
            UUID.randomUUID(), START, trust); }
    private static final class Fixture {
        final MutableClock clock = new MutableClock();
        final OperatorSessionRegistryPort registry = mock(OperatorSessionRegistryPort.class);
        final Map<String,Session> rows = new HashMap<>();
        final OperatorSessionLifecycle lifecycle = new OperatorSessionLifecycle(registry, clock, policy());
        Event event = event(Trust.VERIFIED_OPERATOR_ASSERTION);
        Fixture() {
            when(registry.establish(any())).thenAnswer(a -> { Session s = a.getArgument(0); rows.put(s.digest(),s); return true; });
            when(registry.find(anyString())).thenAnswer(a -> Optional.ofNullable(rows.get(a.getArgument(0)))
                    .map(s -> new Observation(s,clock.instant())));
            when(registry.touch(anyString(),anyLong(),any())).thenAnswer(a -> {
                Session s = rows.get(a.getArgument(0)); rows.put(s.digest(),s.changed(s.digest(),a.getArgument(2),false));return true;
            });
            when(registry.rotate(anyString(),anyLong(),anyString(),any())).thenAnswer(a -> {
                Session s = rows.get(a.getArgument(0)); rows.put(s.digest(),s.changed(s.digest(),s.lastActivityAt(),true));
                String next = a.getArgument(2); rows.put(next,s.changed(next,a.getArgument(3),false)); return true;
            });
            when(registry.revoke(anyString(),any())).thenAnswer(a -> {
                Session s = rows.get(a.getArgument(0)); if(s==null)return false;
                rows.put(s.digest(),s.changed(s.digest(),s.lastActivityAt(),true));return true;
            });
            assertTrue(lifecycle.establish(HANDLE,event).diagnosticReady());
        }
        Session row() { return rows.get(OperatorSessionLifecycle.hashHandle(HANDLE)); }
        OperatorSessionLifecycle.Result read() { return lifecycle.assess(HANDLE,"GET","/api/trading/risk/status","fixture-account"); }
    }
    @Test void verifiedEventBindsExactSession() {
        var f=new Fixture(); assertTrue(f.read().diagnosticReady());
        assertEquals(f.event,f.row().event()); assertEquals(Set.of(RISK_READ),f.row().permissions());
        assertFalse(f.lifecycle.assess(NEXT,"GET","/api/trading/risk/status","fixture-account").diagnosticReady());
        var binding=new OperatorSessionVerificationPort.SessionBinding(HANDLE,f.event.authenticationEvent().toString());
        var adapter=new RegistryOperatorIdentityAdapter(f.registry,f.clock,policy(),binding);
        assertEquals("fixture-subject",adapter.currentSession().orElseThrow().principal().subject());
        assertTrue(adapter.verificationFor(new OperatorSessionVerificationPort.SessionBinding(NEXT,binding.authenticationEvent())).isEmpty());
        assertTrue(adapter.verificationFor(binding).isPresent());
        assertFalse(f.lifecycle.establish(NEXT,new Event("fixture-issuer","other-subject","fixture-audience",
                UUID.randomUUID(),START,Trust.VERIFIED_OPERATOR_ASSERTION)).diagnosticReady());
    }
    @Test void unverifiedOrSyntheticEventIsRejected() {
        var f=new Fixture(); clearInvocations(f.registry);
        for(var trust:List.of(Trust.UNVERIFIED,Trust.SYNTHETIC)) assertFalse(f.lifecycle.establish(NEXT,event(trust)).diagnosticReady());
        verify(f.registry,never()).establish(any());
    }
    @Test void brokerEvidenceIsRejected() {
        var f=new Fixture(); clearInvocations(f.registry);
        assertFalse(f.lifecycle.establish(NEXT,event(Trust.BROKER)).diagnosticReady());verify(f.registry,never()).establish(any());
    }
    @Test void absoluteExpiryIsExclusive() {
        var f=new Fixture(); f.clock.value=START.plusSeconds(1799);
        Session s=f.row(); f.rows.put(s.digest(),s.changed(s.digest(),f.clock.value,false));
        assertTrue(f.read().diagnosticReady());f.clock.value=START.plusSeconds(1800);assertFalse(f.read().diagnosticReady());
    }
    @Test void inactivityExpiryIsExclusive() {
        var f=new Fixture();f.clock.value=START.plusSeconds(599);assertTrue(f.read().diagnosticReady());
        f.clock.value=START.plusSeconds(600);assertFalse(f.read().diagnosticReady());assertFalse(f.lifecycle.renew(HANDLE).diagnosticReady());
    }
    @Test void futureAuthenticationOrActivityIsRejected() {
        var f=new Fixture();
        assertFalse(f.lifecycle.establish(NEXT,new Event("fixture-issuer","fixture-subject","fixture-audience",
                UUID.randomUUID(),START.plusSeconds(1),Trust.VERIFIED_OPERATOR_ASSERTION)).diagnosticReady());
        var s=f.row();f.rows.put(s.digest(),s.changed(s.digest(),START.plusSeconds(1),false));assertFalse(f.read().diagnosticReady());
    }
    @Test void revokedUnknownOrStaleStateIsRejected() {
        var f=new Fixture();assertTrue(f.lifecycle.logout(HANDLE).diagnosticReady());assertFalse(f.read().diagnosticReady());
        f=new Fixture();when(f.registry.find(anyString())).thenReturn(Optional.empty());assertFalse(f.read().diagnosticReady());
        f=new Fixture();var s=f.row();f.clock.value=START.plusSeconds(6);
        when(f.registry.find(anyString())).thenReturn(Optional.of(new Observation(s,START)));assertFalse(f.read().diagnosticReady());
        when(f.registry.find(anyString())).thenThrow(new IllegalStateException("private fixture detail"));
        assertEquals(OperatorSessionLifecycle.Reason.UNAVAILABLE,f.read().reason());assertFalse(f.read().toString().contains("private"));
    }
    @Test void renewalCannotExpandOriginalGrant() {
        var f=new Fixture();var before=f.row();f.clock.value=START.plusSeconds(5);assertTrue(f.lifecycle.renew(HANDLE).diagnosticReady());
        assertEquals(before.absoluteExpiresAt(),f.row().absoluteExpiresAt());assertEquals(before.permissions(),f.row().permissions());
        assertEquals(before.accounts(),f.row().accounts());assertEquals(before.event(),f.row().event());
        assertThrows(UnsupportedOperationException.class,()->before.accounts().add("other"));
        assertThrows(UnsupportedOperationException.class,()->before.permissions().add(ORDERS_READ));
    }
    @Test void rotationInvalidatesPreviousBinding() {
        var f=new Fixture();assertTrue(f.lifecycle.rotate(HANDLE,NEXT).diagnosticReady());assertFalse(f.read().diagnosticReady());
        assertTrue(f.lifecycle.assess(NEXT,"GET","/api/trading/risk/status","fixture-account").diagnosticReady());
        assertEquals(f.row().event(),f.rows.get(OperatorSessionLifecycle.hashHandle(NEXT)).event());
    }
    @Test void missingPermissionIsDenied() {
        var f=new Fixture();assertFalse(f.lifecycle.assess(HANDLE,"GET","/api/trading/orders","fixture-account").diagnosticReady());
        assertFalse(f.lifecycle.assess(HANDLE,"POST","/api/trading/risk/status","fixture-account").diagnosticReady());
    }
    @Test void crossAccountScopeIsDenied() {
        var f=new Fixture();assertFalse(f.lifecycle.assess(HANDLE,"GET","/api/trading/risk/status","other-account").diagnosticReady());
    }
    @Test void diagnosticResultsNeverAuthorizeProduction() {
        var f=new Fixture();assertTrue(f.read().diagnosticReady());assertFalse(f.read().productionAuthorized());
        var adapter=new RegistryOperatorIdentityAdapter(f.registry,f.clock,policy(),
                new OperatorSessionVerificationPort.SessionBinding(HANDLE,f.event.authenticationEvent().toString()));
        var principal=adapter.currentSession().orElseThrow().principal();assertFalse(principal.authenticatedProductionIdentity());
        assertEquals(OperatorPrincipal.Provenance.UNVERIFIED_CLAIM,principal.provenance());
        assertFalse(new TradingApiAuthorization(f.clock).authorize(()->Optional.of(principal),"GET","/api/trading/risk/status").authorized());
        assertFalse(f.row().toString().contains(HANDLE));assertFalse(f.row().toString().contains("fixture-subject"));
    }
}
