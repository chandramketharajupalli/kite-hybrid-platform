package com.kitehybrid.synthetic;

import java.util.UUID;

/** Disposable synthetic test JVM; never packaged as a runtime entry point. */
final class GateAHaltProcessProbe {
    public static void main(String[] args) throws Exception {
        GateAHaltBoundaryTest.keys=SyntheticSecurityTest.key();
        var clock=new GateAHaltBoundaryTest.Time();
        var control=new GateAHaltBoundary.Control(clock,1,GateAHaltBoundaryTest.KEY,GateAHaltBoundaryTest.keys.getPublic());
        String process="SYNTHETIC_"+UUID.randomUUID();
        var boundary=new GateAHaltBoundary(args[0],process,clock,control);
        var approval=GateAHaltBoundaryTest.approval(UUID.fromString(args[1]));
        var signed=GateAHaltBoundaryTest.sign(approval,process,1);
        try {
            boundary.acquire("GET",GateABoundary.URL,null,approval,GateAHaltBoundaryTest.OWNER,1,signed,
                    GateABoundaryTest.response(),stage->{if(stage.equals(args[2]))Runtime.getRuntime().halt(23);});
            System.exit(0);
        } catch(IllegalStateException denied) { System.exit(7); }
    }
}
