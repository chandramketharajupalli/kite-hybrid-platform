package com.kitehybrid.platform.trading.domain;

/**
 * Static capabilities of this foundation, never an attestation about running accounts.
 * No execution permission can be derived from this value.
 */
public record TradingFoundationStatus(
        String schemaVersion,
        String scope,
        String liveOrderPlacement,
        String paperOrderSubmission,
        String operationalReadiness,
        String seriesV2) {

    public static TradingFoundationStatus current() {
        return new TradingFoundationStatus(
                "TradingApi.v1",
                "FOUNDATION_CAPABILITIES_NOT_RUNTIME_HEALTH",
                "DISABLED",
                "NOT_IMPLEMENTED",
                "NO_GO",
                "PROPOSED_NOT_ADOPTED");
    }
}
