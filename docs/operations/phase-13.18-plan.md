# Phase 13.18 frozen public-research plan v1

Baseline: clean develop; HEAD/local origin/develop both
a55967391158a8d3e0a7d374e045d2819ff0d27b. No fetch; remote freshness unknown.
910 tracked raw-byte hashes inventoried before drafting. Parent Phase13.17 canonical
e3d766539838bfce4d6aa00d4c9992524bed59eaa0631e6e6379b5e5bc58d070 verified.
G1/18 registered universal-newline sources/63 raw research,25 K/L files,11 governance
envelopes and13 prior phase envelopes plus retained failed-test receipts verified.
No historical evidence may be rewritten or normalization silently substituted.

## Scope, candidates and source policy

Research and documents only. Seek two concrete dedicated Linux mini-PC/server
candidates and two remote Linux VM candidates. A product is a research candidate
only when exact model/configuration and attributable vendor evidence exist; no
claim of stock, India delivery, quota or suitability without explicit evidence.
For C obtain manufacturer/model and memory part/capacity through narrowly selected
read-only CIM fields, then official compatibility information. No serial/service
tag/UUID/user/host identity, environment, credential or raw configuration collection.
If exact compatible upgrade cannot be established, C=NO_VERIFIED_CANDIDATE.

Permitted local measurement, once without elevation and <=45s each:
Get-CimInstance Win32_ComputerSystem Manufacturer/Model only;
Win32_BaseBoard Manufacturer/Product/Version only; Win32_PhysicalMemory
Capacity/PartNumber/Speed/FormFactor/SMBIOSMemoryType only;
Win32_PhysicalMemoryArray MaxCapacity/MemoryDevices only. Catch inaccessible fields
as UNKNOWN. These identity/specification observations do not refresh Phase13.16
RAM/headroom or establish motherboard upgrade compatibility by themselves.

Public research allowance: <=12 search queries and <=32 page/metadata opens/clicks,
<=90min total, no automatic retry. Official manufacturer/store/datasheet/certification
sources first (Dell, Lenovo/PSREF, ASUS, System76, Slimbook, Canonical); official
AWS/Lightsail/DigitalOcean regional catalogs/pricing/security docs for remote
candidates; official current-host manufacturer manual after identification; Ubuntu
release/checksum text, Adoptium/docker-library publisher docs for artifact identity;
Docker/Microsoft security/support docs as needed. Search discovery may return other
sources but unverified snippets are not final proof. Third-party India retail can
corroborate seller price only with exact model/configuration, seller/tax/availability
qualifiers. Do not contact sellers, log in, submit quotes or use a cloud API.
No binary/image/software download, registry authentication, account creation, VM/
container/host connection or settings change. Textual SHA metadata is permitted.

Capture URL/publisher, local UTC retrieval timestamp, model/region/currency,
tax/shipping/warranty/expiry terms or UNKNOWN, short paraphrased factual extract
and evidence quality. Do not copy entire copyrighted pages or mistake documentation
for enforcement. Foreign prices stay in original currency unless an official dated
rate is retrieved within budget; no guessed exchange rate. Online listing is not
a guaranteed quote or reservation. Unavailable content stays unverified.

## Decision rules frozen before research

Use exact G01-G15 labels/semantics from committed Phase13.15 and13.17 matrices.
Each candidate cell uses MEASURED_PASS, DOCUMENTED_CAPABILITY_NOT_TESTED, UNKNOWN,
FAIL or NOT_APPLICABLE_WITH_JUSTIFICATION with source, test, authority and consequence.
G01 measures repository only. No prospective host enforcement is measured here.
Do not waive G05 AppArmor by silently renaming it. Hard blockers first: capacity,
specific attributable price, OS compatibility, mandatory security feasibility,
witness independence and custody. Full mandatory cost unknown => total TBD, never0.
Separate known component subtotals from total; show tax/shipping/energy/network/
backup/disk/logging/support/witness costs, usage assumptions and12-month formula.
No arbitrary score or cheapest ranking when inputs differ or totals are unknown.

Keep4vCPU/8GiB/60GiB workload target and inherited host reserve assumptions. Historical
Windows7.65GiB total/0.76-0.85GiB free is not viable as-is.16GiB is only an upgrade
candidate,32GiB only planning preference; retain14GiB available admission screen.
Preserve software-witness surviving-root claim and full-authority two-grant/no-HTTP
counterexample. No hardware monotonicity, power-loss or multi-host guarantee.
OCI exact subject/root/signature verification absent => OCI_SIGNATURE_NOT_VERIFIED.
No vendor claim closes LSM/userns/network/sink/HALT/recovery or governance tests.

## Artifacts, verification and termination

Add only docs/operations/phase-13.18-{plan,candidate-shortlist,verified-cost-model,
security-evidence-matrix,provisioning-authorization-draft,runbook,validation}.md,
docs/architecture/kite-concrete-linux-host-candidates.md and
research/phase-13.18/generation-1/evidence-manifest.json. Ignored tmp/phase1318 may
hold sanitized receipts/inventories. Plan raw SHA frozen before research; material
changes need additive numbered hashed amendment, never an in-place plan rewrite.

Run project verifier/secrets using uv run --offline, local links, exact gate names/
all candidate cells, source/time metadata, cost arithmetic/TBD propagation, schema,
canary/untracked whitespace, Git diff/staged/untracked inventory and preservation.
No executable change or new validator; Java/Python unit/integration/Ruff/mypy NOT_RUN.
Replay twice from disk: SHA256 sorted compact ASCII JSON manifest payload,
ensure_ascii=True,allow_nan=False,UTF-8/no newline; self-fingerprint excluded.
Retain failed inspections/checks and corrected outcomes. Hash all910 tracked files
again, raw versus registered source normalization kept distinct.

Mismatch/protected change/secret exposure/prohibited action => BLOCKED and stop.
Missing material quote/compatibility/custody/security => CONCRETE_HOST_SELECTION_INCOMPLETE.
Only a defensible fully supported concrete decision permits
CONCRETE_HOST_SELECTION_READY_AUTHORIZATION_PENDING, never acquisition approval.
Packet DRAFT / NOT_SENT / NOT_APPROVED; spending/provisioning NOT_AUTHORIZED.
No workload resources here; future max3 workloads plus1 identified helper and exact
ID/finally cleanup unchanged. No real Kite/token/account/WebSocket/order/DB/HALT/
arm/execute/trading/startup, host change, procurement, stage/commit/push. V2/second
review/four domains PENDING; NO_GO/MIS NOT_READY; Phase12 BLOCK,H1/H2 FROZEN,July
SEALED,INR10,000 and ZERO_CALL_ABORT preserved. Stop after the evidence report.
