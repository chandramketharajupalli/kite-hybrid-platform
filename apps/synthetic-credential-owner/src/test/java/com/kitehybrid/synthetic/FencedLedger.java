package com.kitehybrid.synthetic;

import java.util.UUID;

/** Unwired test adapter. Verified witness consumption precedes the existing local ledger. */
final class FencedLedger implements SyntheticOwner.Ledger {
    private final FenceWitness.Client witness;
    private final DisposableLedger local;
    private final boolean loseAfterWitnessAck;
    FencedLedger(FenceWitness.Client witness,DisposableLedger local,boolean loseAfterWitnessAck) {
        this.witness=witness;this.local=local;this.loseAfterWitnessAck=loseAfterWitnessAck;
    }
    public boolean consume(SyntheticOwner.Request request){return false;}
    public boolean consume(SyntheticOwner.Request request,String haltDigest) {
        return witness!=null&&witness.claim(request,haltDigest)&&!loseAfterWitnessAck&&local.consume(request,haltDigest);
    }
    public void terminal(UUID id,SyntheticOwner.Outcome outcome){local.terminal(id,outcome);}
    @Override public String toString(){return "SYNTHETIC_FENCED_LEDGER_REDACTED";}
}
