package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

final class KiteMarketDataTestSupport {
    static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    static final Instrument A = instrument(408065, "INFY");
    static final Instrument B = instrument(884737, "SECOND");
    static Instrument instrument(int token, String symbol) {
        return Instrument.create(new BrokerInstrumentId(KiteBrokerIdentity.BROKER_ID, Integer.toUnsignedString(token)), symbol,
                "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    }
    static KiteMarketDataProperties properties(boolean enabled, int capacity, int attempts) {
        return new KiteMarketDataProperties(enabled, false, Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(30), capacity, 3000,
                new KiteMarketDataProperties.Reconnect(Duration.ofSeconds(1), Duration.ofSeconds(8), attempts));
    }
    static byte[] ltp(int token, int price) { return frame(ByteBuffer.allocate(8).putInt(token).putInt(price).array()); }
    static byte[] full(int token, int price, Instant exchangeTime) {
        var packet = ByteBuffer.allocate(184).putInt(token).putInt(price);
        packet.putInt(60, (int) exchangeTime.getEpochSecond());
        return frame(packet.array());
    }
    static byte[] frame(byte[]... packets) {
        int size = 2;
        for (byte[] packet : packets) size += 2 + packet.length;
        var buffer = ByteBuffer.allocate(size).putShort((short) packets.length);
        for (byte[] packet : packets) buffer.putShort((short) packet.length).put(packet);
        return buffer.array();
    }

    static final class TestRig implements AutoCloseable {
        final MutableClock clock = new MutableClock();
        final ManualScheduler scheduler = new ManualScheduler();
        final ManualExecutor worker = new ManualExecutor();
        final FakeTransport transport = new FakeTransport();
        final InMemoryInstrumentRegistry registry = new InMemoryInstrumentRegistry();
        final LatestMarketDataStore store;
        final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        final KiteSession session = new KiteSession(new KiteProperties("dummykey", "dummysecret", "obsolete", true), clock);
        final KiteMarketDataAdapter gateway;
        TestRig() { this(16, 3, null); }
        TestRig(int capacity, int attempts, LatestMarketDataStore override) { this(properties(true, capacity, attempts), override); }
        TestRig(KiteMarketDataProperties settings, LatestMarketDataStore override) {
            store = override == null ? new InMemoryLatestMarketDataStore() : override;
            registry.replace(List.of(A, B), clock.instant());
            authenticate("runtime");
            gateway = new KiteMarketDataAdapter(session, registry, store, transport, settings, clock, metrics,
                    scheduler, worker, () -> 0.0);
        }
        void authenticate(String token) {
            session.install(new KiteAccessToken(token, clock.instant(), clock.instant().plusSeconds(86400)));
            session.profileValidated();
        }
        void connect() { gateway.subscribe(Set.of(A.id())); gateway.start(); transport.open(); }
        void frame(byte[] data) { transport.latest().listener.onBinary(data); }
        void tick(int token, int price) { frame(ltp(token, price)); }
        void advance(Duration duration) { clock.now = clock.now.plus(duration); scheduler.advance(duration); }
        @Override public void close() { gateway.close(); metrics.close(); }
    }

    static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class FakeTransport implements KiteWebSocketTransport {
        final List<Attempt> attempts = new ArrayList<>();
        @Override public CompletableFuture<Connection> connect(Listener listener) {
            var attempt = new Attempt(listener); attempts.add(attempt); return attempt.future;
        }
        Attempt latest() { return attempts.getLast(); }
        void open() { var attempt = latest(); attempt.future.complete(attempt.socket); }
        void failure(Failure failure) { latest().listener.onFailure(failure); }
    }
    static final class Attempt {
        final KiteWebSocketTransport.Listener listener;
        final CompletableFuture<KiteWebSocketTransport.Connection> future = new CompletableFuture<>();
        final FakeConnection socket = new FakeConnection();
        Attempt(KiteWebSocketTransport.Listener listener) { this.listener = listener; }
    }
    static final class FakeConnection implements KiteWebSocketTransport.Connection {
        final List<String> sent = new ArrayList<>();
        CompletableFuture<Void> nextSend;
        int closes, aborts;
        @Override public CompletableFuture<Void> send(String command) {
            sent.add(command);
            var result = nextSend;
            nextSend = null;
            return result == null ? CompletableFuture.completedFuture(null) : result;
        }
        @Override public void close() { closes++; }
        @Override public void abort() { aborts++; }
    }

    static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        volatile boolean shutdown;
        @Override public void execute(Runnable task) {
            if (shutdown) throw new RejectedExecutionException();
            tasks.add(task);
        }
        void runAll() { Runnable task; while ((task = tasks.poll()) != null) task.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; var pending = List.copyOf(tasks); tasks.clear(); return pending; }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
    static final class ManualScheduler extends ManualExecutor implements ScheduledExecutorService {
        final PriorityQueue<ScheduledTask<?>> scheduled = new PriorityQueue<>(Comparator.comparingLong(task -> task.due));
        long nanos;
        void advance(Duration duration) {
            nanos += duration.toNanos();
            while (!scheduled.isEmpty() && scheduled.peek().due <= nanos) scheduled.poll().run();
        }
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return schedule(Executors.callable(command, null), delay, unit);
        }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            var task = new ScheduledTask<V>(callable, nanos + unit.toNanos(delay)); scheduled.add(task); return task;
        }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initial, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initial, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public List<Runnable> shutdownNow() { scheduled.clear(); return super.shutdownNow(); }
        final class ScheduledTask<V> extends FutureTask<V> implements ScheduledFuture<V> {
            final long due;
            ScheduledTask(Callable<V> callable, long due) { super(callable); this.due = due; }
            @Override public long getDelay(TimeUnit unit) { return unit.convert(due - nanos, TimeUnit.NANOSECONDS); }
            @Override public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS)); }
        }
    }
}
