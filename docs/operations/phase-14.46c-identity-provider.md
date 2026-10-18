# Operator identity-provider readiness

IDENTITY_PROVIDER_NOT_SELECTED. Local governance/application records establish no approved operator issuer, client registration, role registry, session invalidation service or secret-custody reference. Unknown provider and owner fields remain null.

Conditional recommendation: Spring Security servlet OAuth2 client with an approved OIDC provider and server-side sessions. Prefer an existing organization-managed OIDC service if independently verified; do not build custom password or key storage. No vendor, issuer, price, account or provider version is selected. An approved local-session provider is an alternative, but no approved identity lifecycle or credential store was found.

HTTP operator identity, governance owner authority and Kite broker authentication are separate. Terminal confirmation and a broker token grant neither HTTP permissions nor authenticated governance approval.

Provider acceptance requires an approved issuer/signing trust, exact registered HTTPS redirect URI, client secret reference in approved custody, default-deny role mapping, stable issuer+subject identity, MFA policy, trusted time, revocation/logout behavior, audit and outage handling. No actual secret was read, registration created or provider contacted. Real discovery/token/JWKS traffic requires separate authorization. Offline synthetic claims never establish a real identity.
