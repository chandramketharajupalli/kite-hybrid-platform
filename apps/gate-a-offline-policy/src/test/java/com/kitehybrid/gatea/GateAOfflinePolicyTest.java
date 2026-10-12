package com.kitehybrid.gatea;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.gatea.GateAOfflinePolicy.*;

class GateAOfflinePolicyTest {
    static final Instant NOW=Instant.parse("2026-01-01T00:00:00Z");
    static final class Time extends Clock {
        Instant now=NOW; public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;}
    }
    static final class Store implements AttemptAuthority {
        final Set<UUID> spent=new HashSet<>(); boolean available=true, uncertain, fail; Runnable after=()->{};
        public boolean available(Authorization a){return available;}
        public synchronized Consumption consume(Authorization a){
            if(fail)throw new IllegalStateException("SYNTHETIC_PRIVATE_VALUE");
            if(!spent.add(a.attempt()))return Consumption.DENIED; after.run();
            return uncertain?Consumption.UNCERTAIN:Consumption.ACKNOWLEDGED;
        }
        public void recordOutcome(Authorization a,String outcome){}
    }
    Time time=new Time(); Store store=new Store(); boolean allowed=true; long epoch=1;
    Authorization a=new Authorization(UUID.randomUUID(),OPERATION,"GET",ENDPOINT,"process","key",1,NOW,NOW.plusSeconds(20),"SYNTHETIC_OWNER_APPROVAL");
    GateAOfflinePolicy policy(){return new GateAOfflinePolicy("process","key",time,(x,t)->allowed&&x.epoch()==epoch,store,b->true);}
    FixtureResponse response(){return new FixtureResponse("synthetic,csv\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),FixtureOutcome.OK);}
    Receipt send(GateAOfflinePolicy p){return p.observeFixture("GET",ENDPOINT,null,a,response());}
    @Test void sequencingReplayAndRestart(){var p=policy();assertTrue(send(p).synthetic());assertEquals(1,p.syntheticSends());assertThrows(IllegalStateException.class,()->send(p));assertThrows(IllegalStateException.class,()->send(policy()));}
    @ParameterizedTest @ValueSource(strings={"POST","http://api.kite.trade/instruments/NSE","https://api.kite.trade:443/instruments/NSE","https://other.invalid/instruments/NSE","/instruments/NSE","https://api.kite.trade/instruments/NSE?x=1","https://api.kite.trade/instruments/NSE#x","https://x@api.kite.trade/instruments/NSE","https://api.kite.trade/instruments/./NSE","https://api.kite.trade/instruments/%4eSE","https://api.kite.trade/orders","https://api.kite.trade/instruments/historical/1/5minute","wss://api.kite.trade/instruments/NSE","proxy"})
    void routes(String x){var p=policy();assertThrows(IllegalStateException.class,()->p.observeFixture(x.equals("POST")?x:"GET",x.equals("POST")||x.equals("proxy")?ENDPOINT:x,x.equals("proxy")?"DIRECT":null,a,response()));assertEquals(0,p.syntheticSends());assertTrue(store.spent.isEmpty());}
    @ParameterizedTest @ValueSource(strings={"missing","revoked","invalidSignature","expired","future","process","key","epoch","owner","operation"})
    void authorization(String x){
        if(x.equals("missing"))a=null;
        else if(x.equals("revoked")||x.equals("invalidSignature"))allowed=false;
        else if(x.equals("expired"))time.now=NOW.plusSeconds(20);
        else if(x.equals("future"))time.now=NOW.minusSeconds(1);
        else a=new Authorization(a.attempt(),x.equals("operation")?"GATE_B":OPERATION,"GET",ENDPOINT,x.equals("process")?"other":"process",x.equals("key")?"other":"key",x.equals("epoch")?2:1,NOW,NOW.plusSeconds(20),x.equals("owner")?null:"SYNTHETIC_OWNER_APPROVAL");
        var p=policy();assertThrows(IllegalStateException.class,()->send(p));assertEquals(0,p.syntheticSends());
    }
    @ParameterizedTest @ValueSource(strings={"revoke","epoch","expiry","rollback"})
    void afterConsume(String x){var p=policy();store.after=()->{switch(x){case "revoke"->allowed=false;case "epoch"->epoch=2;case "expiry"->time.now=NOW.plusSeconds(20);default->time.now=NOW.minusSeconds(1);}};assertThrows(IllegalStateException.class,()->send(p));assertEquals(0,p.syntheticSends());assertTrue(store.spent.contains(a.attempt()));}
    @ParameterizedTest @ValueSource(strings={"missingStore","uncertain","failedPersistence"})
    void persistence(String x){store.available=!x.equals("missingStore");store.uncertain=x.equals("uncertain");store.fail=x.equals("failedPersistence");var p=policy();var e=assertThrows(IllegalStateException.class,()->send(p));assertNull(e.getCause());assertEquals("GATE_A_DENIED",e.getMessage());assertEquals(0,p.syntheticSends());assertThrows(IllegalStateException.class,()->send(p));}
    @ParameterizedTest @ValueSource(strings={"REDIRECT","TIMEOUT","UNCERTAIN","SERVER_ERROR"})
    void uncertainOutcome(String x){var p=policy();assertThrows(IllegalStateException.class,()->p.observeFixture("GET",ENDPOINT,null,a,new FixtureResponse(new byte[]{65},FixtureOutcome.valueOf(x))));assertEquals(1,p.syntheticSends());assertThrows(IllegalStateException.class,()->send(policy()));}
    @Test void invalidEncodingSizeAndValidator(){assertThrows(IllegalStateException.class,()->new FixtureResponse(new byte[LIMIT+1],FixtureOutcome.OK));var p=policy();assertThrows(IllegalStateException.class,()->p.observeFixture("GET",ENDPOINT,null,a,new FixtureResponse(new byte[]{(byte)0xff},FixtureOutcome.OK)));var q=new GateAOfflinePolicy("process","key",time,(x,t)->true,new Store(),b->false);assertThrows(IllegalStateException.class,()->send(q));}
    @Test void concurrentInstancesShareAuthority() throws Exception {try(var pool=Executors.newFixedThreadPool(2)){Callable<Boolean> c=()->{try{send(policy());return true;}catch(IllegalStateException e){return false;}};var x=pool.submit(c);var y=pool.submit(c);assertNotEquals(x.get(),y.get());}}
    @Test void rollbackQuarantineAndCompleteAuthorityCounterexample(){var p=policy();send(p);time.now=NOW.minusSeconds(1);assertThrows(IllegalStateException.class,()->send(p));time.now=NOW;store.spent.clear();assertThrows(IllegalStateException.class,()->send(p));assertTrue(send(policy()).synthetic());}
    @Test void noRuntimeNetworkOrSerializationSurface() throws Exception {assertFalse(java.io.Serializable.class.isAssignableFrom(Authorization.class));assertEquals("GATE_A_AUTHORIZATION_REDACTED",a.toString());assertThrows(IllegalStateException.class,()->new GateAOfflinePolicy("process","key",time,null,store,b->true));var root=java.nio.file.Path.of(GateAOfflinePolicy.class.getProtectionDomain().getCodeSource().getLocation().toURI());try(var paths=java.nio.file.Files.walk(root)){for(var p:paths.filter(x->x.toString().endsWith(".class")).toList()){String bytes=new String(java.nio.file.Files.readAllBytes(p),java.nio.charset.StandardCharsets.ISO_8859_1);for(String forbidden:List.of("java/net/","java/sql/","org/springframework","com/kitehybrid/platform","getenv","java/io/Serializable"))assertFalse(bytes.contains(forbidden));}}}
}
