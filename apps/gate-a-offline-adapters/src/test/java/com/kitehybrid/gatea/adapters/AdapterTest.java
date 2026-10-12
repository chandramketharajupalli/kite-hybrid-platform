package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import com.kitehybrid.gatea.GateAOfflinePolicy.Consumption;
import com.kitehybrid.platform.instrument.application.OfflineUniverseResolution;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AdapterTest {
    @TempDir Path temp;
    AtomicReference<SignedHaltAuthority.State> current=new AtomicReference<>(state(1,false));
    FileAttemptAuthority store() { var s=new FileAttemptAuthority(temp.resolve(UUID.randomUUID().toString()),STORE,AUTH);s.initializeFixture();return s; }
    @Test void integratedSuccessHashProvenanceAndReplay() {
        var s=store();var parser=parser(CSV);var p=policy(s,authority(AUTH,current),parser);
        var receipt=observe(p);assertTrue(receipt.synthetic());assertEquals(NOW,receipt.observedAt());
        assertEquals(parser.rows().getFirst().provenance().orElseThrow().sha256(),receipt.sha256());
        assertEquals(1,p.syntheticSends());assertEquals(2,parser.rows().size());
        assertEquals("SYNTH&A",parser.rows().getFirst().source().symbol());
        assertEquals("ZERODHA",parser.rows().getFirst().instrument().orElseThrow().brokerId().broker());
        assertEquals(OfflineUniverseResolution.Status.RESOLVED,parser.rows().getFirst().status());
        assertEquals(OfflineUniverseResolution.Status.NOT_FOUND,parser.rows().get(1).status());
        assertThrows(IllegalStateException.class,()->observe(p));
        assertThrows(IllegalStateException.class,()->observe(policy(s,authority(AUTH,current),parser(CSV))));
    }
    @ParameterizedTest @ValueSource(strings={"POST","PUT","DELETE","get","HEAD"})
    void methodsDenied(String method) {
        var s=store();var p=policy(s,authority(AUTH,current),parser(CSV));
        assertThrows(IllegalStateException.class,()->p.observeFixture(method,GateAOfflinePolicy.ENDPOINT,null,AUTH,response()));
        assertTrue(s.available(AUTH));assertEquals(0,p.syntheticSends());
    }
    @ParameterizedTest @ValueSource(strings={"http://api.kite.trade/instruments/NSE","https://evil.invalid/instruments/NSE",
        "https://api.kite.trade:443/instruments/NSE","https://api.kite.trade/instruments/NSE?x=1",
        "https://api.kite.trade/instruments/NSE#x","https://user@api.kite.trade/instruments/NSE",
        "https://api.kite.trade/instruments/../instruments/NSE","https://api.kite.trade/instruments/%4eSE",
        "/instruments/NSE","https://api.kite.trade/orders","https://api.kite.trade/instruments/historical/1/5minute",
        "wss://api.kite.trade/instruments/NSE","https://api.kite.trade/instruments/NSE/","https://api.kite.trade/instruments/NFO"})
    void routesDenied(String endpoint) {
        var s=store();var p=policy(s,authority(AUTH,current),parser(CSV));
        assertThrows(IllegalStateException.class,()->p.observeFixture("GET",endpoint,null,AUTH,response()));
        assertTrue(s.available(AUTH));assertEquals(0,p.syntheticSends());
    }
    @Test void proxyAndMissingAuthorizationDeniedWithoutConsumption() {
        var s=store();var p=policy(s,authority(AUTH,current),parser(CSV));
        assertThrows(IllegalStateException.class,()->p.observeFixture("GET",GateAOfflinePolicy.ENDPOINT,"proxy",AUTH,response()));
        assertThrows(IllegalStateException.class,()->p.observeFixture("GET",GateAOfflinePolicy.ENDPOINT,null,null,response()));
        assertTrue(s.available(AUTH));
    }
    @Test void forgedSignatureAndConflictingKeyDenied() {
        var bad=new SignedHaltAuthority(KEYS.getPublic(),"fixture-key","fixture-process","fixture-owner-reference",1,
            new SignedHaltAuthority.Evidence<>(AUTH,new byte[384]),()->signed(current.get()));
        assertFalse(bad.permits(AUTH,NOW));
        var other=keys();var wrong=new SignedHaltAuthority(other.getPublic(),"fixture-key","fixture-process","fixture-owner-reference",1,
            new SignedHaltAuthority.Evidence<>(AUTH,sign(SignedHaltAuthority.authorizationBytes(AUTH),KEYS)),()->signed(current.get()));
        assertFalse(wrong.permits(AUTH,NOW));
    }
    @ParameterizedTest @ValueSource(strings={"operation","method","endpoint","process","key","epoch","owner","future","expired","long"})
    void signedButWrongScopeDenied(String change) {
        var a=new Authorization(AUTH.attempt(),change.equals("operation")?"GATE_B":AUTH.operation(),
            change.equals("method")?"POST":AUTH.method(),change.equals("endpoint")?"https://api.kite.trade/orders":AUTH.endpoint(),
            change.equals("process")?"other":AUTH.process(),change.equals("key")?"other":AUTH.keyId(),change.equals("epoch")?2:1,
            change.equals("future")?NOW.plusSeconds(1):AUTH.issued(),change.equals("expired")?NOW:change.equals("long")?NOW.plusSeconds(31):AUTH.expires(),
            change.equals("owner")?"other":AUTH.ownerApprovalReference());
        assertFalse(authority(a,current).permits(a,NOW));
    }
    @Test void changedAttemptCannotUseExistingSignature() {
        var a=new Authorization(UUID.randomUUID(),AUTH.operation(),AUTH.method(),AUTH.endpoint(),AUTH.process(),AUTH.keyId(),1,AUTH.issued(),AUTH.expires(),AUTH.ownerApprovalReference());
        assertFalse(authority(AUTH,current).permits(a,NOW));
    }
    @Test void revokedUnavailableAndForgedStateDenied() {
        current.set(state(1,true));assertFalse(authority(AUTH,current).permits(AUTH,NOW));
        var h=new SignedHaltAuthority(KEYS.getPublic(),"fixture-key","fixture-process","fixture-owner-reference",1,
            new SignedHaltAuthority.Evidence<>(AUTH,sign(SignedHaltAuthority.authorizationBytes(AUTH),KEYS)),()->{throw new IllegalStateException("fixture-sensitive");});
        assertFalse(h.permits(AUTH,NOW));
        h=new SignedHaltAuthority(KEYS.getPublic(),"fixture-key","fixture-process","fixture-owner-reference",1,
            new SignedHaltAuthority.Evidence<>(AUTH,sign(SignedHaltAuthority.authorizationBytes(AUTH),KEYS)),()->new SignedHaltAuthority.Evidence<>(state(1,false),new byte[384]));
        assertFalse(h.permits(AUTH,NOW));
    }
    @Test void staleStateAndEpochRollbackQuarantine() {
        var h=authority(AUTH,current);assertTrue(h.permits(AUTH,NOW));assertFalse(h.permits(AUTH,NOW.plusSeconds(5)));
        current.set(state(2,false));assertFalse(h.permits(AUTH,NOW.plusSeconds(5)));
        current.set(state(1,false));assertFalse(h.permits(AUTH,NOW.plusSeconds(5)));
    }
    @Test void clockRollbackQuarantinePersists() {
        var h=authority(AUTH,current);assertTrue(h.permits(AUTH,NOW.plusSeconds(1)));
        assertFalse(h.permits(AUTH,NOW));assertFalse(h.permits(AUTH,NOW.plusSeconds(2)));
    }
    @Test void observedRevocationCannotBeClearedByReplayedUnrevokedState() {
        var h=authority(AUTH,current);assertTrue(h.permits(AUTH,NOW));
        current.set(state(1,true));assertFalse(h.permits(AUTH,NOW));
        current.set(state(1,false));assertFalse(h.permits(AUTH,NOW));
    }
    @Test void stateQuarantineWrongApprovalAndProcessDeny() {
        for(var s:List.of(new SignedHaltAuthority.State(1,false,true,NOW,NOW.plusSeconds(5),"fixture-key","fixture-process","fixture-owner-reference"),
            new SignedHaltAuthority.State(1,false,false,NOW,NOW.plusSeconds(5),"fixture-key","other","fixture-owner-reference"),
            new SignedHaltAuthority.State(1,false,false,NOW,NOW.plusSeconds(5),"fixture-key","fixture-process","other"))) {
            current.set(s);assertFalse(authority(AUTH,current).permits(AUTH,NOW));
        }
    }
    @Test void revocationAfterConsumptionDeniesObservationAndRemainsSpent() {
        var s=new FileAttemptAuthority(temp.resolve("store"),STORE,AUTH,event->{if(event.equals("AFTER_FORCE"))current.set(state(1,true));});
        s.initializeFixture();var p=policy(s,authority(AUTH,current),parser(CSV));
        assertThrows(IllegalStateException.class,()->observe(p));assertEquals(0,p.syntheticSends());assertFalse(s.available(AUTH));
    }
    @Test void lostAckAfterForceDeniesObservationAndRemainsSpent() {
        var s=new FileAttemptAuthority(temp.resolve("store"),STORE,AUTH,event->{if(event.equals("AFTER_FORCE"))throw new IllegalStateException("fixture-sensitive");});
        s.initializeFixture();var p=policy(s,authority(AUTH,current),parser(CSV));
        var error=assertThrows(IllegalStateException.class,()->observe(p));assertEquals("GATE_A_DENIED",error.getMessage());assertNull(error.getCause());
        assertEquals(0,p.syntheticSends());assertFalse(s.available(AUTH));
    }
    @ParameterizedTest @ValueSource(strings={"REDIRECT","TIMEOUT","UNCERTAIN","SERVER_ERROR"})
    void uncertainResponsesNeverReopen(String outcome) {
        var s=store();var p=policy(s,authority(AUTH,current),parser(CSV));
        var r=new GateAOfflinePolicy.FixtureResponse(CSV.getBytes(StandardCharsets.UTF_8),GateAOfflinePolicy.FixtureOutcome.valueOf(outcome));
        assertThrows(IllegalStateException.class,()->p.observeFixture("GET",GateAOfflinePolicy.ENDPOINT,null,AUTH,r));
        assertEquals(1,p.syntheticSends());assertFalse(s.available(AUTH));assertThrows(IllegalStateException.class,()->observe(p));
    }
    @Test void missingCorruptAndPartialStoreFailClosed() throws Exception {
        Path root=temp.resolve("store");var s=new FileAttemptAuthority(root,STORE,AUTH);
        assertFalse(s.available(AUTH));assertEquals(Consumption.DENIED,s.consume(AUTH));
        s.initializeFixture();Files.writeString(root.resolve("identity"),"corrupt");assertFalse(s.available(AUTH));assertEquals(Consumption.DENIED,s.consume(AUTH));
        var partial=new FileAttemptAuthority(temp.resolve("partial"),STORE,AUTH,e->{if(e.equals("AFTER_CREATE"))throw new IllegalStateException();});
        partial.initializeFixture();assertEquals(Consumption.UNCERTAIN,partial.consume(AUTH));assertFalse(partial.available(AUTH));
        assertEquals(Consumption.DENIED,partial.consume(AUTH));
    }
    @Test void cannotReinitializeOrUseWrongStoreBinding() {
        Path root=temp.resolve("store");var s=new FileAttemptAuthority(root,STORE,AUTH);s.initializeFixture();
        assertThrows(IllegalStateException.class,s::initializeFixture);
        assertFalse(new FileAttemptAuthority(root,UUID.randomUUID(),AUTH).available(AUTH));
    }
    @Test void redactedObjectsHaveNoEvidencePayload() {
        assertEquals("GATE_A_AUTHORIZATION_REDACTED",AUTH.toString());
        assertEquals("HALT_AUTHORITY_REDACTED",authority(AUTH,current).toString());
        assertEquals("SIGNED_EVIDENCE_REDACTED",signed(current.get()).toString());
        assertEquals("ATTEMPT_STORE_REDACTED",store().toString());
        assertEquals("NSE_MASTER_ADAPTER_REDACTED",parser(CSV).toString());
    }
}
