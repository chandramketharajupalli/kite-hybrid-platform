package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** On-demand broker observations only. Every entry point is measured; no cache, retries or polling. */
public final class KiteTradingReadAdapter implements BrokerOrdersProvider, BrokerTradesProvider,
        BrokerPositionsProvider, BrokerHoldingsProvider, BrokerMarginsProvider {
    private static final Logger LOG = LoggerFactory.getLogger(KiteTradingReadAdapter.class);
    private final KiteRestTransport transport;
    private final KiteSession session;
    private final KiteTradingReadMapper mapper;
    private final MeterRegistry metrics;

    KiteTradingReadAdapter(KiteRestTransport transport, KiteSession session,
                          KiteTradingReadMapper mapper, MeterRegistry metrics) {
        this.transport = Objects.requireNonNull(transport);
        this.session = Objects.requireNonNull(session);
        this.mapper = Objects.requireNonNull(mapper);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override public List<BrokerOrder> orders() {
        return read("orders", KiteRestTransport.Endpoint.ORDERS, mapper::orders);
    }
    @Override public List<BrokerTrade> trades() {
        return read("trades", KiteRestTransport.Endpoint.TRADES, mapper::trades);
    }
    @Override public BrokerPositions positions() {
        return read("positions", KiteRestTransport.Endpoint.POSITIONS, mapper::positions);
    }
    @Override public List<BrokerHolding> holdings() {
        return read("holdings", KiteRestTransport.Endpoint.HOLDINGS, mapper::holdings);
    }
    @Override public BrokerMargins margins() {
        return read("margins", KiteRestTransport.Endpoint.MARGINS, mapper::margins);
    }

    private <T> T read(String operation, KiteRestTransport.Endpoint endpoint, Function<String, T> normalize) {
        Timer.Sample sample = Timer.start(metrics);
        String result = "success";
        try {
            T value;
            // Reuse the existing REST/session monitor: an old read cannot invalidate a replacement login.
            synchronized (session) {
                if (!session.authenticated()) throw new BrokerReadException(AUTHENTICATION);
                value = normalize.apply(transport.get(endpoint));
            }
            LOG.info("Kite read operation={} result=success", operation);
            return value;
        } catch (BrokerReadException safe) {
            result = safe.category().name();
            LOG.warn("Kite read operation={} result={}", operation, result);
            throw safe;
        } catch (RuntimeException invalid) {
            result = INVALID_RESPONSE.name();
            LOG.warn("Kite read operation={} result={}", operation, result);
            throw new BrokerReadException(INVALID_RESPONSE);
        } finally {
            metrics.counter("kite.rest.operations", "operation", operation, "result", result).increment();
            sample.stop(metrics.timer("kite.rest.duration", "operation", operation, "result", result));
        }
    }
}
