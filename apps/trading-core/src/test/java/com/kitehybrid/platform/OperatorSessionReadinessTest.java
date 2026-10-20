package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.OperatorIdentityProvider;
import com.kitehybrid.platform.trading.application.OperatorSessionReadiness;
import com.kitehybrid.platform.trading.application.OperatorSessionVerificationPort;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.trading.application.OperatorIdentityProvider.Revocation.NOT_REVOKED;
import static com.kitehybrid.platform.trading.application.OperatorIdentityProvider.Revocation.UNKNOWN;
import static com.kitehybrid.platform.trading.application.OperatorSessionReadiness.Reason.*;
import static com.kitehybrid.platform.trading.application.OperatorSessionVerificationPort.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

/** All verifier assertions are synthetic test data, never real credential verification. */
class OperatorSessionReadinessTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final class Fixture {
        SessionBinding expectedBinding = new SessionBinding("fixture-handle", "fixture-event");
        SessionBinding suppliedBinding = expectedBinding;
        String issuer = "fixture-issuer", subject = "fixture-subject", audience = "fixture-audience";
        String digest = "a".repeat(64), authority = "fixture-revocation", account = "fixture-account";
        String method = "GET", endpoint = "/api/trading/risk/status";
        Trust trust = Trust.VERIFIED_ASSERTION;
        CredentialKind kind = CredentialKind.OPERATOR;
        OperatorPrincipal.Provenance provenance = OperatorPrincipal.Provenance.UNVERIFIED_CLAIM;
        Instant now = NOW, authenticated = NOW.minusSeconds(60), activity = NOW;
        Instant absolute = NOW.plusSeconds(600), idle = NOW.plusSeconds(120), verified = NOW;
        Instant principalExpiry = NOW.plusSeconds(600), sessionExpiry = NOW.plusSeconds(600), checked = NOW;
        OperatorIdentityProvider.Revocation revocation = NOT_REVOKED, sessionRevocation = NOT_REVOKED;
        Instant sessionChecked = NOW;
        Set<OperatorPrincipal.Permission> permissions = Set.of(RISK_READ), principalPermissions = Set.of(RISK_READ);
        Set<OperatorPrincipal.Permission> allowedPermissions = Set.of(RISK_READ);
        Set<String> accounts = Set.of("fixture-account"), allowedAccounts = Set.of("fixture-account");
        OriginalGrant original = new OriginalGrant("fixture-event", NOW.minusSeconds(60), NOW.plusSeconds(600),
                Set.of(RISK_READ), Set.of("fixture-account"));
        OperatorIdentityProvider.SessionEvidence session() {
            var principal = new OperatorPrincipal("fixture-issuer", "fixture-subject", provenance,
                    principalPermissions, NOW.minusSeconds(120), principalExpiry);
            return new OperatorIdentityProvider.SessionEvidence(principal, "a".repeat(64),
                    NOW.minusSeconds(60), sessionExpiry, sessionRevocation, sessionChecked);
        }
        Verification verification() {
            return new Verification(suppliedBinding, issuer, subject, audience, digest, trust, kind,
                    authenticated, absolute, activity, idle, verified, revocation, authority, checked,
                    permissions, accounts, original);
        }
        OperatorSessionReadiness.Policy policy() {
            return new OperatorSessionReadiness.Policy("fixture-issuer", "fixture-audience", "fixture-revocation",
                    method, endpoint, account, allowedAccounts, allowedPermissions,
                    Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(120));
        }
        OperatorSessionReadiness.Result assess() {
            return new OperatorSessionReadiness(() -> Optional.of(session()), binding -> {
                assertEquals(expectedBinding, binding);
                return Optional.of(verification());
            }, Clock.fixed(now, ZoneOffset.UTC), policy(), expectedBinding).assess();
        }
    }
    private static void reject(String label, Consumer<Fixture> mutation, OperatorSessionReadiness.Reason reason) {
        Fixture f = new Fixture(); mutation.accept(f);
        var result = f.assess();
        assertEquals(reason, result.reason(), label); assertFalse(result.diagnosticReady(), label);
        assertFalse(result.productionAuthorized(), label);
    }
    @Test void absentSessionFailsClosed() {
        Fixture f = new Fixture();
        var result = new OperatorSessionReadiness(OperatorIdentityProvider.unavailable(),
                binding -> { fail("No verifier read without session"); return Optional.empty(); },
                CLOCK, f.policy(), f.expectedBinding).assess();
        assertEquals(SESSION_UNAVAILABLE, result.reason()); assertFalse(result.productionAuthorized());
    }
    @Test void invalidCredentialVerdictFailsClosed() {
        reject("rejected credential assertion", f -> f.trust = Trust.REJECTED, VERIFICATION_REJECTED);
        reject("unavailable credential assertion", f -> f.trust = Trust.UNAVAILABLE, VERIFICATION_UNAVAILABLE);
    }
    @Test void unverifiedEvidenceCannotEstablishTrust() {
        reject("caller assertions alone", f -> f.trust = Trust.UNVERIFIED, UNVERIFIED);
    }
    @Test void syntheticPrincipalIsRejected() {
        reject("synthetic principal despite verifier assertion",
                f -> f.provenance = OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE, SYNTHETIC_IDENTITY);
    }
    @Test void issuerAndAudienceMustMatchPolicy() {
        reject("issuer", f -> f.issuer = "other-issuer", BINDING_MISMATCH);
        reject("audience", f -> f.audience = "other-audience", BINDING_MISMATCH);
    }
    @Test void verificationMustBindExactSessionAndSubject() {
        reject("handle", f -> f.suppliedBinding = new SessionBinding("other-handle", "fixture-event"), BINDING_MISMATCH);
        reject("event", f -> f.suppliedBinding = new SessionBinding("fixture-handle", "other-event"), BINDING_MISMATCH);
        reject("subject", f -> f.subject = "other-subject", BINDING_MISMATCH);
        reject("captured digest", f -> f.digest = "b".repeat(64), BINDING_MISMATCH);
        reject("authentication instant", f -> f.authenticated = NOW.minusSeconds(59), BINDING_MISMATCH);
    }
    @Test void principalAndAbsoluteSessionExpiryAreExclusive() {
        reject("principal expiry", f -> f.principalExpiry = NOW, INVALID_TIME_WINDOW);
        reject("session expiry", f -> f.sessionExpiry = NOW, INVALID_TIME_WINDOW);
        reject("independent absolute expiry", f -> f.absolute = NOW, INVALID_TIME_WINDOW);
        reject("original absolute expiry", f -> f.original = new OriginalGrant("fixture-event",
                f.authenticated, NOW, Set.of(RISK_READ), f.accounts), INVALID_TIME_WINDOW);
        Fixture f = new Fixture(); f.principalExpiry = NOW.plusNanos(1); assertTrue(f.assess().diagnosticReady());
    }
    @Test void inactivityExpiryAndFutureActivityFailClosed() {
        reject("idle expiry", f -> f.idle = NOW, INACTIVITY_EXPIRED);
        reject("server idle limit boundary", f -> { f.now = NOW.plusSeconds(120);
            f.verified = f.now; f.checked = f.now; f.sessionChecked = f.now; }, INACTIVITY_EXPIRED);
        reject("future activity", f -> f.activity = NOW.plusNanos(1), INVALID_TIME_WINDOW);
        reject("extended idle assertion", f -> f.idle = NOW.plusSeconds(121), INACTIVITY_EXPIRED);
        reject("future verification", f -> f.verified = NOW.plusNanos(1), INVALID_TIME_WINDOW);
        reject("stale verification", f -> f.verified = NOW.minusSeconds(31), INVALID_TIME_WINDOW);
        Fixture f = new Fixture(); f.now = NOW.minusSeconds(61);
        assertEquals(INVALID_TIME_WINDOW, f.assess().reason(), "authentication in future");
    }
    @Test void revokedSessionIsRejected() {
        reject("independent revocation", f -> f.revocation = OperatorIdentityProvider.Revocation.REVOKED, REVOKED);
        reject("captured revocation", f -> f.sessionRevocation = OperatorIdentityProvider.Revocation.REVOKED, REVOKED);
    }
    @Test void unknownStaleAndFutureRevocationFailClosed() {
        reject("unknown", f -> f.revocation = UNKNOWN, REVOCATION_UNAVAILABLE);
        reject("captured unknown", f -> f.sessionRevocation = UNKNOWN, REVOCATION_UNAVAILABLE);
        reject("stale", f -> f.checked = NOW.minusSeconds(31), REVOCATION_UNAVAILABLE);
        reject("captured stale", f -> f.sessionChecked = NOW.minusSeconds(31), REVOCATION_UNAVAILABLE);
        reject("future", f -> f.checked = NOW.plusNanos(1), REVOCATION_UNAVAILABLE);
        reject("wrong authority", f -> f.authority = "other-authority", REVOCATION_UNAVAILABLE);
        Fixture f = new Fixture(); f.checked = NOW.minusSeconds(30); assertTrue(f.assess().diagnosticReady());
    }
    @Test void missingEndpointPermissionIsRejected() {
        reject("principal", f -> f.principalPermissions = Set.of(), MISSING_PERMISSION);
        reject("verification", f -> f.permissions = Set.of(), MISSING_PERMISSION);
        reject("server policy", f -> f.allowedPermissions = Set.of(), MISSING_PERMISSION);
    }
    @Test void crossAccountScopeIsRejected() {
        reject("server selected different account", f -> f.account = "other-account", ACCOUNT_DENIED);
        reject("policy intersection", f -> f.allowedAccounts = Set.of("other-account"), ACCOUNT_DENIED);
    }
    @Test void emptyOrUnmappedAccountScopeFailsClosed() {
        reject("empty verified scope", f -> f.accounts = Set.of(), ACCOUNT_DENIED);
        reject("empty policy scope", f -> f.allowedAccounts = Set.of(), ACCOUNT_DENIED);
    }
    @Test void unknownRoutesAndMutationsAreRejected() {
        reject("unknown endpoint", f -> f.endpoint = "/api/trading/not-reviewed", UNKNOWN_ENDPOINT);
        reject("POST", f -> f.method = "POST", METHOD_NOT_ALLOWED);
        reject("HEAD", f -> f.method = "HEAD", METHOD_NOT_ALLOWED);
    }
    @Test void renewalCannotExpandPermissionsOrAccounts() {
        reject("permission expansion", f -> f.permissions = Set.of(RISK_READ, ORDERS_READ), RENEWAL_ESCALATION);
        reject("principal expansion", f -> f.principalPermissions = Set.of(RISK_READ, ORDERS_READ), RENEWAL_ESCALATION);
        reject("account expansion", f -> f.accounts = Set.of("fixture-account", "other-account"), RENEWAL_ESCALATION);
    }
    @Test void renewalCannotExtendOriginalAbsoluteExpiry() {
        reject("absolute extension", f -> f.absolute = NOW.plusSeconds(601), RENEWAL_ESCALATION);
        reject("session extension", f -> f.sessionExpiry = NOW.plusSeconds(601), RENEWAL_ESCALATION);
        reject("substituted event", f -> f.original = new OriginalGrant("other-event", f.authenticated,
                f.absolute, f.permissions, f.accounts), BINDING_MISMATCH);
    }
    @Test void brokerEvidenceCannotBecomeOperatorIdentity() {
        reject("broker credential", f -> f.kind = CredentialKind.BROKER, NON_OPERATOR_CREDENTIAL);
        reject("unknown credential", f -> f.kind = CredentialKind.UNKNOWN, NON_OPERATOR_CREDENTIAL);
    }
    @Test void providerFailuresAndNullResultsFailClosed() {
        Fixture f = new Fixture();
        for (OperatorSessionVerificationPort verifier : List.<OperatorSessionVerificationPort>of(
                binding -> null, OperatorSessionVerificationPort.unavailable(),
                binding -> { throw new IllegalStateException("fixture-sensitive-detail"); })) {
            var result = new OperatorSessionReadiness(() -> Optional.of(f.session()), verifier, CLOCK, f.policy(), f.expectedBinding).assess();
            assertEquals(VERIFICATION_UNAVAILABLE, result.reason());
            assertFalse(result.toString().contains("fixture-sensitive-detail"));
        }
        for (OperatorIdentityProvider provider : List.<OperatorIdentityProvider>of(
                () -> null, () -> { throw new IllegalStateException("fixture-sensitive-detail"); })) {
            assertFalse(new OperatorSessionReadiness(provider, OperatorSessionVerificationPort.unavailable(),
                    CLOCK, f.policy(), f.expectedBinding).assess().productionAuthorized());
        }
    }
    @Test void assessmentEvidenceIsImmutableAndRedacted() {
        Fixture f = new Fixture(); var accounts = new HashSet<>(f.accounts); var permissions = new HashSet<>(f.permissions);
        f.accounts = accounts; f.permissions = permissions;
        var evidence = f.verification(); var policy = f.policy();
        accounts.clear(); permissions.clear();
        assertEquals(Set.of("fixture-account"), evidence.accounts()); assertEquals(Set.of(RISK_READ), evidence.permissions());
        assertThrows(UnsupportedOperationException.class, () -> evidence.accounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.originalGrant().permissions().clear());
        assertThrows(UnsupportedOperationException.class, () -> policy.accounts().clear());
        for (Object value : List.of(evidence, f.original, f.expectedBinding, policy, f.assess())) {
            assertFalse(value.toString().contains("fixture-subject"));
            assertFalse(value.toString().contains("fixture-handle"));
            assertFalse(value.toString().contains("fixture-account"));
        }
    }
    @Test void satisfiedDiagnosticChecksNeverGrantProductionAccess() {
        Fixture f = new Fixture(); var reads = new AtomicInteger(); var verifications = new AtomicInteger();
        var result = new OperatorSessionReadiness(() -> { reads.incrementAndGet(); return Optional.of(f.session()); },
                binding -> { verifications.incrementAndGet(); assertEquals(f.expectedBinding, binding); return Optional.of(f.verification()); },
                CLOCK, f.policy(), f.expectedBinding).assess();
        assertEquals(DIAGNOSTIC_MATCH_NOT_AUTHORIZATION, result.reason()); assertTrue(result.diagnosticReady());
        assertFalse(result.productionAuthorized()); assertEquals(1, reads.get()); assertEquals(1, verifications.get());
        assertFalse(f.session().principal().authenticatedProductionIdentity());
    }
}
