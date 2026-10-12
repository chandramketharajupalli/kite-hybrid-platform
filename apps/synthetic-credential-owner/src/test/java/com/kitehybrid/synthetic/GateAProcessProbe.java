package com.kitehybrid.synthetic;

import java.util.UUID;

/** Test JVM only; no packaged launcher and no network or credential capability. */
final class GateAProcessProbe {
    public static void main(String[] args) {
        var journal=new AttemptJournal(args[0],stage->{if(stage.equals(args[2]))Runtime.getRuntime().halt(23);});
        var boundary=new GateABoundary(journal,GateABoundaryTest.CLOCK);
        try {
            boundary.acquire("GET",GateABoundary.URL,null,GateABoundaryTest.approval(UUID.fromString(args[1])),
                    "fixture-owner",GateABoundaryTest.response());
            System.exit(0);
        } catch (IllegalStateException denied) { System.exit(7); }
    }
}
