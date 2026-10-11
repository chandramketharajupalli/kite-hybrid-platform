package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Disposable diagnostic fault injector. Never included in the owner runtime JAR. */
public final class DiagnosticsProbe {
    static byte[] secret;
    static byte[][] pressure;
    static byte[] canary() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest("SYNTHETIC_PHASE1312_DIAGNOSTIC_ONLY".getBytes(StandardCharsets.US_ASCII));
    }
    static void require(boolean value) { if (!value) throw new IllegalStateException("SYNTHETIC_DIAGNOSTIC_DENIED"); }
    static String read(String path) {
        try { return Files.readString(Path.of(path)).strip().replace('\n',';'); }
        catch (IOException unavailable) { return "UNAVAILABLE"; }
    }
    static boolean contains(Path path, byte[] value) throws Exception {
        int matched=0;
        try(var in=new BufferedInputStream(Files.newInputStream(path))) {
            int b; while((b=in.read())!=-1) {
                if(b==(value[matched]&255)) { if(++matched==value.length)return true; }
                else matched=b==(value[0]&255)?1:0;
            }
        }
        return false;
    }
    static void scan() throws Exception {
        long bytes=0;int count=0;
        try(var files=Files.list(Path.of("/scratch"))) {
            for(Path file:files.sorted().toList()) {
                require(Files.isRegularFile(file));require(++count<=6);
                long size=Files.size(file);bytes+=size;require(bytes<=48L*1024*1024);
                boolean hit=contains(file,canary()) || contains(file,HexFormat.of().formatHex(canary()).getBytes(StandardCharsets.US_ASCII))
                        || contains(file,Base64.getEncoder().encode(canary()));
                // Only the deliberately created heap is allowed to contain the canary.
                require(!hit || file.getFileName().toString().equals("heap.hprof"));
                var digest=MessageDigest.getInstance("SHA-256");
                try(var in=Files.newInputStream(file)) { byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n); }
                System.out.println("ARTIFACT="+file.getFileName()+";BYTES="+size+";SHA256="+HexFormat.of().formatHex(digest.digest())+";CANARY_PRESENT="+hit);
            }
        }
        System.out.println("ARTIFACT_COUNT="+count);
    }
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((t,e)->System.err.println("SYNTHETIC_DIAGNOSTIC_REDACTED"));
        try {
            switch(args[0]) {
                case "MEASURE" -> {
                    System.out.println("UID_MAP="+read("/proc/self/uid_map"));
                    System.out.println("GID_MAP="+read("/proc/self/gid_map"));
                    System.out.println("LSM_LABEL="+read("/proc/self/attr/current"));
                    System.out.println("LSM_LIST="+read("/sys/kernel/security/lsm"));
                    System.out.println("APPARMOR_ENABLED="+read("/sys/module/apparmor/parameters/enabled"));
                    System.out.println("SELINUX_ENFORCE="+read("/sys/fs/selinux/enforce"));
                    System.out.println("JAVA="+Runtime.version());
                    require(ModuleLayer.boot().modules().size()==1);
                    require(ModuleLayer.boot().findModule("jdk.jfr").isEmpty());
                    System.out.println("JFR_MODULE=ABSENT_FROM_RESTRICTED_RUNTIME");
                }
                case "HOLD" -> { secret=canary();System.out.println("DIAGNOSTIC_READY");Thread.sleep(55000); }
                case "OOM_ALLOWED", "OOM_DENIED" -> {
                    secret=canary();pressure=new byte[40][];
                    try {for(int i=0;i<pressure.length;i++)pressure[i]=new byte[1024*1024];throw new IllegalStateException("OOM_NOT_TRIGGERED");}
                    catch(OutOfMemoryError expected) { pressure=null;System.gc();System.out.println("BOUNDED_OOM_OBSERVED"); }
                    if(args[0].equals("OOM_ALLOWED")) { require(Files.size(Path.of("/scratch/heap.hprof"))>0);require(contains(Path.of("/scratch/heap.hprof"),secret)); }
                    else { require(!Files.exists(Path.of("/forbidden.hprof")));require(!Files.exists(Path.of("/scratch/heap.hprof"))); }
                }
                case "CRASH" -> {
                    secret=canary();
                    // Explicit test-only module opening; never applied to SyntheticOwner.
                    Class<?> type=Class.forName("jdk.internal.misc.Unsafe");var field=type.getDeclaredField("theUnsafe");field.setAccessible(true);
                    Object unsafe=field.get(null);type.getMethod("putLong",long.class,long.class).invoke(unsafe,0L,0L);
                    throw new IllegalStateException("CRASH_NOT_TRIGGERED");
                }
                case "SCAN" -> scan();
                default -> throw new IllegalArgumentException("SYNTHETIC_MODE_DENIED");
            }
        } catch(Exception failure) {System.err.println("SYNTHETIC_DIAGNOSTIC_FAILED_CLOSED");System.exit(45);}
    }
}
