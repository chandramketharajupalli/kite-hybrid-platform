package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.domain.TradingFoundationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public static metadata only. No account data, broker client or mutable application service. */
@RestController
@ConditionalOnProperty(prefix = "trading.foundation-api", name = "enabled", havingValue = "true")
public final class TradingFoundationController {

    @GetMapping(value = "/api/trading/status", produces = "application/json")
    public ResponseEntity<TradingFoundationStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(TradingFoundationStatus.current());
    }
}
