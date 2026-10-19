package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.TradingProjection;
import com.kitehybrid.platform.trading.domain.TradingProjection.Kind;
import com.kitehybrid.platform.trading.domain.TradingProjection.Unavailable;
import com.kitehybrid.platform.trading.domain.TradingProjection.UnavailableReason;

/**
 * Read-only port. Future implementations may consume already captured immutable snapshots only.
 * Never refresh instruments, invoke broker-connected risk evaluation, execute research or place
 * orders here. This port does not perform authorization; callers must enforce that separately.
 */
public interface TradingProjectionService {
    default TradingProjection instruments() {
        return new Unavailable(Kind.INSTRUMENTS, UnavailableReason.EVIDENCE_UNAVAILABLE);
    }

    default TradingProjection strategies() {
        return new Unavailable(Kind.STRATEGIES, UnavailableReason.EVIDENCE_UNAVAILABLE);
    }

    default TradingProjection qualifications() {
        return new Unavailable(Kind.QUALIFICATIONS, UnavailableReason.EVIDENCE_UNAVAILABLE);
    }

    default TradingProjection riskStatus() {
        return new Unavailable(Kind.RISK_STATUS, UnavailableReason.EVIDENCE_UNAVAILABLE);
    }

    static TradingProjectionService unavailable() {
        return new TradingProjectionService() { };
    }
}
