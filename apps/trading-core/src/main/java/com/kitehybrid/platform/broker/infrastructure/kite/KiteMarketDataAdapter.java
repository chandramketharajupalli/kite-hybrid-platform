package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.kitehybrid.platform.marketdata.application.MarketDataHealth.Reason.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteBrokerIdentity.BROKER_ID;

/**
 * One socket per instance. The monitor serializes lifecycle/desired state, never blocking network I/O.
 * Receive callbacks only decode, normalize and offer to a bounded queue. One worker updates local state.
 * No strategies, database writes, REST calls or user callbacks run on either path.
 */
public final class KiteMarketDataAdapter implements MarketDataGateway {
    private static final Logger LOG = LoggerFactory.getLogger(KiteMarketDataAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final KiteSession session;
    private final InstrumentRegistry instruments;
    private final LatestMarketDataStore store;
    private final KiteWebSocketTransport transport;
    private final KiteMarketDataProperties settings;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService worker;
    private final KiteReconnectPolicy reconnectPolicy;
    private final KiteMarketDataDecoder decoder = new KiteMarketDataDecoder();
    private final KiteMarketDataNormalizer normalizer = new KiteMarketDataNormalizer();
    private final ArrayBlockingQueue<PendingTick> queue;
    private final AtomicBoolean draining = new AtomicBoolean();
    private final Map<InstrumentId, StreamMode> desired = new HashMap<>();
    private final Map<InstrumentId, Long> subscriptionRevisions = new HashMap<>();
    private final Map<InstrumentId, Tick> freshness = new HashMap<>();
    private Map<InstrumentId, StreamMode> active = Map.of();
    private State state = State.STOPPED;
    private MarketDataHealth.Reason reason = NONE;
    private MarketDataHealth.Reason quality = NONE;
    private MarketDataHealth.Status lastLoggedStatus;
    private boolean running, closed, synchronizing;
    private long epoch, sessionGeneration;
    private long subscriptionSequence;
    private InstrumentSnapshot mapping = InstrumentSnapshot.empty();
    private KiteWebSocketTransport.Connection connection;
    private KiteWebSocketTransport.Connection retiringConnection;
    private CompletableFuture<KiteWebSocketTransport.Connection> connecting;
    private ScheduledFuture<?> reconnectTask, watchdogTask, timeoutTask;
    private Instant connectedAt, lastMessageAt, lastTickAt;
    private int reconnectAttempts;
    private long framesReceived, ticksReceived, decodeFailures, eventsDropped, ignoredTextMessages;

    public KiteMarketDataAdapter(KiteSession session, InstrumentRegistry instruments, LatestMarketDataStore store,
            KiteWebSocketTransport transport, KiteMarketDataProperties settings, Clock clock, MeterRegistry metrics) {
        this(session, instruments, store, transport, settings, clock, metrics, scheduler(),
                Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("marketdata-worker").factory()),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    KiteMarketDataAdapter(KiteSession session, InstrumentRegistry instruments, LatestMarketDataStore store,
            KiteWebSocketTransport transport, KiteMarketDataProperties settings, Clock clock, MeterRegistry metrics,
            ScheduledExecutorService scheduler, ExecutorService worker, DoubleSupplier random) {
        this.session = session; this.instruments = instruments; this.store = store; this.transport = transport;
        this.settings = settings; this.clock = clock; this.metrics = metrics;
        this.scheduler = scheduler; this.worker = worker;
        this.reconnectPolicy = new KiteReconnectPolicy(settings.reconnect(), random);
        this.queue = new ArrayBlockingQueue<>(settings.queueCapacity());
        if (!settings.enabled()) reason = DISABLED;
        metrics.gauge("marketdata.connection.state", this, gateway -> gateway.state().ordinal());
        metrics.gauge("marketdata.subscriptions", List.of(io.micrometer.core.instrument.Tag.of("kind", "desired")),
                this, gateway -> gateway.health().desiredSubscriptions());
        metrics.gauge("marketdata.subscriptions", List.of(io.micrometer.core.instrument.Tag.of("kind", "active")),
                this, gateway -> gateway.health().activeSubscriptions());
        metrics.gauge("marketdata.last.tick.age", this, gateway -> gateway.lastTickAge());
        metrics.gauge("marketdata.queue.size", queue, Collection::size);
        for (String name : List.of("ticks.received", "frames.received", "decode.failures", "reconnects",
                "events.dropped", "text.ignored", "ticks.rejected")) metrics.counter("marketdata." + name);
    }

    private static ScheduledExecutorService scheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon().name("marketdata-lifecycle").factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override public synchronized void start() {
        if (closed) throw new MarketDataException(MarketDataException.Reason.CLOSED);
        if (running) return;
        if (!settings.enabled()) { reason = DISABLED; return; }
        if (retiringConnection != null) { retiringConnection.abort(); retiringConnection = null; }
        running = true;
        quality = NONE; reason = NONE; reconnectAttempts = 0;
        freshness.clear(); lastTickAt = null; lastMessageAt = null; connectedAt = null;
        transition(State.STARTING);
        connect();
        if (running) scheduleWatchdog();
    }

    @Override public void subscribe(Set<InstrumentId> ids) { subscribe(ids, StreamMode.LTP); }

    @Override public synchronized void subscribe(Set<InstrumentId> ids, StreamMode mode) {
        requireOpen();
        Set<InstrumentId> requested = Set.copyOf(ids);
        Objects.requireNonNull(mode);
        var candidate = new HashMap<>(desired);
        requested.forEach(id -> candidate.put(id, mode));
        validate(candidate.keySet(), instruments.snapshot());
        if (candidate.equals(desired)) return;
        requested.forEach(id -> {
            if (desired.get(id) != mode) {
                freshness.remove(id);
                subscriptionRevisions.put(id, ++subscriptionSequence);
            }
        });
        desired.clear(); desired.putAll(candidate);
        LOG.info("Market data subscription action=subscribe desired={} mode={}", desired.size(), mode);
        synchronizeSubscriptions();
    }

    @Override public synchronized void unsubscribe(Set<InstrumentId> ids) {
        requireOpen();
        boolean changed = false;
        for (var id : Set.copyOf(ids)) {
            changed |= desired.remove(id) != null; freshness.remove(id); subscriptionRevisions.remove(id);
        }
        if (!changed) return;
        LOG.info("Market data subscription action=unsubscribe desired={}", desired.size());
        synchronizeSubscriptions();
    }

    private void requireOpen() {
        if (closed) throw new MarketDataException(MarketDataException.Reason.CLOSED);
    }

    private void validate(Set<InstrumentId> ids, InstrumentSnapshot snapshot) {
        if (ids.size() > settings.maxSubscriptions())
            throw new MarketDataException(MarketDataException.Reason.SUBSCRIPTION_LIMIT);
        for (var id : ids) token(snapshot, id);
    }

    private long token(InstrumentSnapshot snapshot, InstrumentId id) {
        var instrument = snapshot.byId().get(id);
        if (instrument == null) throw new MarketDataException(MarketDataException.Reason.UNRESOLVED_INSTRUMENT);
        var broker = instrument.brokerId();
        try {
            if (!broker.broker().equals(BROKER_ID) || !broker.value().matches("[1-9][0-9]{0,9}"))
                throw new NumberFormatException();
            long token = Long.parseLong(broker.value());
            if (token > 0xffff_ffffL) throw new NumberFormatException();
            return token;
        } catch (NumberFormatException invalid) {
            throw new MarketDataException(MarketDataException.Reason.INVALID_BROKER_MAPPING);
        }
    }

    /** Must hold the monitor. A new epoch fences all old callbacks, timers and queued events. */
    private void connect() {
        var sessionStatus = session.marketDataStatus();
        if (!sessionStatus.authenticated()) { degrade(AUTH_REQUIRED); return; }
        mapping = instruments.snapshot();
        try { validate(desired.keySet(), mapping); }
        catch (MarketDataException invalid) {
            degrade(invalid.reason() == MarketDataException.Reason.INVALID_BROKER_MAPPING
                    ? INVALID_BROKER_MAPPING : UNRESOLVED_INSTRUMENT);
            return;
        }
        sessionGeneration = sessionStatus.generation();
        long attempt = ++epoch;
        LOG.info("Market data connect state={} attempt={}", state, reconnectAttempts);
        timeoutTask = schedule(() -> connectionFailed(attempt, KiteWebSocketTransport.Failure.CONNECTION),
                settings.connectTimeout());
        try {
            connecting = transport.connect(new KiteWebSocketTransport.Listener() {
                @Override public void onBinary(byte[] frame) { receive(attempt, frame); }
                @Override public void onText(String text) { receiveText(attempt, text); }
                @Override public void onClosed(boolean auth) {
                    connectionFailed(attempt, auth ? KiteWebSocketTransport.Failure.AUTHENTICATION
                            : KiteWebSocketTransport.Failure.CONNECTION);
                }
                @Override public void onFailure(KiteWebSocketTransport.Failure failure) { connectionFailed(attempt, failure); }
            });
            connecting.whenComplete((opened, failure) -> {
                synchronized (this) {
                    if (!running || attempt != epoch) { if (opened != null) opened.abort(); return; }
                    connecting = null;
                    if (failure != null) { connectionFailed(attempt, classify(failure)); return; }
                    if (!validSession()) { opened.abort(); degrade(invalidSessionReason()); return; }
                    connection = opened;
                    active = Map.of();
                    synchronizeSubscriptions();
                }
            });
        } catch (RuntimeException failure) { connectionFailed(attempt, classify(failure)); }
    }

    private static KiteWebSocketTransport.Failure classify(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error instanceof KiteWebSocketTransport.TransportException safe ? safe.failure()
                : KiteWebSocketTransport.Failure.CONNECTION;
    }

    /** Coalesce desired-state changes while one bounded command batch is in flight. */
    private void synchronizeSubscriptions() {
        if (!running || connection == null || synchronizing) return;
        if (!validSession()) { degrade(invalidSessionReason()); return; }
        if (mapping.version() != instruments.snapshot().version()) { degrade(INSTRUMENT_REGISTRY_CHANGED); return; }
        Map<InstrumentId, StreamMode> target = Map.copyOf(desired);
        long attempt = epoch;
        var socket = connection;
        List<String> commands = commands(active, target);
        synchronizing = true;
        cancel(timeoutTask);
        timeoutTask = schedule(() -> connectionFailed(attempt, KiteWebSocketTransport.Failure.CONNECTION),
                settings.connectTimeout());
        CompletableFuture<Void> sent = CompletableFuture.completedFuture(null);
        for (String command : commands) sent = sent.thenCompose(ignored -> {
            synchronized (this) {
                if (!running || attempt != epoch || connection != socket)
                    return CompletableFuture.failedFuture(new CancellationException());
                return socket.send(command);
            }
        });
        sent.whenComplete((ignored, failure) -> {
            synchronized (this) {
                if (!running || attempt != epoch) return;
                synchronizing = false;
                cancel(timeoutTask); timeoutTask = null;
                if (failure != null) { connectionFailed(attempt, classify(failure)); return; }
                active = target;
                if (!desired.equals(target)) { synchronizeSubscriptions(); return; }
                if (state != State.CONNECTED) {
                    connectedAt = clock.instant();
                    reason = NONE; reconnectAttempts = 0;
                    transition(State.CONNECTED);
                    LOG.info("Market data connected active={}", active.size());
                }
            }
        });
    }

    private List<String> commands(Map<InstrumentId, StreamMode> previous, Map<InstrumentId, StreamMode> next) {
        List<String> result = new ArrayList<>();
        Set<InstrumentId> removed = new HashSet<>(previous.keySet()); removed.removeAll(next.keySet());
        Set<InstrumentId> added = new HashSet<>(next.keySet()); added.removeAll(previous.keySet());
        if (!removed.isEmpty()) result.add(command("unsubscribe", tokens(removed)));
        if (!added.isEmpty()) result.add(command("subscribe", tokens(added)));
        for (var mode : StreamMode.values()) {
            Set<InstrumentId> changed = new HashSet<>();
            next.forEach((id, value) -> { if (value == mode && previous.get(id) != value) changed.add(id); });
            if (!changed.isEmpty()) result.add(command("mode", List.of(mode.name().toLowerCase(Locale.ROOT), tokens(changed))));
        }
        return result;
    }

    private List<Long> tokens(Set<InstrumentId> ids) {
        return ids.stream().map(id -> token(mapping, id)).sorted().toList();
    }
    private static String command(String action, Object value) {
        try { return JSON.writeValueAsString(Map.of("a", action, "v", value)); }
        catch (Exception impossible) { throw new IllegalStateException("Cannot encode market-data command"); }
    }

    private void receive(long attempt, byte[] frame) {
        InstrumentSnapshot snapshot;
        long receivedSubscriptionSequence;
        Instant receivedAt = clock.instant();
        synchronized (this) {
            if (!running || attempt != epoch) return;
            if (!validSession()) { degrade(invalidSessionReason()); return; }
            if (!currentMapping()) { degrade(INSTRUMENT_REGISTRY_CHANGED); return; }
            lastMessageAt = receivedAt;
            framesReceived++; metrics.counter("marketdata.frames.received").increment();
            snapshot = mapping;
            receivedSubscriptionSequence = subscriptionSequence;
        }
        try {
            // Whole-frame validation precedes publishing any ticks.
            var normalized = decoder.decode(frame).stream().map(wire -> {
                Tick tick = normalizer.normalize(wire, snapshot, receivedAt);
                if (tick.exchangeTimestamp().filter(exchange -> exchange.isAfter(receivedAt.plusSeconds(5))).isPresent())
                    throw new IllegalArgumentException("Future exchange timestamp");
                return new Normalized(wire.mode(), tick);
            }).toList();
            synchronized (this) {
                if (!running || attempt != epoch || !validSession() || !currentMapping()) return;
                for (var value : normalized) {
                    Tick tick = value.tick;
                    if (desired.get(tick.instrumentId()) != value.mode
                            || subscriptionRevisions.get(tick.instrumentId()) > receivedSubscriptionSequence) continue;
                    ticksReceived++; metrics.counter("marketdata.ticks.received").increment();
                    if (!queue.offer(new PendingTick(attempt, subscriptionRevisions.get(tick.instrumentId()), tick))) {
                        eventsDropped++; metrics.counter("marketdata.events.dropped").increment();
                        latchQuality(BACKPRESSURE);
                    }
                }
            }
            requestDrain();
        } catch (IllegalArgumentException malformed) {
            synchronized (this) {
                if (!running || attempt != epoch) return;
                decodeFailures++; metrics.counter("marketdata.decode.failures").increment();
                if (decodeFailures == 1 || (decodeFailures & (decodeFailures - 1)) == 0)
                    LOG.warn("Market data decode failure count={}", decodeFailures);
                latchQuality(MALFORMED_DATA);
            }
        }
    }

    private void receiveText(long attempt, String text) {
        synchronized (this) {
            if (!running || attempt != epoch) return;
            lastMessageAt = clock.instant();
            ignoredTextMessages++; metrics.counter("marketdata.text.ignored").increment();
            try {
                var message = JSON.readTree(text);
                if (message != null && "error".equals(message.path("type").asText())) latchQuality(BROKER_ERROR);
                // Order postbacks, alerts and unknown text have no market-data or order-domain effects.
            } catch (Exception ignored) { /* bounded transport payload, never log broker text */ }
        }
    }

    private void requestDrain() {
        if (!queue.isEmpty() && draining.compareAndSet(false, true)) {
            try { worker.execute(this::drain); }
            catch (RejectedExecutionException stopped) { draining.set(false); }
        }
    }

    private void drain() {
        try {
            PendingTick event;
            while ((event = queue.poll()) != null) {
                synchronized (this) {
                    if (!accepts(event)) continue;
                }
                // The store is in-process, but even a delayed consumer must never hold the I/O monitor.
                try {
                    boolean updated = store.update(event.tick);
                    synchronized (this) {
                        if (!accepts(event)) continue;
                        if (updated) {
                            freshness.put(event.tick.instrumentId(), event.tick);
                            lastTickAt = event.tick.receivedAt();
                        } else metrics.counter("marketdata.ticks.rejected").increment();
                    }
                } catch (RuntimeException failure) {
                    synchronized (this) { if (accepts(event)) latchQuality(PROCESSING_FAILURE); }
                }
            }
        } finally {
            draining.set(false);
            if (!worker.isShutdown()) requestDrain();
        }
    }

    private boolean accepts(PendingTick event) {
        return running && event.epoch == epoch && validSession() && currentMapping()
                && Objects.equals(subscriptionRevisions.get(event.tick.instrumentId()), event.revision);
    }

    private synchronized void connectionFailed(long attempt, KiteWebSocketTransport.Failure failure) {
        if (!running || attempt != epoch) return;
        LOG.warn("Market data disconnected classification={} state={}", failure, state);
        if (failure == KiteWebSocketTransport.Failure.MESSAGE_TOO_LARGE) {
            decodeFailures++; metrics.counter("marketdata.decode.failures").increment();
            latchQuality(MALFORMED_DATA);
        }
        if (!validSession()) { degrade(invalidSessionReason()); return; }
        if (!currentMapping()) { degrade(INSTRUMENT_REGISTRY_CHANGED); return; }
        if (failure == KiteWebSocketTransport.Failure.AUTHENTICATION) {
            degrade(AUTH_REQUIRED); return;
        }
        detach(false);
        if (reconnectAttempts >= settings.reconnect().maxAttempts()) {
            degrade(RECONNECT_EXHAUSTED);
            LOG.warn("Market data reconnect exhausted attempts={}", reconnectAttempts);
            return;
        }
        transition(State.RECONNECTING);
        reason = CONNECTION_FAILED;
        Duration delay = reconnectPolicy.delay(++reconnectAttempts);
        metrics.counter("marketdata.reconnects").increment();
        LOG.info("Market data reconnect attempt={} delayMs={}", reconnectAttempts, delay.toMillis());
        long scheduledEpoch = epoch;
        reconnectTask = schedule(() -> {
            synchronized (this) { if (running && scheduledEpoch == epoch) connect(); }
        }, delay);
    }

    private boolean validSession() {
        var snapshot = session.marketDataStatus();
        return snapshot.authenticated() && snapshot.generation() == sessionGeneration;
    }

    private MarketDataHealth.Reason invalidSessionReason() {
        return session.marketDataStatus().authenticated() ? SESSION_CHANGED : AUTH_REQUIRED;
    }

    private boolean currentMapping() { return mapping.version() == instruments.snapshot().version(); }

    private void scheduleWatchdog() {
        Duration interval = Duration.ofNanos(Math.max(1_000_000, Math.min(Duration.ofSeconds(1).toNanos(),
                Math.min(settings.staleAfter().toNanos(), settings.idleTimeout().toNanos()) / 2)));
        watchdogTask = schedule(() -> {
            synchronized (this) {
                if (!running) return;
                if (!validSession()) degrade(invalidSessionReason());
                else if (!currentMapping()) degrade(INSTRUMENT_REGISTRY_CHANGED);
                else if (state == State.CONNECTED
                        && Duration.between(lastActivitySinceConnect(), clock.instant()).compareTo(settings.idleTimeout()) > 0)
                    connectionFailed(epoch, KiteWebSocketTransport.Failure.CONNECTION);
                logHealthTransition();
                if (running) scheduleWatchdog();
            }
        }, interval);
    }

    private Instant lastActivitySinceConnect() {
        return lastMessageAt == null || lastMessageAt.isBefore(connectedAt) ? connectedAt : lastMessageAt;
    }

    private void latchQuality(MarketDataHealth.Reason cause) {
        if (quality == NONE) { quality = cause; LOG.warn("Market data quality degraded reason={}", cause); }
    }

    private void degrade(MarketDataHealth.Reason cause) {
        running = false;
        detach(false); cancel(watchdogTask); watchdogTask = null;
        reason = cause;
        transition(State.DEGRADED);
        LOG.warn("Market data degraded reason={}", cause);
    }

    private void detach(boolean graceful) {
        ++epoch;
        cancel(reconnectTask); reconnectTask = null;
        cancel(timeoutTask); timeoutTask = null;
        if (connecting != null) { var pending = connecting; connecting = null; pending.cancel(true); }
        if (connection != null) {
            var old = connection; connection = null;
            if (graceful) {
                if (retiringConnection != null) retiringConnection.abort();
                retiringConnection = old;
                old.close();
            } else old.abort();
        }
        synchronizing = false;
        active = Map.of(); freshness.clear(); queue.clear(); connectedAt = null;
    }

    @Override public synchronized State state() { return state; }

    @Override public synchronized MarketDataHealth health() {
        var status = switch (state) {
            case STOPPED -> MarketDataHealth.Status.STOPPED;
            case STARTING -> MarketDataHealth.Status.STARTING;
            case RECONNECTING -> MarketDataHealth.Status.RECONNECTING;
            case DEGRADED -> MarketDataHealth.Status.DEGRADED;
            case STOPPING -> MarketDataHealth.Status.STOPPING;
            case CONNECTED -> freshnessStatus();
        };
        var healthReason = reason != NONE ? reason : quality;
        if (state == State.CONNECTED && !validSession())
            healthReason = invalidSessionReason();
        else if (state == State.CONNECTED && !currentMapping()) healthReason = INSTRUMENT_REGISTRY_CHANGED;
        return new MarketDataHealth(state, status, healthReason,
                Optional.ofNullable(connectedAt), Optional.ofNullable(lastMessageAt), Optional.ofNullable(lastTickAt),
                desired.size(), active.size(), reconnectAttempts, framesReceived, ticksReceived, decodeFailures,
                eventsDropped, ignoredTextMessages, queue.size());
    }

    private MarketDataHealth.Status freshnessStatus() {
        if (!validSession() || !currentMapping()) return MarketDataHealth.Status.DEGRADED;
        if (quality != NONE) return MarketDataHealth.Status.DEGRADED;
        if (desired.isEmpty() || !active.equals(desired) || !freshness.keySet().containsAll(desired.keySet()))
            return MarketDataHealth.Status.NO_DATA;
        Instant now = clock.instant();
        for (var id : desired.keySet()) {
            Tick tick = freshness.get(id);
            if (Duration.between(tick.receivedAt(), now).compareTo(settings.staleAfter()) > 0
                    || tick.exchangeTimestamp().filter(exchange ->
                        Duration.between(exchange, now).compareTo(settings.staleAfter()) > 0).isPresent())
                return MarketDataHealth.Status.STALE;
        }
        return MarketDataHealth.Status.FRESH;
    }

    private synchronized double lastTickAge() {
        return lastTickAt == null ? Double.NaN : Math.max(0, Duration.between(lastTickAt, clock.instant()).toMillis() / 1000.0);
    }

    private void logHealthTransition() {
        var status = health().status();
        if (status != lastLoggedStatus) {
            LOG.info("Market data freshness status={} reason={}", status, health().reason());
            lastLoggedStatus = status;
        }
    }

    private void transition(State next) {
        if (state == next) return;
        boolean valid = switch (state) {
            case STOPPED -> next == State.STARTING;
            case STARTING -> next == State.CONNECTED || next == State.RECONNECTING || next == State.DEGRADED || next == State.STOPPING;
            case CONNECTED -> next == State.RECONNECTING || next == State.DEGRADED || next == State.STOPPING;
            case RECONNECTING -> next == State.CONNECTED || next == State.DEGRADED || next == State.STOPPING;
            case DEGRADED -> next == State.STARTING || next == State.STOPPING;
            case STOPPING -> next == State.STOPPED;
        };
        if (!valid) throw new IllegalStateException("Invalid market-data transition " + state + " -> " + next);
        state = next;
    }

    private ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        return scheduler.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
    }
    private static void cancel(Future<?> task) { if (task != null) task.cancel(false); }

    @Override public synchronized void stop() {
        running = false;
        if (state != State.STOPPED) transition(State.STOPPING);
        detach(true); cancel(watchdogTask); watchdogTask = null;
        if (state != State.STOPPED) transition(State.STOPPED);
        reason = settings.enabled() ? NONE : DISABLED;
        quality = NONE;
        LOG.info("Market data stopped desired={}", desired.size());
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        stop();
        scheduler.shutdownNow(); worker.shutdownNow();
    }

    private record Normalized(StreamMode mode, Tick tick) {}
    private record PendingTick(long epoch, long revision, Tick tick) {}
}
