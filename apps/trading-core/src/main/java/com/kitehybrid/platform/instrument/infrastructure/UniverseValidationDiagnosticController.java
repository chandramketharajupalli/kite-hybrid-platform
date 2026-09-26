package com.kitehybrid.platform.instrument.infrastructure;

import com.kitehybrid.platform.instrument.application.UniverseCsvImporter;
import com.kitehybrid.platform.instrument.application.UniverseValidationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Local development-only, read-only universe validation. */
@RestController
@Profile("development & !production")
@ConditionalOnProperty(prefix = "universe.diagnostic", name = "enabled", havingValue = "true")
@RequestMapping("/api/development/universe")
public final class UniverseValidationDiagnosticController {
    private final UniverseValidationService validation;

    public UniverseValidationDiagnosticController(
            com.kitehybrid.platform.instrument.application.InstrumentRegistry registry,
            @Value("${universe.diagnostic.path:}") String path) {
        this.validation = new UniverseValidationService(path, registry);
    }

    @GetMapping("/validate")
    public ResponseEntity<ValidationSummary> validate(HttpServletRequest request) {
        requireLoopback(request);
        var result = validation.validate();
        return ResponseEntity.ok(new ValidationSummary(result.inputRows(), result.uniqueInstruments(),
                result.duplicatesRemoved(), result.enabled(), result.disabled(), result.resolved(), result.unresolved()));
    }

    @GetMapping("/unresolved")
    public ResponseEntity<List<UnresolvedEntry>> unresolved(HttpServletRequest request) {
        requireLoopback(request);
        var result = validation.validate();
        return ResponseEntity.ok(result.entries().stream().filter(entry -> entry.instrument().isEmpty())
                .map(entry -> new UnresolvedEntry(entry.key().exchange(), entry.key().tradingSymbol())).toList());
    }

    @GetMapping("/lookup")
    public ResponseEntity<List<UniverseValidationService.ReferenceEntry>> lookup(
            HttpServletRequest request, @RequestParam String fragment) {
        requireLoopback(request);
        return ResponseEntity.ok(validation.lookup(fragment));
    }

    private static void requireLoopback(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr()) || !isLocalHost(request.getServerName())) {
            throw new DiagnosticAccessException();
        }
        var headers = request.getHeaderNames();
        while (headers != null && headers.hasMoreElements()) {
            var name = headers.nextElement();
            if (name.equalsIgnoreCase("Forwarded") || name.regionMatches(true, 0, "X-Forwarded-", 0, 12)) {
                throw new DiagnosticAccessException();
            }
        }
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || isLoopback(host);
    }

    private static boolean isLoopback(String address) {
        if (address == null) return false;
        if (address.equals("::1")) return true;
        if (!address.matches("127(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        return java.util.Arrays.stream(address.split("\\.")).skip(1)
                .allMatch(part -> Integer.parseInt(part) <= 255);
    }

    public record ValidationSummary(int inputRows, int unique, int duplicatesRemoved, int enabled,
                                    int disabled, int resolved, int unresolved) {}

    public record UnresolvedEntry(String exchange, String symbol) {}

    @org.springframework.web.bind.annotation.ExceptionHandler(DiagnosticAccessException.class)
    ResponseEntity<Map<String, String>> accessDenied() {
        return ResponseEntity.status(403).body(Map.of("code", "LOOPBACK_REQUIRED"));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_FRAGMENT"));
    }

    private static final class DiagnosticAccessException extends RuntimeException {}
}
