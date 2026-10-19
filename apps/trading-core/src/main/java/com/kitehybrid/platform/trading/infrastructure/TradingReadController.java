package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorPrincipalAdapter;
import com.kitehybrid.platform.trading.application.TradingApiAuthorization;
import com.kitehybrid.platform.trading.application.TradingProjectionService;
import com.kitehybrid.platform.trading.domain.TradingProjection;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** No broker dependency or alternate mapping. Production requests remain denied. */
@RestController
public final class TradingReadController {
    private final TradingProjectionService projections;
    private final TradingApiAuthorization authorization = new TradingApiAuthorization(Clock.systemUTC());

    public TradingReadController(TradingProjectionService projections) {
        this.projections = Objects.requireNonNull(projections);
    }

    @GetMapping(value = "/api/trading/instruments", produces = "application/json")
    public ResponseEntity<?> instruments() {
        return read("/api/trading/instruments", projections::instruments);
    }

    @GetMapping(value = "/api/trading/strategies", produces = "application/json")
    public ResponseEntity<?> strategies() {
        return read("/api/trading/strategies", projections::strategies);
    }

    @GetMapping(value = "/api/trading/qualifications", produces = "application/json")
    public ResponseEntity<?> qualifications() {
        return read("/api/trading/qualifications", projections::qualifications);
    }

    @GetMapping(value = "/api/trading/risk/status", produces = "application/json")
    public ResponseEntity<?> riskStatus() {
        return read("/api/trading/risk/status", projections::riskStatus);
    }

    private ResponseEntity<?> read(String path, Supplier<TradingProjection> source) {
        // Defense in depth if invoked without the filter chain. No header, cookie, broker
        // token or synthetic principal can supply an authenticated production adapter.
        var decision = authorization.authorize(OperatorPrincipalAdapter.unavailable(), "GET", path);
        if (!decision.authorized()) return failure(401, "OPERATOR_AUTHENTICATION_REQUIRED");
        try {
            return render(source.get());
        } catch (RuntimeException unavailable) {
            return failure(503, "SOURCE_UNAVAILABLE");
        }
    }

    /** Pure wire mapping for contract tests; not a handler or an authorization decision. */
    public static ResponseEntity<?> render(TradingProjection projection) {
        if (projection instanceof TradingProjection.Available available) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ReadResponse(
                    "TradingApi.v1", available.kind(), available.entries(), available.evidence(), false));
        }
        if (projection instanceof TradingProjection.Unavailable unavailable) {
            return failure(503, unavailable.reason().name());
        }
        return failure(503, "SOURCE_UNAVAILABLE");
    }

    private static ResponseEntity<ApiError> failure(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ApiError("TradingApi.v1", code, UUID.randomUUID()));
    }

    public record ApiError(String schemaVersion, String code, UUID correlationId) {}
    public record ReadResponse(String schemaVersion, TradingProjection.Kind kind,
            List<TradingProjection.Entry> entries, TradingProjection.Evidence evidence,
            boolean executionAuthorized) {
        public ReadResponse { entries = List.copyOf(entries); }
    }
}
