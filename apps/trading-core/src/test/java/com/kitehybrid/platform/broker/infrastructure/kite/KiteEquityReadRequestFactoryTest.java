package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KiteEquityReadRequestFactoryTest {
    static final Instant NOW=Instant.parse("2026-10-10T05:00:00Z");
    static KiteSession session(Clock clock) {
        var session=new KiteSession(new KiteProperties("syntheticKey","","",true),clock);
        session.install(new KiteAccessToken("syntheticToken",NOW.minusSeconds(1),NOW.plusSeconds(600)));
        session.profileValidated(); return session;
    }
    @ParameterizedTest @ValueSource(strings={"https://evil.invalid","http://localhost:1234","http://127.0.0.1",
            "http://127.0.0.1:1234/","http://127.0.0.1:1234?x=1","http://x@127.0.0.1:1234","http://127.0.0.1:1234#x"})
    void syntheticOriginCannotEscapeLiteralLoopback(String origin) {
        assertThrows(BrokerReadException.class,()->KiteEquityReadRequestFactory.loopback(URI.create(origin),Duration.ofSeconds(1)));
    }
    @ParameterizedTest @ValueSource(strings={"POST","PUT","DELETE","/orders","/margins/orders","/user/profile",
            "/user/margins/equity?x=1","/user/margins/equity#x","host","scheme","port"})
    void wrongMethodOrExactUriDeniedBeforeDispatch(String fault) throws Exception {
        try(var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:1234"),Duration.ofSeconds(1))) {
            var uri=wire.origin().resolve(KiteEquityReadRequestFactory.PATH); var method=HttpMethod.GET;
            if(fault.startsWith("/")) uri=wire.origin().resolve(fault);
            else if(fault.equals("host")) uri=URI.create("http://evil.invalid:1234/user/margins/equity");
            else if(fault.equals("scheme")) uri=URI.create("https://127.0.0.1:1234/user/margins/equity");
            else if(fault.equals("port")) uri=URI.create("http://127.0.0.1:1235/user/margins/equity");
            else method=HttpMethod.valueOf(fault);
            var deniedUri=uri;var deniedMethod=method;
            assertThrows(BrokerReadException.class,()->wire.createRequest(deniedUri,deniedMethod));
            assertThat(wire.attempts()).isZero();
            assertThrows(BrokerReadException.class,()->wire.createRequest(wire.origin().resolve(KiteEquityReadRequestFactory.PATH),HttpMethod.GET));
        }
    }
    @ParameterizedTest @ValueSource(ints={200,302,401,403,429,500,503})
    void oneLoopbackRequestNoRedirectOrRetry(int status) throws Exception {
        var calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().toString()).isEqualTo(KiteEquityReadRequestFactory.PATH);
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("token syntheticKey:syntheticToken");
            if(status==302)exchange.getResponseHeaders().set("Location","/orders");
            var body=KiteTradingReadFixtures.envelope(KiteTradingReadFixtures.SEGMENT).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try(var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(1))) {
            var clock=Clock.fixed(NOW,ZoneOffset.UTC);var session=session(clock);
            var adapter=new KiteEquityMarginReadAdapter(KiteRestTransport.controlledEquity(session,wire),session,clock,true);
            if(status==200) assertThat(adapter.read().receivedAt()).isEqualTo(NOW);
            else assertThrows(BrokerReadException.class,adapter::read);
            assertThrows(BrokerReadException.class,adapter::read);
            assertThat(wire.attempts()).isEqualTo(1);assertThat(calls).hasValue(1);
        }finally{server.stop(0);}
    }
    @Test void lostResponseDoesNotRepeatGetOnAnotherConnection() throws Exception {
        try(var socket=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))) {
            socket.setSoTimeout(600); var calls=new AtomicInteger();
            var peer=Thread.ofPlatform().start(()->{try {
                while(true)try(var accepted=socket.accept()){
                    var reader=new BufferedReader(new InputStreamReader(accepted.getInputStream()));
                    for(String line=reader.readLine();line!=null&&!line.isEmpty();line=reader.readLine()){}
                    calls.incrementAndGet(); // Close without headers after receiving the GET.
                }
            }catch(IOException finished){}});
            try(var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:"+socket.getLocalPort()),Duration.ofMillis(200))) {
                var clock=Clock.fixed(NOW,ZoneOffset.UTC);var session=session(clock);
                assertThrows(BrokerReadException.class,()->new KiteEquityMarginReadAdapter(
                        KiteRestTransport.controlledEquity(session,wire),session,clock,true).read());
                peer.join(2000);assertThat(peer.isAlive()).isFalse();assertThat(calls).hasValue(1);
            }
        }
    }
    @Test void officialFactoryIsFixedAndCreatingItMakesNoRequest() {
        try(var wire=KiteEquityReadRequestFactory.official()) {
            assertThat(wire.origin()).isEqualTo(URI.create("https://api.kite.trade"));
            assertThat(wire.officialOrigin()).isTrue();assertThat(wire.attempts()).isZero();
        }
    }
    @Test void officialFactoriesShareProcessBudgetWithoutExecutingAnyRequest() throws Exception {
        try(var first=KiteEquityReadRequestFactory.official();var second=KiteEquityReadRequestFactory.official()) {
            // Creating a request object is not execute(): no socket or real request is made.
            first.createRequest(KiteEquityReadRequestFactory.OFFICIAL.resolve(KiteEquityReadRequestFactory.PATH),HttpMethod.GET);
            assertThrows(BrokerReadException.class,()->second.createRequest(
                    KiteEquityReadRequestFactory.OFFICIAL.resolve(KiteEquityReadRequestFactory.PATH),HttpMethod.GET));
        }
    }
}
