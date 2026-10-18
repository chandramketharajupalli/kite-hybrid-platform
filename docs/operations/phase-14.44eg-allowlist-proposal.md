# Alternatives and exact non-applied candidate

Current selection: D_RETAIN_REJECTION_DEFER. Candidate for later review: B_EXACT_IDENTITY_BOUND_HELPER_PIN. No current source, allowlist or live custody is changed.

| Alternative | Changes and Windows evidence | Security, children and job handling | Fidelity, tests and rollback |
|---|---|---|---|
| A Avoid console host | New execution-mode or interpreter identity needed; current CREATE_NO_WINDOW still produced a helper in the retained receipt. Availability/semantics unverified. | Could reduce helper surface; cannot assume no child or different stdio behavior. Retain all job/identity checks. | Requires runtime/stdio/exit tests and new executable provenance. Historical source unchanged; remove only new candidate after approval. |
| B Exact helper pin | Add only exact system path/raw hash to a new allowlist; additive source copy binds new custody and digest. Signature freshness and independent approval integration unresolved. | Keep retained handle/creation, parent lifetime and owned-job checks. Never trust all Microsoft/system processes. | Smallest identity fragment. Requires negative identity tests plus future live lifecycle/sentinel validation. Never edit or remove historical files. |
| C Separate observation | New observer/reporting logic; no termination authority from console association. | Cannot safely ignore an unidentified member of the owned job; unknown members must still fail closed. | Larger semantic change and child-coverage tests; not selected. New observer only could be removed after review. |
| D Retain rejection | No active change. | Existing rejection/owned cleanup stays in force. | Selected while signature freshness, console association and live authorization integration are incomplete. No rollback needed. |

The exact fragment below is a proposal for a future additive COPY, not a patch to apply to the original path. Complete future bytes are not frozen: authorization integration and future replay command remain unresolved. Future source SHA256 is null. The command-allowlist candidate adds one exact helper pin and removes the old replay command; it is retained as .json.proposal outside live custody. No unrestricted path, filename-only or signer-only allowance is proposed. Existing imports and replay semantics are unchanged by this fragment.

Original source: apps/strategy-engine/src/strategy_engine/phase1444ef/supervisor.py

Original raw SHA256: a70a068cf71b8da035d5d15c764ef8721b73ce265fd3eaa4b05ffa973de76597

Proposal fingerprint: 64b362c461a52ac327ab9d95c71c83abae788efe6576a16b14c97fdc5110710b

Exact diff SHA256: 726d42fe3800263b94dd0b80d514037fe8a8a37ea94622f7a39ebb0e7c5bc371

Candidate allowlist SHA256: 404936e191d9662587345eec3b16a160d86427bc442d5d91d0d41975c8d3b1be

Proposed future paths (UNAPPROVED):

- apps/strategy-engine/src/strategy_engine/phase1444eg/supervisor.py
- D:/Yogendra/offline-validation-evidence/phase-14.44E-G/live-qualification-generation-1/command-allowlist.json

Diff display omits blank context markers; exact raw diff bytes remain in identity-pin-candidate.diff.txt and patch-proposal.json in external custody.

```diff
--- a/apps/strategy-engine/src/strategy_engine/phase1444ef/supervisor.py
+++ PROPOSED_ADDITIVE_COPY/apps/strategy-engine/src/strategy_engine/phase1444eg/supervisor.py
@@ -13,8 +13,8 @@

 from strategy_engine.phase1444ee import supervisor as native

-CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-F/generation-1")
-ALLOWLIST_SHA256 = "8da54faebd03a9bb1fa83073ded7ae98bec693c7d3263b3be366d062e7c29555"
+CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-G/live-qualification-generation-1")
+ALLOWLIST_SHA256 = "404936e191d9662587345eec3b16a160d86427bc442d5d91d0d41975c8d3b1be"
 QUERY_RIGHTS = 0x101000  # SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION
 PROCESS_ID = native.api("GetProcessId", [W.HANDLE], W.DWORD)

```

Signature and real-authorization prerequisites are external pre-execution gates in this incomplete fragment, not implemented real authentication. Synthetic predicate tests do not validate their integration. Future acceptance requires complete frozen bytes, approved invariants, approved custody and explicit owner decision before any live workload. Unknown identity rejects. Cleanup is owned-job-only; no PID-only termination or unrelated console inclusion.
