# Authentication options and decision

Decision: DEFER_PROTECTED_ENDPOINT_ACTIVATION. Operator HTTP authentication NOT_ESTABLISHED. No provider selected or enrolled.

| Candidate | Evidence and feasibility | Controls/remaining approvals |
|---|---|---|
| A: reuse existing operator service | No approved HTTP identity/role mechanism verified; terminal console is not HTTP authentication | Identity provenance, role mapping, session expiry/revocation, CSRF and audit remain unresolved |
| B: Spring Security plus approved OIDC | Conditional recommendation for a future same-origin operator UI; no verified provider and starters absent from default cache | Approved issuer/client/trust, subject-to-local permission mapping, standard library session/token validation, offline dependency closure and security review required |
| C: Spring Security plus approved local sessions | No approved account store or local identity-verification mechanism found | Account enrollment, password/key storage, recovery, lockout, revocation, session expiry and operational ownership add work; no custom crypto |
| D: defer | Supported by observed lack of approved identity and framework closure | Keep protected routes absent/default-deny; safe static capability route only |

Smallest conditional design: reuse Spring Boot's managed Spring Security integration with server-side operator sessions and an independently approved existing OIDC provider, if one becomes available. This avoids building a new identity/password system. It is a proposal, not an available infrastructure claim or adopted dependency choice. No actual provider/version is invented.

OIDC requires approved network access for discovery/login/key lifecycle and a defined outage policy; none is granted here. Browser tokens must not be treated as direct operator permission declarations. Require approved issuer/audience/signature/time validation, state/nonce protections, verified subject, server-owned role mapping and least privilege. Provider outage or stale revocation/authentication evidence denies protected access; never indefinite cached acceptance.

Session design requires HTTPS, Secure/HttpOnly/SameSite cookies, session fixation protection, server-controlled expiry/invalidation and CSRF protection for session mutations; never put tokens in URLs/logs. Credential custody/provider client configuration is a separate approval gate. Audits use bounded outcome/reason and non-secret principal references, never raw tokens or provider exception text. Approved network and credential storage are unresolved for all concrete candidates.
