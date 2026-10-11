package com.kitehybrid.synthetic;

import java.util.UUID;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

/** Test subprocess only. Arguments contain ports, fixture marker, ID and mode; no keys or capabilities. */
public final class OwnerWorker {
    public static void main(String[] args) throws Exception {
        int dbPort=Integer.parseInt(args[0]);String marker=args[1];UUID id=UUID.fromString(args[2]);
        var peer=new LoopbackPeer(Integer.parseInt(args[3]),2000);
        String incarnation=args[4];var fault=SyntheticOwner.Fault.valueOf(args[5]);
        var clock=new MutableClock();var key=key();var halt=new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());
        var base=request(peer);
        var request=new SyntheticOwner.Request(id,base.version(),incarnation,base.caller(),base.recipient(),base.account(),base.purpose(),base.method(),base.endpoint(),base.issued(),base.expires(),base.haltEpoch(),base.authority());
        var ledger=new DisposableLedger(dbPort,marker,DisposableLedger.Fault.NONE);
        try(var owner=new SyntheticOwner(incarnation,CALLER,RECIPIENT,ACCOUNT,SyntheticOwner.Credential.generatedSyntheticFixture(),clock,ledger,halt,peer,()->SAFE)) {
            var result=owner.observe(request,CALLER,sign(request,key),fault);
            // Only bounded status/count is emitted; no request, signature, account, credential or exception.
            System.out.println("SYNTHETIC_ATTEMPTED_SENDS="+peer.attemptedSends());
            System.out.flush();
            Runtime.getRuntime().halt(switch(result.outcome()){
                case SYNTHETIC_READ_SUCCEEDED->17;case SYNTHETIC_READ_OUTCOME_UNKNOWN->19;default->18;
            });
        }
    }
}
