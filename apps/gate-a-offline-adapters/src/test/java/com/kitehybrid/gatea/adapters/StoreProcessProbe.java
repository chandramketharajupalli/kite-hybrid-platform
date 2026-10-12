package com.kitehybrid.gatea.adapters;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.kitehybrid.gatea.adapters.Fixtures.*;

/** Test-only child. No real authority or dispatch. Arguments contain only owned fixture paths/events. */
public final class StoreProcessProbe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);String crash=args[1];
        var store=new FileAttemptAuthority(root,STORE,AUTH,event->{if(event.equals(crash))Runtime.getRuntime().halt(23);});
        var p=policy(store,authority(AUTH,new AtomicReference<>(state(1,false))),parser(CSV));
        try {
            observe(p);
            if(crash.equals("AFTER_OBSERVATION"))Runtime.getRuntime().halt(23);
            System.out.println("SYNTHETIC_OBSERVATION_ACCEPTED");
        } catch(Exception denied) { System.out.println("SYNTHETIC_OBSERVATION_DENIED");System.exit(42); }
    }
}
