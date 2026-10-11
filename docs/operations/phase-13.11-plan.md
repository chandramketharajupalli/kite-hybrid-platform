# Phase 13.11 frozen plan v1

Baseline: clean develop, HEAD/local origin/develop
e4ff258f6dd6bbb3d52195e1359e7de192194699. No fetch/pull or remote-freshness claim.
845 tracked raw hashes captured before edits. Registered Phase13.7-13.10 envelopes,
plans/amendments, G1, 18 normalized sources, 63 research files, 162 historical files
and 25 K/L files match. Preserve all existing source/evidence bytes; changes require
an additive hashed amendment. Root AGENTS.md applies; user authorizes bounded Linux
runtime acquisition for this synthetic phase, not host JDK installation.

## Source review and allowed scope

Existing runtime is SyntheticOwner/SyntheticHalt/LoopbackPeer, package-private,
java.base-only; signed synthetic HALT then Ledger.consume precedes ticket/socket/send.
FencedLedger and PostgreSQL/coordinator implementations are test-only. RootRecovery
fixtures invoke the real owner path but run on Windows, not in an OS sandbox.
Prior source hashes match those inspected in Phase13.9/13.10. No existing runtime,
tests, POM, reactor, trading/config/risk/migration or historical evidence edits.

May add test-only SandboxOwnerProbe.java, SandboxWitnessProbe.java, JavaSandboxIT.java
and bounded supporting test resources under apps/synthetic-credential-owner/src/test/.
These must not enter the runtime JAR. May add this plan, numbered amendments,
docs/architecture/kite-synthetic-java-owner-sandbox.md,
docs/operations/phase-13.11-{runbook,validation}.md and additive research/phase-13.11/
generation-1 evidence. Ignored tmp/phase1311 holds acquisition metadata, images/
artifacts, before hashes, progress and raw logs. No generated binary/log staged.

## Bounded acquisition and trust gate

Cached images contain no JDK. Host JDK21.0.12 is Windows; Docker is linux/amd64.
Permit HTTPS metadata research only against official Adoptium/Eclipse, Docker Hub/
registry/auth services, Docker official-images and vendor GitHub repositories, and
Moby's published seccomp source. Maximum 30 metadata requests, 8 MiB per response,
64 MiB total retained metadata; 30s per request, no automatic retries. Public registry
authentication stays memory-only, never logged. No Git fetch or repository repair.

Select one published fixed JDK21 patch/build from official Eclipse Temurin catalog,
never latest/floating21. Verify independently: vendor documentation identifies the
official container publisher; Docker official-images catalog binds version/architecture
to immutable vendor Git commit and Dockerfile; inspect that exact recipe, release
checksums/signature verification commands and platform mapping. Pin manifest index,
linux/amd64 manifest, config and layer digests; record HTTPS sources and timestamps.
TLS-origin plus independent official catalog/source linkage is the minimum provenance
threshold; digest identity alone fails. Record whether image signatures/attestations
are actually authenticated. Recipe signature checks are not a fresh independent
signature verification of the delivered image, and must not be represented as such.
If source linkage is absent/conflicting or trust remains unverifiable, do not execute.

Allow one docker.io/library/eclipse-temurin immutable platform pull after that gate,
compressed layers <=600 MiB, declared total runtime size <=1.5 GiB, <=300s, no retry.
No unofficial mirror, install script, host JDK install, alternate image fallback or
implicit networked Docker build. Read-only inspection before execution checks config,
Java version and expected files. Exact provenance verdict must distinguish publisher
origin verification from cryptographic image signing; incomplete proof => PARTIAL.
Missing/unverifiable artifact stops sandbox branch; permitted offline regressions may
continue, without inventing sandbox passing/skipped counts.

## Sandbox and fixture hypotheses

Actual SyntheticOwner.observe must run with fake credentials, RSA3072 HALT, bounded
one-use request, independent test witness and a numeric-loopback fake peer. Prefer
separate disposable witness and owner containers sharing only an isolated network-none
namespace: witness/peer sockets on 127.0.0.1, owner has no witness storage mount.
Witness test persistence may use an owned named volume with forced immutable retirement
markers and separately retained root; explicitly distinguish this new fixture from
PostgreSQL historical tests and from physical power-loss durability. Restarts must
not reset IDs. No environment/argv bearer secrets; deterministic synthetic canaries
generated internally, pinned public bootstrap only. Missing trusted root denies.

Owner: non-root UID/GID, cap-drop ALL, no-new-privileges, read-only root, read-only
minimal artifact mount, bounded tmpfs scratch, memory/CPU/PID limits, private PID/IPC,
no host networking/home/socket/secrets. No root trading artifacts or JDBC dependencies.
Use independently inspected Moby default seccomp as a baseline; a separately hashed
test policy may remove fork/vfork and restrict clone to threads while retaining
default restrictions. clone3 must fail with documented fallback semantics. Do not
use default-allow or privileged bypass. If unsupported, classify unavailable/partial.
Report AppArmor/SELinux/user namespaces as measured, not assumed.

Positive: one owner send/peer receipt, durable retirement before dispatch, replacement
denied. Negative Java probes: root write, forbidden host path, process spawn, numeric
documentation-address TCP and invalid DNS inside verified no-route namespace; explicit
exceptions/denials, not timeout-only success. HALT expiry/revocation/conflict, duplicate
ID, unsafe sinks and canary containment must fail closed. Never probe a broker/public
service. Concurrent instances and kill/restart use only owned fixture processes.

## Test gates, evidence and budgets

1. Acquire/verify metadata and one immutable image, or report branch unavailable.
2. Existing FenceLedgerIT/IsolationLedgerIT gate before new owner HTTP; preserve
   dual-store and all-authority rollback counterexamples, no HTTP in negative control.
3. Focused new sandbox tests with measured inspect and Java output, plus existing
   FenceOwnerIT/RootRecoveryIT for recovery/kill/quarantine/uncertainty. Retain failures.
4. Full standalone unit/integration then root Java unit/architecture; JDK21/wrapper,
   Maven offline except explicitly bounded image acquisition. No shared production
   changes => root broad integration and Python/Ruff/mypy not needed.
5. JAR/classes/dependency/jdeps scan and actual module-limited RSA probe; test-only
   fixture classpath separately inventoried. Scan canaries in selected logs/reports/
   dumpstreams without printing them; record accessible dump provenance and limitations.
6. Project/secrets/diff/untracked/staged checks; all protected/raw before hashes;
   two canonical manifest replays. Sorted compact ASCII JSON, allow_nan=False,
   UTF-8/no newline; exclude self-reference. Evidence consistency is not approval.

Total engineering budget <=120 minutes; <=3 concurrent owned containers, <=2 owner
instances, sockets <=3s, processes <=30s, recovery <=30s; <=2 corrective reruns per
failing gate. Acquisition timeout is separately bounded above. Remove only owned
containers/volumes/scratch when safe; do not delete prior logs/evidence. Log meaningful
progress in validation and ignored progress log. Stop on unexpected Git/protected
change, real credential need, external runtime egress, unsafe policy or unverifiable
pre-dispatch consumption. No weakening assertions to force green results.

## Decision and retained restrictions

VALIDATED requires provenance gate, actual Java sandbox, mandatory enforced denials,
recovery, complete regressions and preservation all pass. Otherwise PARTIAL/BLOCKED.
All-authority rollback, privileged restore, hardware monotonicity, physical power loss,
multi-host consensus, live provenance/HALT/logs/OS certification remain unproven.
Zero real Kite/token/WebSocket/historical/account/order/trading DB/HALT changes.
No operational startup/deployment, .env/policy edits, commit/push or destructive Git.
V2/governance second review/four reviews PENDING; real access NOT_AUTHORIZED; NO_GO,
MIS NOT_READY; INR10,000, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, ZERO_CALL_ABORT
preserved. Stop after evidence/report; no automatic next phase or operational action.
