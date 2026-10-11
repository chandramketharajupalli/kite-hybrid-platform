package com.kitehybrid.synthetic;

import java.util.UUID;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

/** Test-only process. A bounded stage label is the only output; parent owns termination. */
public final class IsolationWorker {
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(args[0]); String marker=args[1]; UUID id=UUID.fromString(args[2]);
        String stage=args[3];
        var ledger=new DisposableLedger(port,marker,DisposableLedger.Fault.NONE,
            point->{if(point.equals(stage))checkpoint(point);});
        if(args.length==4) {
            boolean grant=ledger.consume(LedgerWorker.request(id));
            Runtime.getRuntime().halt(grant?17:18);
        }
        var peer=new LoopbackPeer(Integer.parseInt(args[4]),3000);
        var clock=new MutableClock();var key=key();var halt=new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());
        var base=request(peer);
        var request=new SyntheticOwner.Request(id,base.version(),base.owner(),base.caller(),base.recipient(),base.account(),
            base.purpose(),base.method(),base.endpoint(),base.issued(),base.expires(),base.haltEpoch(),base.authority());
        var observed=new SyntheticOwner.Ledger() {
            public boolean consume(SyntheticOwner.Request ignored){return false;}
            public boolean consume(SyntheticOwner.Request r,String digest){
                boolean granted=ledger.consume(r,digest);
                if(granted&&stage.equals("AFTER_OWNER_ACK"))checkpoint(stage);
                return granted;
            }
            public void terminal(UUID attempt,SyntheticOwner.Outcome outcome){
                if(stage.equals("BEFORE_OUTCOME"))checkpoint(stage);
                ledger.terminal(attempt,outcome);
            }
        };
        try(var owner=owner(observed,halt,peer,clock,()->SAFE)) {
            var result=owner.observe(request,CALLER,sign(request,key),SyntheticOwner.Fault.NONE);
            Runtime.getRuntime().halt(result.outcome()==SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED?17:18);
        }
    }
    private static void checkpoint(String stage) {
        System.out.println("SYNTHETIC_STAGE="+stage);System.out.flush();
        try {System.in.read();} catch(java.io.IOException ignored) { }
        Runtime.getRuntime().halt(29); // A closed control pipe never resumes work.
    }
}
