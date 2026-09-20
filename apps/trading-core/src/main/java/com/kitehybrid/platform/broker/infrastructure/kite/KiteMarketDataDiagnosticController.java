package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Explicit local development tool only. No automatic live stream and no broker order actions. */
@RestController
@Profile("development")
@ConditionalOnProperty(prefix = "kite.market-data", name = {"enabled", "diagnostic-enabled"}, havingValue = "true")
@RequestMapping("/api/development/market-data")
public final class KiteMarketDataDiagnosticController {
    private final MarketDataGateway gateway;
    private final InstrumentRegistry instruments;
    private final LatestMarketDataStore store;
    public KiteMarketDataDiagnosticController(MarketDataGateway gateway, InstrumentRegistry instruments,
            LatestMarketDataStore store) { this.gateway = gateway; this.instruments = instruments; this.store = store; }

    @PostMapping("/start") public MarketDataHealth start(@RequestParam String exchange, @RequestParam String symbol,
            @RequestParam(defaultValue = "LTP") StreamMode mode) {
        gateway.subscribe(Set.of(resolve(exchange, symbol)), mode);
        gateway.start();
        return gateway.health();
    }
    @PostMapping("/subscriptions") public MarketDataHealth subscribe(@RequestParam String exchange,
            @RequestParam String symbol, @RequestParam(defaultValue = "LTP") StreamMode mode) {
        gateway.subscribe(Set.of(resolve(exchange, symbol)), mode);
        return gateway.health();
    }
    @DeleteMapping("/subscriptions/{id}") public MarketDataHealth unsubscribe(@PathVariable UUID id) {
        gateway.unsubscribe(Set.of(new InstrumentId(id)));
        return gateway.health();
    }
    @PostMapping("/stop") public MarketDataHealth stop() { gateway.stop(); return gateway.health(); }
    @GetMapping("/status") public MarketDataHealth status() { return gateway.health(); }
    @GetMapping("/latest") public ResponseEntity<Tick> latest(@RequestParam String exchange, @RequestParam String symbol) {
        return ResponseEntity.of(store.latest(resolve(exchange, symbol)));
    }
    private InstrumentId resolve(String exchange, String symbol) {
        try {
            return instruments.findByExchangeAndSymbol(exchange, symbol).orElseThrow(() ->
                    new MarketDataException(MarketDataException.Reason.UNRESOLVED_INSTRUMENT)).id();
        } catch (IllegalArgumentException invalid) {
            throw new MarketDataException(MarketDataException.Reason.UNRESOLVED_INSTRUMENT);
        }
    }
    @ExceptionHandler(MarketDataException.class) ResponseEntity<Map<String, String>> invalid(MarketDataException failure) {
        return ResponseEntity.badRequest().body(Map.of("reason", failure.reason().name()));
    }
}
