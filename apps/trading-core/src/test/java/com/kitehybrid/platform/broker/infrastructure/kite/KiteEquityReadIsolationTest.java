package com.kitehybrid.platform.broker.infrastructure.kite;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KiteEquityReadIsolationTest {
    @TempDir Path temporary;
    public static class LockPeer {
        public static void main(String[] args)throws Exception{
            try(var channel=FileChannel.open(Path.of(args[0]),StandardOpenOption.WRITE,StandardOpenOption.CREATE)){
                var lock=channel.tryLock();System.out.println(lock==null?"DENIED":"HELD");System.out.flush();
                if(lock!=null){System.in.read();lock.release();}
            }
        }
    }
    Process peer(Path path)throws Exception{
        var java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        return new ProcessBuilder(java,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                LockPeer.class.getName(),path.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    @Test void independentChildCannotTakeHeldFixtureLockAndCrashReleasesIt()throws Exception{
        var path=temporary.resolve("fixture.lock");
        try(var channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.CREATE);var lock=channel.lock()){
            var clock=new KiteEquityReadHarnessTest.MutableClock();
            try(var lease=new KiteEquityReadIsolation(clock,Duration.ofSeconds(10),lock::isValid)){
                assertThat(lease.valid()).isTrue();
                var child=peer(path);
                try{assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();
                    assertThat(new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim()).isEqualTo("DENIED");
                }finally{child.destroyForcibly();}
                lock.release();assertThat(lease.valid()).isFalse();
            }
        }
        var child=peer(path);
        try{
            var line=CompletableRead.line(child);
            assertThat(line).isEqualTo("HELD");
            try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){assertThat(channel.tryLock()).isNull();}
        }finally{child.destroyForcibly();assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();}
        try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){
            FileLock recovered=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(recovered==null && System.nanoTime()<deadline){recovered=channel.tryLock();if(recovered==null)Thread.sleep(10);}
            assertThat(recovered).isNotNull();recovered.release();
        }
    }
    static class CompletableRead {
        static String line(Process child)throws Exception{
            try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
                var line=pool.submit(()->new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream())).readLine());
                try { return line.get(10,TimeUnit.SECONDS); }
                catch (Exception failed) { child.destroyForcibly(); throw failed; }
            }
        }
    }
    @Test void lossBetweenRequestCreationAndExecuteStopsHttp()throws Exception{
        try(var f=new KiteEquityReadHandoffTest.Fixture()){
            var request=f.base.wire.createRequest(f.base.wire.origin().resolve("/user/margins/equity"),org.springframework.http.HttpMethod.GET);
            f.witness.set(false);
            assertThatThrownBy(request::execute).isInstanceOf(RuntimeException.class);
            assertThat(f.base.calls).hasValue(0);
            assertThatThrownBy(()->f.base.wire.createRequest(request.getURI(),org.springframework.http.HttpMethod.GET)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void sameRequestObjectCannotExecuteAgainAfterResponseLoss()throws Exception{
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var peer=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/",exchange->{calls.incrementAndGet();exchange.close();});peer.start();
        try(var wire=KiteEquityReadRequestFactory.loopback(java.net.URI.create("http://127.0.0.1:"+peer.getAddress().getPort()),Duration.ofMillis(300))){
            var request=wire.createRequest(wire.origin().resolve("/user/margins/equity"),org.springframework.http.HttpMethod.GET);
            assertThatThrownBy(request::execute).isInstanceOf(java.io.IOException.class);
            assertThatThrownBy(request::execute).isInstanceOf(RuntimeException.class);
            assertThat(calls).hasValue(1);
        }finally{peer.stop(0);}
    }

}
