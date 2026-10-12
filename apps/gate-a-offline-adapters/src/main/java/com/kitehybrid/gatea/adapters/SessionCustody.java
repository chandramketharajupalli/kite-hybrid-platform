package com.kitehybrid.gatea.adapters;

import java.time.Instant;

/** Future metadata-only custody contract. No token getter, serializer, login or restoration. */
public interface SessionCustody {
    record Provenance(String ownerEvidenceReference, String entitlementEvidenceReference,
                      Instant observedAt, Instant expiresAt, boolean synthetic) {
        @Override public String toString() { return "SESSION_PROVENANCE_REDACTED"; }
    }
    Provenance provenance();
    // No implementation is supplied; this interface does not establish an authenticated session.
}
