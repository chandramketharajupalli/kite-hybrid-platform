# Kite callback correlation

Start at `http://localhost:8080/api/broker/kite/auth/login` using the same browser
that completes Zerodha authorization. Configure the developer-console redirect
URL exactly as `http://localhost:8080/api/broker/kite/auth/callback`.

Flyway V3 creates the PostgreSQL login-attempt table. Only the random nonce's
SHA-256 digest, the API-key namespace digest, and creation/expiry times are stored.
The application issues a separate `KITE_LOGIN_NONCE` cookie with Path
`/api/broker/kite/auth`, HttpOnly, SameSite=Lax, a ten-minute maximum age and Secure
on HTTPS. It creates no servlet session. Old or unknown JSESSIONID cookies have no
effect on authentication.

Callback state and browser nonce must both be present, well formed and equal.
One atomic SQL DELETE consumes the unexpired attempt before exchange. A restart
does not invalidate an outstanding attempt if the database, configured API key,
origin and browser cookie are retained. Expired, already consumed or absent
attempts fail deterministically. A callback that failed after consumption needs a
fresh login; do not refresh/replay its URL. Inspect `/api/broker/kite/auth/status`
to check whether the original callback succeeded.

## Missing state is a separate issue

[Current official Kite documentation](https://kite.trade/docs/connect/v3/user/#login-flow)
specifies one URL-encoded query string in `redirect_params`. The generated format is:

```text
https://kite.zerodha.com/connect/login?v=3&api_key=<REDACTED>&redirect_params=state%3D<REDACTED>
```

Decoding the value once yields `state=<nonce>`. The nonce is Base64URL without
padding. Do not double encode it or put a dynamic redirect URL in the developer
console. Kite staff also demonstrate this behavior in
[their redirect-parameter example](https://kite.trade/forum/discussion/6006/how-to-pass-an-extra-parameter-while-requesting-request-token).

A real callback lacked state despite the application's URL format matching the
documentation. This observation does not prove where it was lost. Tests exercise
our URL and callback behavior; they do not simulate Zerodha's actual redirect chain.
Persistence fixes lost local session state, not a missing broker query parameter.

After restarting with this code, begin a fresh login from the local endpoint.
If state is still missing, inspect the browser's network redirects locally:
check only whether our initial Location includes `redirect_params`, whether its
value has one encoding layer, and which redirect first loses it. Do not copy or
share complete URLs, cookies, request tokens, state values, keys or screenshots
containing them. A shared diagnostic should contain only booleans/reason codes.
Never enable raw HTTP request, query-string, cookie or JDBC parameter logging.

The application deliberately rejects callbacks without state before spending a
request token, even if the browser cookie and database attempt are valid. Accepting
that cookie alone would allow an attacker to send a token from another login to a
browser with a pending attempt. The same problem exists with an unbound database
nonce alone. The security choice here is to retain both proofs and fail closed;
no weaker automatic fallback is enabled. This follows the browser-bound state
requirement described in [OAuth security guidance](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.7.1).

## Safe diagnostics

```text
Kite callback state validation failed: reason={}, statePresent={}, stateValid={}, browserNoncePresent={}, browserNonceValid={}, stateMatched={}, attemptConsumed={}
```

Reasons are `STATE_MISSING`, `STATE_INVALID`, `BROWSER_NONCE_MISSING`,
`BROWSER_NONCE_INVALID`, `STATE_MISMATCH`, and `ATTEMPT_UNAVAILABLE`. The last covers
missing, expired, future-dated and already-consumed database attempts without
revealing their contents. Log arguments contain only internal enums and booleans.
The public response remains HTTP 403 with `broker=KITE` and
`code=KITE_CALLBACK_STATE_INVALID`; no internal diagnostics are returned.

If `.env` has a valid encryption key but this shell has an old value, rerun
`scripts/Use-DevelopmentInfrastructure.ps1`. Managed settings now come from `.env`
without inherited shell overrides. The helper preserves Base64 padding and never
prints values. Its synthetic regression checks run with:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/Test-DevelopmentInfrastructure.ps1
```
