# Containment assessment

| Option | Evidence and limitations | Decision |
|---|---|---|
| A: sealed E-E/F supervisors | Retained handles, suspended creation and owned jobs appear in source. E-E WinError 5 and F conhost rejection/root 125 remain; no completed supervised replays. No filesystem/network/credential boundary. | Unqualified; unchanged |
| B: separate test-only job runner | Would require new qualified native lifecycle and cleanup implementation. Sanitized environment and exact commands cannot stop same-user credential-file reads or network access. | Design only; no runner created |
| C: approved isolated Windows guest | No approved provider, provenance-pinned image, guest runtime closure or independently verified containment. Privileges/licensing remain unresolved; no provisioning. | Unavailable for execution |
| D: deferral | Preserves all restrictions while owner approval and infrastructure remain absent. | Selected |

Host Windows 11 Home assessment is inherited evidence, not a fresh capability qualification. No host settings or firewall changes. Exact source hashes are in runner-assessment.json. No original18/31 tests or original46 pure tests were run.

FILESYSTEM_CONTAINMENT_UNVERIFIED: source must be read-only; sealed custody, production databases and credential files inaccessible; writes only fresh authorized output with independent write inventory and reparse protection.
NETWORK_CONTAINMENT_UNVERIFIED: require independent egress/DNS/NAT/proxy restriction and prevention of interface reactivation. No socket monkeypatch claim.
CREDENTIAL_ISOLATION_UNVERIFIED: reject inherited Kite/database secrets, profiles, .env mounts and credential-store access. Environment allowlisting alone cannot enforce this.

Historical external capture.py has literal UTF-8 mojibake c3 af c2 bb c2 bf, not a BOM. U+00BB SyntaxError/exit 1 remains. The original repository collector and replacement passive helper remain unchanged. Historical WinError 5/31 causes remain unverified.
