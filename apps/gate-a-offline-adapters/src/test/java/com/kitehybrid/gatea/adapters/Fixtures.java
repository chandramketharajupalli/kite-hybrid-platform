package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import com.kitehybrid.platform.instrument.application.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class Fixtures {
    static final Instant NOW=Instant.parse("2026-10-12T10:00:00Z"); // Fixed synthetic clock, not observation evidence.
    static final UUID STORE=UUID.fromString("00000000-0000-0000-0000-000000000012");
    static final Authorization AUTH=new Authorization(UUID.fromString("00000000-0000-0000-0000-000000000013"),
            GateAOfflinePolicy.OPERATION,"GET",GateAOfflinePolicy.ENDPOINT,"fixture-process","fixture-key",1,
            NOW,NOW.plusSeconds(20),"fixture-owner-reference");
    static final String HEADER="instrument_token,tradingsymbol,exchange,segment,instrument_type,expiry,strike,tick_size,lot_size\n";
    static final String CSV=HEADER+"101,SYNTH&A,NSE,NSE,EQ,,0,0.05,1\n";
    static final KeyPair KEYS=keys();
    static KeyPair keys() {
        try { var g=KeyPairGenerator.getInstance("RSA");g.initialize(3072);return g.generateKeyPair(); }
        catch(Exception e){throw new AssertionError(e);}
    }
    static byte[] sign(byte[] bytes,KeyPair key) {
        try { var s=Signature.getInstance("SHA256withRSA");s.initSign(key.getPrivate());s.update(bytes);return s.sign(); }
        catch(Exception e){throw new AssertionError(e);}
    }
    static SignedHaltAuthority.State state(long epoch,boolean revoked) {
        return new SignedHaltAuthority.State(epoch,revoked,false,NOW,NOW.plusSeconds(5),"fixture-key","fixture-process","fixture-owner-reference");
    }
    static SignedHaltAuthority.Evidence<SignedHaltAuthority.State> signed(SignedHaltAuthority.State s) {
        return new SignedHaltAuthority.Evidence<>(s,sign(SignedHaltAuthority.stateBytes(s),KEYS));
    }
    static SignedHaltAuthority authority(Authorization a,AtomicReference<SignedHaltAuthority.State> state) {
        return new SignedHaltAuthority(KEYS.getPublic(),"fixture-key","fixture-process","fixture-owner-reference",1,
                new SignedHaltAuthority.Evidence<>(a,sign(SignedHaltAuthority.authorizationBytes(a),KEYS)),()->signed(state.get()));
    }
    static NseMasterAdapter parser(String csv) { return parser(csv.getBytes(StandardCharsets.UTF_8)); }
    static NseMasterAdapter parser(byte[] csv) {
        try {
            return new NseMasterAdapter(new DatedInstrumentMaster.Provenance("synthetic-fixture","fixture/master",NOW,
                    Optional.empty(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csv)),csv.length,
                    "kite-instruments-csv-v3",true),List.of(new OfflineUniverseResolution.Source("NSE","SYNTH&A",true),
                    new OfflineUniverseResolution.Source("NSE","MISSING",true)),NOW);
        } catch(Exception e){throw new AssertionError(e);}
    }
    static GateAOfflinePolicy policy(FileAttemptAuthority store,SignedHaltAuthority halt,NseMasterAdapter parser) {
        return new GateAOfflinePolicy("fixture-process","fixture-key",Clock.fixed(NOW,ZoneOffset.UTC),halt,store,parser);
    }
    static GateAOfflinePolicy.FixtureResponse response() {
        return new GateAOfflinePolicy.FixtureResponse(CSV.getBytes(StandardCharsets.UTF_8),GateAOfflinePolicy.FixtureOutcome.OK);
    }
    static GateAOfflinePolicy.Receipt observe(GateAOfflinePolicy p) {
        return p.observeFixture("GET",GateAOfflinePolicy.ENDPOINT,null,AUTH,response());
    }
}
