package com.kitehybrid.synthetic;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.testcontainers.containers.PostgreSQLContainer;

/** Test coordinator authority, outside owner processes/local rollback fixtures. NOT a production root. */
final class FenceWitness implements AutoCloseable {
    enum Fault { NONE, BEFORE_RESERVATION, DURING_RESERVATION, LOST_ACK, BAD_SIGNATURE }
    record Root(KeyPair key, UUID identity, long checkpoint, Set<UUID> retired, String authentication, boolean quarantined) {
        Root { retired=Set.copyOf(retired); }
        @Override public String toString(){return "SYNTHETIC_ROOT_REDACTED";}
    }
    final PostgreSQLContainer<?> db;
    private int dbPort;
    private final KeyPair key;
    private final UUID identity;
    private final String authentication;
    private final Set<UUID> retired=new HashSet<>();
    private long checkpoint;
    private boolean quarantined;
    volatile Fault fault=Fault.NONE;
    volatile boolean available=true;
    private final ServerSocket listener;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Future<?> task;
    private volatile boolean closed;
    private volatile boolean fixtureFailure;

    static Root freshRoot() throws Exception {
        var g=KeyPairGenerator.getInstance("RSA");g.initialize(3072);
        byte[] material=new byte[32];new SecureRandom().nextBytes(material);
        return new Root(g.generateKeyPair(),UUID.randomUUID(),0,Set.of(),HexFormat.of().formatHex(material),false);
    }
    FenceWitness(PostgreSQLContainer<?> db,Root root,boolean initialize) throws Exception {
        this.db=db;dbPort=db.getMappedPort(5432);key=root.key();identity=root.identity();
        checkpoint=root.checkpoint();retired.addAll(root.retired());authentication=root.authentication();
        quarantined=root.quarantined();
        if(initialize)try(var c=open();var s=c.createStatement()) {
            s.execute("CREATE TABLE witness_head (root uuid PRIMARY KEY, seq bigint NOT NULL)");
            s.execute("CREATE TABLE witness_claims (id uuid PRIMARY KEY, binding text NOT NULL)");
            try(var p=c.prepareStatement("INSERT INTO witness_head VALUES (?,0)")){p.setObject(1,identity);p.executeUpdate();}
        }
        listener=new ServerSocket(0,8,InetAddress.getByAddress(new byte[]{127,0,0,1}));listener.setSoTimeout(100);
        task=executor.submit(()->{
            while(!closed)try(var socket=listener.accept()) {
                socket.setSoTimeout(3000);
                var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());
                String auth=readFrame(in),request=readFrame(in);
                String[] receipt=claim(auth,request);writeFrame(out,receipt[0]);writeFrame(out,receipt[1]);out.flush();
            } catch(SocketTimeoutException ignored) { }
            catch(IOException ignored) { /* Missing/partial request or lost response grants nothing to caller. */ }
            catch(Exception ignored) {fixtureFailure=true;}
        });
    }
    synchronized Root retainedRoot(){return new Root(key,identity,checkpoint,retired,authentication,quarantined);}
    synchronized Client client(){return new Client(listener.getLocalPort(),identity,key.getPublic(),authentication,checkpoint);}
    synchronized void mappedPortAfterRestart(int port){dbPort=port;}
    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+dbPort+
            "/synthetic_owner_only?connectTimeout=2&socketTimeout=3","synthetic_fixture","synthetic_fixture");
    }
    private synchronized String[] claim(String auth,String request) {
        String[] denied={"DENIED",""};
        if(!available||quarantined||!MessageDigest.isEqual(authentication.getBytes(StandardCharsets.US_ASCII),auth.getBytes(StandardCharsets.US_ASCII)))return denied;
        try {
            String[] fields=request.split("\\|",-1);if(fields.length!=8)return denied;
            UUID id=UUID.fromString(fields[0]);
            if(!fields[1].matches("[0-9a-f]{64}")||!fields[2].matches("[0-9a-f]{64}")
                ||!SyntheticOwner.syntheticIdentity(fields[3])||!Instant.parse(fields[4]).isAfter(SyntheticSecurityTest.NOW)
                ||Instant.parse(fields[4]).isAfter(SyntheticSecurityTest.NOW.plusSeconds(30))
                ||!fields[5].equals("1")||Long.parseLong(fields[6])!=checkpoint)return denied;
            UUID.fromString(fields[7]);
            if(!retired.add(id))return denied; // Trusted coordinator retirement survives DB/local uncertainty.
            if(fault==Fault.BEFORE_RESERVATION)return denied;
            long previous=checkpoint;
            try(var c=open()) {
                c.setAutoCommit(false);
                try(var s=c.createStatement()) {
                    s.setQueryTimeout(3);
                    for(String setting:List.of("fsync","synchronous_commit","full_page_writes"))
                        try(var r=s.executeQuery("SHOW "+setting)){if(!r.next()||!r.getString(1).equals("on"))throw new SQLException();}
                    try(var r=s.executeQuery("SELECT root,seq FROM witness_head FOR UPDATE")) {
                        if(!r.next()||!identity.equals(r.getObject(1,UUID.class))||r.getLong(2)!=checkpoint||r.next())throw new SQLException();
                    }
                }
                try(var p=c.prepareStatement("INSERT INTO witness_claims VALUES (?,?) ON CONFLICT DO NOTHING")) {
                    p.setQueryTimeout(3);p.setObject(1,id);p.setString(2,request);if(p.executeUpdate()!=1){c.rollback();return denied;}
                }
                if(fault==Fault.DURING_RESERVATION){c.rollback();return denied;}
                try(var p=c.prepareStatement("UPDATE witness_head SET seq=seq+1 WHERE root=? AND seq=?")) {
                    p.setQueryTimeout(3);p.setObject(1,identity);p.setLong(2,checkpoint);if(p.executeUpdate()!=1)throw new SQLException();
                }
                c.commit();checkpoint=previous+1;
            } catch(Exception uncertain){quarantined=true;return denied;}
            if(fault==Fault.LOST_ACK)return denied;
            String payload="SYNTHETIC_FENCE_V1|"+identity+"|"+request+"|"+checkpoint;
            var signer=Signature.getInstance("SHA256withRSA");signer.initSign(key.getPrivate());signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            byte[] signature=signer.sign();if(fault==Fault.BAD_SIGNATURE)signature[0]^=1;
            return new String[]{payload,Base64.getEncoder().encodeToString(signature)};
        } catch(Exception ignored){return denied;}
    }
    static void writeFrame(DataOutputStream out,String value) throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.US_ASCII);if(bytes.length>4096)throw new IOException("SYNTHETIC_FRAME_DENIED");
        out.writeInt(bytes.length);out.write(bytes);
    }
    static String readFrame(DataInputStream in) throws IOException {
        int length=in.readInt();if(length<0||length>4096)throw new IOException("SYNTHETIC_FRAME_DENIED");
        byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.US_ASCII);
    }
    @Override public void close() throws Exception {
        closed=true;listener.close();executor.shutdownNow();
        if(!executor.awaitTermination(5,TimeUnit.SECONDS)||fixtureFailure)throw SyntheticOwner.denied();
        task.get(1,TimeUnit.SECONDS);
    }
    @Override public String toString(){return "SYNTHETIC_WITNESS_REDACTED";}

    static final class Client {
        private final int port;private final UUID root;private final PublicKey publicKey;
        private final String authentication;private final long previous;
        private final Set<UUID> attempted=new HashSet<>();
        Client(int port,UUID root,PublicKey publicKey,String authentication,long previous) {
            this.port=port;this.root=root;this.publicKey=publicKey;this.authentication=authentication;this.previous=previous;
        }
        synchronized boolean claim(SyntheticOwner.Request r,String haltDigest) {
            if(!attempted.add(r.id()))return false;
            String request=r.id()+"|"+r.fingerprint()+"|"+haltDigest+"|"+r.owner()+"|"+r.expires()+"|"+r.haltEpoch()+"|"+previous+"|"+UUID.randomUUID();
            try(var socket=new Socket(Proxy.NO_PROXY)) {
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),port),3000);socket.setSoTimeout(3000);
                var out=new DataOutputStream(socket.getOutputStream());writeFrame(out,authentication);writeFrame(out,request);out.flush();
                var in=new DataInputStream(socket.getInputStream());String payload=readFrame(in),encoded=readFrame(in);
                if(!payload.equals("SYNTHETIC_FENCE_V1|"+root+"|"+request+"|"+(previous+1)))return false;
                var signature=Signature.getInstance("SHA256withRSA");signature.initVerify(publicKey);
                signature.update(payload.getBytes(StandardCharsets.US_ASCII));return signature.verify(Base64.getDecoder().decode(encoded));
            } catch(Exception ignored){return false;}
        }
        void bootstrap(DataOutputStream out) throws IOException {
            out.writeInt(port);writeFrame(out,root.toString());writeFrame(out,Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            writeFrame(out,authentication);out.writeLong(previous);out.flush();
        }
        static Client bootstrap(DataInputStream in) throws Exception {
            int port=in.readInt();UUID root=UUID.fromString(readFrame(in));
            var key=KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(readFrame(in))));
            return new Client(port,root,key,readFrame(in),in.readLong());
        }
        @Override public String toString(){return "SYNTHETIC_FENCE_CLIENT_REDACTED";}
    }
}
