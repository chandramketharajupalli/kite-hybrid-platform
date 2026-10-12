package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static com.kitehybrid.gatea.adapters.AuthorityCheckpointJournal.State;

/** Test-only bounded child, never a production launcher. */
public final class AuthorityRecoveryProbe {
    public static void main(String[] args){
        Path root=Path.of(args[0]);String action=args[1];
        var j=new AuthorityCheckpointJournal(root.resolve("local"),root.resolve("witness"),AUTH,e->{if(e.equals(action))Runtime.getRuntime().halt(23);});
        try{
            if(action.equals("CAS")||action.equals("BEFORE_RESERVATION")||action.equals("AFTER_WITNESS")||action.equals("AFTER_LOCAL")){
                if(!j.advance(0,State.CONSUMED,1,NOW))System.exit(42);
            }else{
                var a=new RecoverableGateAAuthority(authority(AUTH,new AtomicReference<>(state(1,false))),new FileAttemptAuthority(root.resolve("attempt"),STORE,AUTH),j);
                if(action.equals("REVOKE")){if(!a.revokeFixture(NOW))System.exit(42);}
                else if(action.equals("CHECK")){if(!a.permits(AUTH,NOW))System.exit(42);}
                else {var p=new GateAOfflinePolicy(AUTH.process(),AUTH.keyId(),Clock.fixed(NOW,ZoneOffset.UTC),a,a,parser(CSV));observe(p);}
            }
            System.out.println("SYNTHETIC_ACCEPTED");
        }catch(Exception denied){System.out.println("SYNTHETIC_DENIED");System.exit(42);}
    }
}
