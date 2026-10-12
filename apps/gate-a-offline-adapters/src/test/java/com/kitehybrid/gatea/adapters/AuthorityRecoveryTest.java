package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static com.kitehybrid.gatea.adapters.AuthorityCheckpointJournal.State;
import static org.junit.jupiter.api.Assertions.*;

class AuthorityRecoveryTest {
    @TempDir Path root;
    AuthorityCheckpointJournal journal(){return new AuthorityCheckpointJournal(root.resolve("local"),root.resolve("witness"),AUTH);}
    FileAttemptAuthority attempts(){return new FileAttemptAuthority(root.resolve("attempt"),STORE,AUTH);}
    void init(){journal().initializeFixture();attempts().initializeFixture();}
    RecoverableGateAAuthority wrapper(){return new RecoverableGateAAuthority(authority(AUTH,new AtomicReference<>(state(1,false))),attempts(),journal());}
    GateAOfflinePolicy policy(RecoverableGateAAuthority a){return new GateAOfflinePolicy(AUTH.process(),AUTH.keyId(),Clock.fixed(NOW,ZoneOffset.UTC),a,a,parser(CSV));}
    @Test void uninitializedNeverArms(){assertEquals(State.UNINITIALIZED,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void integratedConsumeRestartAndReplay(){init();var p=policy(wrapper());assertTrue(observe(p).synthetic());assertEquals(1,p.syntheticSends());
        assertEquals(State.CONSUMED,journal().snapshot().state());assertTrue(journal().snapshot().consumed());
        assertThrows(IllegalStateException.class,()->observe(policy(wrapper())));}
    @Test void stickyRevocationReconstructed(){init();assertTrue(wrapper().revokeFixture(NOW));assertEquals(State.REVOKED,journal().snapshot().state());
        assertFalse(wrapper().permits(AUTH,NOW));assertFalse(journal().advance(journal().snapshot().sequence(),State.ACTIVE_SYNTHETIC,1,NOW));}
    @Test void signedRevocationPersistsAsQuarantine(){init();var a=new RecoverableGateAAuthority(authority(AUTH,new AtomicReference<>(state(1,true))),attempts(),journal());
        assertFalse(a.permits(AUTH,NOW));assertEquals(State.QUARANTINED,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void highestEpochSurvivesRestartAndRejectsStale(){init();assertTrue(journal().advance(0,State.ACTIVE_SYNTHETIC,2,NOW));
        assertEquals(2,journal().snapshot().highestEpoch());assertFalse(wrapper().permits(AUTH,NOW));assertEquals(State.QUARANTINED,journal().snapshot().state());}
    @Test void clockRollbackPersistsQuarantine(){init();assertTrue(wrapper().permits(AUTH,NOW.plusSeconds(1)));assertFalse(wrapper().permits(AUTH,NOW));
        assertEquals(State.QUARANTINED,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW.plusSeconds(2)));}
    @Test void conflictingKeyIdentityDenied(){init();var a=new Authorization(AUTH.attempt(),AUTH.operation(),AUTH.method(),AUTH.endpoint(),AUTH.process(),"other-key",1,AUTH.issued(),AUTH.expires(),AUTH.ownerApprovalReference());
        assertFalse(wrapper().permits(a,NOW));assertEquals(State.QUARANTINED,journal().snapshot().state());}
    @Test void corruptAndMissingCheckpointFailClosed()throws Exception{init();Files.writeString(root.resolve("witness/00000000000000000000.record"),"corrupt");
        assertEquals(State.RECOVERY_PENDING,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));
        Files.delete(root.resolve("witness/00000000000000000000.record"));assertEquals(State.RECOVERY_PENDING,journal().snapshot().state());}
    @Test void localRollbackWithWitnessSurvivingDenied()throws Exception{init();assertTrue(journal().advance(0,State.REVOKED,1,NOW));
        Files.delete(root.resolve("local/00000000000000000001.record"));assertEquals(State.RECOVERY_PENDING,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void witnessRollbackWithLocalSurvivingDenied()throws Exception{init();assertTrue(journal().advance(0,State.REVOKED,1,NOW));
        Files.delete(root.resolve("witness/00000000000000000001.record"));assertEquals(State.RECOVERY_PENDING,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void lostAckAndPartialAdvanceDenyWithoutRepair(){init();var j=new AuthorityCheckpointJournal(root.resolve("local"),root.resolve("witness"),AUTH,e->{if(e.equals("AFTER_WITNESS"))throw new IllegalStateException("synthetic-sensitive");});
        assertFalse(j.advance(0,State.CONSUMED,1,NOW));assertEquals(State.RECOVERY_PENDING,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void lostAckAfterBothCopiesRetainsConsumption(){init();var j=new AuthorityCheckpointJournal(root.resolve("local"),root.resolve("witness"),AUTH,e->{if(e.equals("AFTER_LOCAL"))throw new IllegalStateException();});
        assertFalse(j.advance(0,State.CONSUMED,1,NOW));assertEquals(State.CONSUMED,journal().snapshot().state());assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void staleCompareAndAdvanceCannotWin(){init();assertTrue(journal().advance(0,State.ACTIVE_SYNTHETIC,1,NOW));assertFalse(journal().advance(0,State.CONSUMED,1,NOW));}
    @Test void unknownResponseRemainsConsumed(){init();var p=policy(wrapper());var r=new GateAOfflinePolicy.FixtureResponse(CSV.getBytes(java.nio.charset.StandardCharsets.UTF_8),GateAOfflinePolicy.FixtureOutcome.UNCERTAIN);
        assertThrows(IllegalStateException.class,()->p.observeFixture("GET",GateAOfflinePolicy.ENDPOINT,null,AUTH,r));assertFalse(wrapper().permits(AUTH,NOW));}
    @Test void gateBAndLoggingDenied(){init();var p=policy(wrapper());assertThrows(IllegalStateException.class,()->p.observeFixture("GET","https://api.kite.trade/instruments/historical/1/5minute",null,AUTH,response()));
        assertEquals(0,p.syntheticSends());assertEquals("AUTHORITY_JOURNAL_REDACTED",journal().toString());assertEquals("AUTHORITY_CHECKPOINT_REDACTED",journal().snapshot().toString());assertEquals("RECOVERABLE_AUTHORITY_REDACTED",wrapper().toString());}
    @Test void reinitializeCannotClearState(){init();assertTrue(wrapper().revokeFixture(NOW));assertThrows(IllegalStateException.class,()->journal().initializeFixture());assertEquals(State.REVOKED,journal().snapshot().state());}
}
