package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import com.kitehybrid.gatea.GateAOfflinePolicy.Consumption;
import java.time.Instant;
import static com.kitehybrid.gatea.adapters.AuthorityCheckpointJournal.State;

/** Offline composition only. Checkpoints add persistence, not real signing/admin authority. */
public final class RecoverableGateAAuthority implements GateAOfflinePolicy.HaltAuthority,GateAOfflinePolicy.AttemptAuthority {
    private final SignedHaltAuthority signed;
    private final FileAttemptAuthority attempts;
    private final AuthorityCheckpointJournal journal;
    private boolean consumingIncarnation;
    public RecoverableGateAAuthority(SignedHaltAuthority signed,FileAttemptAuthority attempts,AuthorityCheckpointJournal journal){
        if(signed==null||attempts==null||journal==null)throw denied();this.signed=signed;this.attempts=attempts;this.journal=journal;
    }
    @Override public synchronized boolean permits(Authorization a,Instant now){
        try{
            var s=journal.snapshot();
            if(s.state()!=State.ACTIVE_SYNTHETIC && !(s.state()==State.CONSUMED&&consumingIncarnation))return false;
            if(!journal.bound(a)||a.epoch()<s.highestEpoch()||now.isBefore(s.observed())||!signed.permits(a,now)){
                journal.advance(s.sequence(),State.QUARANTINED,s.highestEpoch(),now.isBefore(s.observed())?s.observed():now);
                consumingIncarnation=false;return false;
            }
            return journal.advance(s.sequence(),s.state(),a.epoch(),now);
        }catch(Exception invalid){return false;}
    }
    @Override public synchronized boolean available(Authorization a){
        var s=journal.snapshot();return journal.bound(a)&&s.state()==State.ACTIVE_SYNTHETIC&&!s.consumed()&&attempts.available(a);
    }
    @Override public synchronized Consumption consume(Authorization a){
        var s=journal.snapshot();
        if(!available(a)||!journal.advance(s.sequence(),State.CONSUMED,s.epoch(),s.observed()))return Consumption.DENIED;
        Consumption result=attempts.consume(a);consumingIncarnation=result==Consumption.ACKNOWLEDGED;return result;
    }
    @Override public synchronized void recordOutcome(Authorization a,String outcome){
        consumingIncarnation=false;attempts.recordOutcome(a,outcome);
    }
    /** Denial-only synthetic control; never an operational permission or rearm API. */
    public synchronized boolean revokeFixture(Instant now){
        consumingIncarnation=false;var s=journal.snapshot();
        return journal.advance(s.sequence(),State.REVOKED,s.highestEpoch(),now);
    }
    @Override public String toString(){return "RECOVERABLE_AUTHORITY_REDACTED";}
    private static IllegalStateException denied(){return new IllegalStateException("GATE_A_DENIED");}
}
