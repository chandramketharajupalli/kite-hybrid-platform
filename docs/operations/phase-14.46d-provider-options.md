# Conditional provider comparison

No provider is selected or verified available. This is an offline requirements comparison using preserved local records, not current vendor-product certification. Versions, issuers, tenants, deployments, licensing and prices are unknown. Named candidates below are owner-review alternatives only.

| Option | Evidence available locally | Conditions before suitability can be established | Operational burden to assess |
|---|---|---|---|
| Existing organization-managed OIDC service | No approved service/issuer record | Approved operator identity lifecycle, MFA, claims, revocation, exact client registration and secret custody | Existing support ownership, outage response and audit integration |
| Microsoft Entra ID candidate | No approved tenant/client/issuer | Tenant ownership, permitted issuer/account audience, claim mapping, redirect policy, license/cost evidence and logout/session behavior | Organizational administration and remote-service/network authorization |
| Keycloak candidate | No approved deployment/version | Approved host/image/runtime, patches, trust, backup, signing-key custody, operator lifecycle and verified OIDC behavior | Deployment, upgrades, database, recovery and service ownership |
| Authentik candidate | No approved deployment/version | Approved host/image/runtime, patches, trust, signing-key custody, verified protocol/claims/session behavior | Deployment, recovery, operational ownership and licensing review |
| Approved local-session identity | No approved identity store/mechanism | Independently approved account/password lifecycle, standard framework, MFA/recovery, credential custody and revocation | Credential lifecycle and security maintenance; avoid custom cryptography |
| Deferral | Supported by null provider/authority records | Preserve disabled endpoints until prerequisites are evidenced | No registration/provisioning or identity infrastructure introduced |

Technical direction remains conditional Spring Security servlet OIDC and server-side sessions; it does not choose a vendor. Prefer reusing an independently approved existing service over introducing a new identity system. Current decision is DEFER_PENDING_APPROVED_IDENTITY_PROVIDER. No unsupported feature, provider availability, compliance, price or compatibility claim is made.

Required comparison evidence for any future selection: exact issuer/version, identity provenance, role/account permissions, signature validation, trusted time, expiry/revocation, session/CSRF/logout controls, audit redaction, secret custody, bounded network endpoints, operational support and total-cost methodology. All must be supplied and reviewed separately. Do not infer governance authority from successful operator login.
