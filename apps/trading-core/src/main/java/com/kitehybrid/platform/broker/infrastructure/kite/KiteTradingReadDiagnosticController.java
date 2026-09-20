package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Explicit local, read-only diagnostics. Requires a loopback peer and local Host, with no proxy headers.
 * Never starts polling or creates a broker session; hostname checks never perform DNS resolution.
 */
@RestController
@Profile("development & !production")
@ConditionalOnProperty(prefix = "kite.trading-read", name = {"enabled", "diagnostic-enabled"}, havingValue = "true")
@RequestMapping("/api/development/trading-read")
public final class KiteTradingReadDiagnosticController {
    private final BrokerOrdersProvider orders;
    private final BrokerTradesProvider trades;
    private final BrokerPositionsProvider positions;
    private final BrokerHoldingsProvider holdings;
    private final BrokerMarginsProvider margins;

    public KiteTradingReadDiagnosticController(BrokerOrdersProvider orders, BrokerTradesProvider trades,
            BrokerPositionsProvider positions, BrokerHoldingsProvider holdings, BrokerMarginsProvider margins) {
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.holdings = holdings;
        this.margins = margins;
    }

    @GetMapping("/orders") public ResponseEntity<List<BrokerOrder>> orders(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(200).body(orders.orders());
    }
    @GetMapping("/trades") public ResponseEntity<List<BrokerTrade>> trades(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(200).body(trades.trades());
    }
    @GetMapping("/positions") public ResponseEntity<BrokerPositions> positions(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(200).body(positions.positions());
    }
    @GetMapping("/holdings") public ResponseEntity<List<BrokerHolding>> holdings(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(200).body(holdings.holdings());
    }
    @GetMapping("/margins") public ResponseEntity<BrokerMargins> margins(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(200).body(margins.margins());
    }

    // Explicit rejection also prevents browser HEAD/OPTIONS probes from reading broker data.
    @RequestMapping(value = {"/orders", "/trades", "/positions", "/holdings", "/margins"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE,
                    RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<Map<String, String>> unsupportedMethod(HttpServletRequest request) {
        requireLocalGet(request);
        return safe(405).body(Map.of("code", "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(BrokerReadException.class)
    public ResponseEntity<Map<String, Object>> brokerFailure(BrokerReadException failure) {
        int status = switch (failure.category()) {
            case CONFIGURATION -> 503;
            case AUTHENTICATION -> 401;
            case BROKER_API, TRANSPORT, INVALID_RESPONSE -> 502;
        };
        int upstreamStatus = failure.httpStatus();
        return safe(status).body(Map.of("category", failure.category().name(),
                "httpStatus", upstreamStatus >= 400 && upstreamStatus <= 599 ? upstreamStatus : 0));
    }

    @ExceptionHandler(DiagnosticAccessException.class)
    public ResponseEntity<Map<String, String>> accessFailure(DiagnosticAccessException failure) {
        return safe(failure.status).body(Map.of("code", failure.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> unexpectedFailure() {
        // No exception message, cause or broker payload may cross the diagnostic boundary.
        return safe(502).body(Map.of("category", "INVALID_RESPONSE", "httpStatus", 0));
    }

    private static void requireLocalGet(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) throw new DiagnosticAccessException(403, "LOOPBACK_REQUIRED");
        // Local Host validation prevents DNS rebinding, even for same-origin requests without Origin.
        if (!isLocalHostName(request.getServerName())) throw new DiagnosticAccessException(403, "LOCAL_HOST_REQUIRED");
        var hosts = request.getHeaders("Host");
        if (hosts != null && hosts.hasMoreElements()) {
            if (!isLocalHostHeader(hosts.nextElement()) || hosts.hasMoreElements())
                throw new DiagnosticAccessException(403, "LOCAL_HOST_REQUIRED");
        }
        var headers = request.getHeaderNames();
        while (headers != null && headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header.equalsIgnoreCase("Forwarded") || header.regionMatches(true, 0, "X-Forwarded-", 0, 12))
                throw new DiagnosticAccessException(403, "PROXY_NOT_ALLOWED");
        }
        if (request.getHeader("Origin") != null) throw new DiagnosticAccessException(403, "ORIGIN_NOT_ALLOWED");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.equals("none") && !fetchSite.equals("same-origin"))
            throw new DiagnosticAccessException(403, "CROSS_SITE_NOT_ALLOWED");
        if (!request.getMethod().equals("GET")) throw new DiagnosticAccessException(405, "METHOD_NOT_ALLOWED");
    }

    private static boolean isLocalHostName(String host) {
        if (host == null || host.length() > 64) return false;
        if (host.startsWith("[") && host.endsWith("]")) {
            String address = host.substring(1, host.length() - 1);
            return address.contains(":") && isLoopback(address);
        }
        return host.equalsIgnoreCase("localhost") || isLoopback(host);
    }

    private static boolean isLocalHostHeader(String host) {
        if (host == null || host.isEmpty() || host.length() > 128) return false;
        int separator;
        if (host.startsWith("[")) {
            int closingBracket = host.indexOf(']');
            if (closingBracket < 0) return false;
            separator = closingBracket + 1;
        } else {
            int colon = host.indexOf(':');
            separator = colon < 0 ? host.length() : colon;
        }
        if (!isLocalHostName(host.substring(0, separator))) return false;
        String port = host.substring(separator);
        if (port.isEmpty()) return true;
        return port.matches(":[0-9]{1,5}") && Integer.parseInt(port.substring(1)) >= 1
                && Integer.parseInt(port.substring(1)) <= 65535;
    }

    private static boolean isLoopback(String address) {
        if (address == null) return false;
        if (address.equals("::1") || address.matches("(?:0{1,4}:){7}0{0,3}1")) return true;
        if (!address.matches("127(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        String[] octets = address.split("\\.");
        return Integer.parseInt(octets[1]) <= 255 && Integer.parseInt(octets[2]) <= 255
                && Integer.parseInt(octets[3]) <= 255;
    }

    private static ResponseEntity.BodyBuilder safe(int status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache").header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
    }

    private static final class DiagnosticAccessException extends RuntimeException {
        private final int status;
        private DiagnosticAccessException(int status, String safeCode) {
            super(safeCode, null, false, false);
            this.status = status;
        }
    }
}
