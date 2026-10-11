package com.kitehybrid.synthetic;

import java.io.*;
import java.util.UUID;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

/** Test-only child. Authentication bootstrap is private stdin, never process arguments/environment. */
public final class FenceWorker {
    public static void main(String[] args) throws Exception {
        var client=FenceWitness.Client.bootstrap(new DataInputStream(System.in));
        var local=new DisposableLedger(Integer.parseInt(args[0]),args[1],DisposableLedger.Fault.NONE);
        UUID id=UUID.fromString(args[2]);var ledger=new FencedLedger(client,local,false);
        if(args[3].equals("LEDGER"))Runtime.getRuntime().halt(ledger.consume(LedgerWorker.request(id),"0".repeat(64))?17:18);
        var peer=new LoopbackPeer(Integer.parseInt(args[4]),2000);var clock=new MutableClock();var key=key();
        var halt=new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());var r=request(peer);
        String incarnation=args.length>5?args[5]:r.owner();
        r=new SyntheticOwner.Request(id,r.version(),incarnation,r.caller(),r.recipient(),r.account(),r.purpose(),r.method(),r.endpoint(),r.issued(),r.expires(),r.haltEpoch(),r.authority());
        try(var owner=new SyntheticOwner(incarnation,CALLER,RECIPIENT,ACCOUNT,SyntheticOwner.Credential.generatedSyntheticFixture(),clock,ledger,halt,peer,()->SAFE)) {
            var result=owner.observe(r,CALLER,sign(r,key),SyntheticOwner.Fault.NONE);
            System.out.println("SYNTHETIC_ATTEMPTED_SENDS="+peer.attemptedSends());System.out.flush();
            Runtime.getRuntime().halt(result.outcome()==SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED?17:18);
        }
    }
}
