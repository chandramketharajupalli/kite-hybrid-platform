# Development Entra authentication — inactive until separately authorized

This profile establishes diagnostic operator sessions only. It never grants access
to protected Trading APIs or trading mutations. Production remains deny-only.
Source preparation does not authorize application startup, credential loading,
provider requests, database creation or migration.

## Owner prerequisites

Approve a single workforce tenant, its exact v2 issuer, confidential Web client ID,
application-specific subject, and the exact registered callback:
`https://localhost:8443/api/development/operator-auth/callback`.
Use direct TLS bound to 127.0.0.1 with a browser-trusted external development
certificate. Do not enable forwarded headers or introduce a reverse proxy for
this first test. Do not change existing Kite callback settings.

Review the actual enforced Conditional Access authentication-strength policy.
An `acrs` claim alone is not proof of an enforced MFA policy. Independently
prepare a small UTF-8 JSON approval file containing `issuer`, `clientId`,
`subject`, `authenticationContext`, `policyVersion` and the boolean
`enforcedAuthenticationStrengthReviewed`. Record its SHA-256 independently.
The application checks that binding and exact fields; it does not query the
tenant or certify the owner's review. Never trust the first login to enroll a
subject. Unknown subjects remain denied.

Set the following external `operator.entra-development` properties only after
owner approval: `issuer`, `client-id`, `subject`, `callback`,
`authentication-context`, `approval-file`, `approval-sha256`,
`client-credential-file`, `database-url`, `database-user`,
`database-password-file`, `policy-version`, `selected-account`, `accounts`,
`permissions`, `absolute-lifetime`, `inactivity`, `authentication-age`.
Keep credentials in separately ACL-restricted regular files outside the checkout;
properties contain absolute references, never credential values. Paths through
symbolic links or special filesystem objects are rejected. The owner must verify
OS ACLs; the Java path checks do not certify ACL ownership.

The first implementation supports confidential-client secret POST, with the
secret read only during explicitly enabled application initialization. Certificate
client authentication is not implemented. Never place credentials in command
arguments, logs, this runbook, a test packet or Git. Configure credential rotation
and owner recovery before any real-provider test.

## Activation boundary

Both `development` and `entra-development` profiles and
`operator.entra-development.enabled=true` are required. The shipped profile keeps
the switch false. An enabled `production` profile or invalid safety settings
fails startup. TLS certificate/key material is external and must be approved
separately. No production identity provider is registered.

Required settings are loopback binding, TLS enabled, Secure and HttpOnly servlet
cookies, SameSite=Lax, no cookie Domain, cookie-only session tracking, forwarded
headers disabled and automatic Flyway disabled. Keep all Kite REST/market/order,
operator-control and live-test switches off. Keep PAPER, emergency stop and
disabled live trading. No OIDC token is retained for Graph or broker access.

## Dedicated database and history

Use a fresh separately authorized development database named `phase1447o_*` on
127.0.0.1, never the existing Compose `postgres-data` database. Use a dedicated
database role and a separate migration identity. The application does not run
migrations. Before launch, separately validate/apply V1–V14 from migration zero
with `public.flyway_schema_history` and these locations:

- `classpath:db/migration`
- `classpath:db/historical`
- `classpath:db/evidence`
- `classpath:db/operator`

Do not repair, baseline or migrate an existing database automatically.
The registry writes only owner/session/audit tables in `trading`; its internal
DataSource is not a Spring DataSource bean and does not replace other persistence.
The base application datasource still needs separately supplied safe dedicated
development settings. PostgreSQL roles must enforce the intended table access.

Original grants and absolute expiration are immutable. Revoked rows and audit
records are retained; DELETE/TRUNCATE is prohibited. Agree retention and
disposal before real identity data is stored. The audit records contain action
and time, not a complete tenant/security audit trail. No retention deletion is
implemented.

## Browser flow and failures

Open GET `/api/development/operator-auth/login`; its form supplies a session CSRF
token. POST login creates state/nonce/PKCE and redirects. GET callback is consumed
exactly once by the existing adapter, which performs token verification and
session-ID rotation before durable establishment. No framework OAuth2 login
filter also consumes the request.

The opaque registry handle stays in the server-side servlet session. The
SecurityContext contains a diagnostic marker with no Trading authorities.
GET status checks the registry and server-selected account/permission each time;
it returns only diagnostic readiness and `productionAuthorized=false`.
POST logout requires CSRF, attempts durable revocation, clears context and
invalidates the browser session. A registry failure returns unconfirmed
revocation, never a false success. Operator recovery must revoke any remaining
session before a later controlled test. Provider-wide logout/revocation is not
implemented.

Login transactions are process-local, bounded to 32 and expire after three
minutes. Restart loses pending transactions and fails closed. Run one instance.
Token and JWKS requests use exact tenant endpoints, normal TLS verification,
five-second connection and ten-second request/read limits, no redirects and
bounded bodies. JWKS network refresh is rate-limited to once per 30 seconds;
unknown keys/outages can temporarily deny login. Nimbus supplies its key cache.
No token refresh, UserInfo request or provider-wide revocation feed is enabled.

Disable HTTP wire/debug logging and callback query-string access logging in the
actual deployment. Never export headers, codes, tokens, cookies or client secrets.
Use redacted status and correlation-independent outcome evidence only.

## Validation and later manual smoke test

The offline packet targets 20 configuration/security methods followed by four
disposable PostgreSQL methods only after complete Stage 1 acceptance. No real
provider or credentials enter these tests. RSA tests use generated synthetic keys;
token exchange and servlet behavior remain synthetic.

A real browser smoke test needs separate bounded authorization specifying tenant,
client, subject, policy evidence, callback, credential references, approved
network endpoints, dedicated database, time window and cleanup. Record only
redacted outcomes: MFA policy confirmation, diagnostic status, denied Trading
reads/mutations, logout and expired/replayed-session denial. Do not infer
deployed-cookie, proxy, recovery or provider-revocation security from offline tests.

AutoGit INACTIVE. Protected Trading APIs DENY-ONLY. Live orders DISABLED.
Operational readiness NO_GO.
