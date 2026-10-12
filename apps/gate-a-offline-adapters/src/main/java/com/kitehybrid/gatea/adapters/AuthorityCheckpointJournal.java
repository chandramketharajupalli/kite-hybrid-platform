package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Bounded synthetic recovery model. Separate directories are NOT independent administrators. */
public final class AuthorityCheckpointJournal {
    public enum State { UNINITIALIZED, ACTIVE_SYNTHETIC, CONSUMED, REVOKED, QUARANTINED, RECOVERY_PENDING }
    public record Snapshot(State state,long epoch,long highestEpoch,long sequence,Instant observed,
                           boolean consumed,String digest) {
        @Override public String toString(){return "AUTHORITY_CHECKPOINT_REDACTED";}
    }
    private final Path local,witness;
    private final String binding,scope;
    private final long initialEpoch;
    private final Instant initialTime;
    private final Consumer<String> probe;
    public AuthorityCheckpointJournal(Path local,Path witness,Authorization a) {
        this(local,witness,a,event->{});
    }
    AuthorityCheckpointJournal(Path local,Path witness,Authorization a,Consumer<String> probe) {
        try {
            this.local=local.toAbsolutePath().normalize();this.witness=witness.toAbsolutePath().normalize();
            if(this.local.startsWith(this.witness)||this.witness.startsWith(this.local)||probe==null||a.epoch()<1)throw denied();
            scope=Base64.getEncoder().encodeToString(SignedHaltAuthority.authorizationBytes(a));
            binding=hash(scope);initialEpoch=a.epoch();initialTime=a.issued();this.probe=probe;
        }catch(Exception invalid){throw denied();}
    }
    public void initializeFixture() {
        try {
            Files.createDirectory(local);Files.createDirectory(witness);
            write(local.resolve("identity"),binding);write(witness.resolve("identity"),binding);
            write(witness.resolve("lock"),binding);
            String row=encode(State.ACTIVE_SYNTHETIC,initialEpoch,initialEpoch,0,initialTime,false,"ROOT");
            write(witness.resolve(name(0)),row);write(local.resolve(name(0)),row);
        }catch(Exception invalid){throw denied();}
    }
    public Snapshot snapshot() {
        if(Files.notExists(local)&&Files.notExists(witness))return unavailable(State.UNINITIALIZED);
        try(var channel=openLock();var lock=channel.lock()){return paired();}
        catch(Exception failure){return unavailable(State.RECOVERY_PENDING);}
    }
    /** Compare-and-advance. No automatic recovery, truncation, retry or reactivation. */
    public boolean advance(long expected,State target,long epoch,Instant now) {
        try(var channel=openLock();var lock=channel.lock()){
            Snapshot old=paired();
            if(old.sequence()!=expected||old.sequence()>=255||epoch<old.highestEpoch()||now.isBefore(old.observed())
                    ||!allowed(old.state(),target))return false;
            boolean consumed=old.consumed()||target==State.CONSUMED;
            String row=encode(target,epoch,Math.max(epoch,old.highestEpoch()),old.sequence()+1,now,consumed,old.digest());
            probe.accept("BEFORE_RESERVATION");
            write(witness.resolve(name(old.sequence()+1)),row);probe.accept("AFTER_WITNESS");
            write(local.resolve(name(old.sequence()+1)),row);probe.accept("AFTER_LOCAL");
            return true;
        }catch(Exception uncertain){return false;}
    }
    public boolean bound(Authorization a) {
        try{return scope.equals(Base64.getEncoder().encodeToString(SignedHaltAuthority.authorizationBytes(a)));}
        catch(Exception invalid){return false;}
    }
    private FileChannel openLock() throws Exception {
        validDirectory(local);validDirectory(witness);
        Path lock=witness.resolve("lock");
        if(!Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS)||Files.size(lock)!=64||!Files.readString(lock).equals(binding))throw denied();
        return FileChannel.open(lock,StandardOpenOption.WRITE);
    }
    private void validDirectory(Path directory) throws Exception {
        for(Path p=directory;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw denied();
        Path id=directory.resolve("identity");
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(id,LinkOption.NOFOLLOW_LINKS)
                ||Files.size(id)!=64||!Files.readString(id).equals(binding))throw denied();
    }
    private Snapshot paired() throws Exception {
        Snapshot a=read(local),b=read(witness);if(!a.equals(b))throw denied();return a;
    }
    private Snapshot read(Path directory) throws Exception {
        List<Path> files;
        try(var stream=Files.list(directory)){
            files=stream.filter(p->!p.getFileName().toString().equals("identity")&&!p.getFileName().toString().equals("lock"))
                    .limit(257).sorted().toList();
        }
        if(files.isEmpty()||files.size()>256)throw denied();
        Snapshot previous=null;
        for(int i=0;i<files.size();i++){
            Path p=files.get(i);
            if(!p.getFileName().toString().equals(name(i))||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>8192)throw denied();
            String raw=Files.readString(p,StandardCharsets.US_ASCII);String[] f=raw.split("\n",-1);
            if(f.length!=13||!f[0].equals("GATE_A_RECOVERY_V1")||!f[1].equals(scope)||!f[10].equals("SYNTHETIC_FIXTURE_ONLY")||!f[12].isEmpty())throw denied();
            String payload=raw.substring(0,raw.lastIndexOf(f[11]+"\n"));if(!hash(payload).equals(f[11]))throw denied();
            long epoch=Long.parseLong(f[2]),high=Long.parseLong(f[3]),seq=Long.parseLong(f[4]);
            State state=State.valueOf(f[5]);Instant time=Instant.parse(f[6]);
            if(!Set.of("true","false").contains(f[7])||!f[9].equals(binding))throw denied();
            boolean consumed=Boolean.parseBoolean(f[7]);
            if(seq!=i||epoch<1||high<epoch||state==State.RECOVERY_PENDING||state==State.UNINITIALIZED)throw denied();
            if(previous==null){if(state!=State.ACTIVE_SYNTHETIC||epoch!=initialEpoch||high!=initialEpoch||consumed||!time.equals(initialTime)||!f[8].equals("ROOT"))throw denied();}
            else if(!f[8].equals(previous.digest())||!allowed(previous.state(),state)||time.isBefore(previous.observed())
                    ||epoch<previous.highestEpoch()||high!=Math.max(epoch,previous.highestEpoch())
                    ||consumed!=(previous.consumed()||state==State.CONSUMED))throw denied();
            previous=new Snapshot(state,epoch,high,seq,time,consumed,f[11]);
        }
        return previous;
    }
    private static boolean allowed(State from,State to){
        return switch(from){
            case ACTIVE_SYNTHETIC -> Set.of(State.ACTIVE_SYNTHETIC,State.CONSUMED,State.REVOKED,State.QUARANTINED).contains(to);
            case CONSUMED -> Set.of(State.CONSUMED,State.REVOKED,State.QUARANTINED).contains(to);
            case REVOKED -> to==State.REVOKED||to==State.QUARANTINED;
            case QUARANTINED -> to==State.QUARANTINED;
            default -> false;
        };
    }
    private String encode(State state,long epoch,long high,long seq,Instant time,boolean consumed,String previous){
        String payload=String.join("\n","GATE_A_RECOVERY_V1",scope,Long.toString(epoch),Long.toString(high),Long.toString(seq),
                state.name(),time.toString(),Boolean.toString(consumed),previous,binding,"SYNTHETIC_FIXTURE_ONLY")+"\n";
        return payload+hash(payload)+"\n";
    }
    private static void write(Path p,String value)throws Exception{
        try(var ch=FileChannel.open(p,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            var b=ByteBuffer.wrap(value.getBytes(StandardCharsets.US_ASCII));while(b.hasRemaining())ch.write(b);ch.force(true);
        }
    }
    private static String name(long seq){return String.format(Locale.ROOT,"%020d.record",seq);}
    private static String hash(String text){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII)));}
        catch(Exception invalid){throw denied();}
    }
    private static Snapshot unavailable(State state){return new Snapshot(state,0,0,-1,Instant.EPOCH,false,"");}
    @Override public String toString(){return "AUTHORITY_JOURNAL_REDACTED";}
    private static IllegalStateException denied(){return new IllegalStateException("GATE_A_DENIED");}
}
