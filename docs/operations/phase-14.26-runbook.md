# Phase 14.26 offline review runbook

Use the repository Python environment with `PYTHONDONTWRITEBYTECODE=1` and Python `-B`. All fixture journals, caches, approval records, witness records and receipts belong in a unique external temporary directory. Never point a fault interface at historical evidence or operational state.

1. Verify develop, the full baseline and local origin/develop. Verify all tracked baseline hashes, the preserved Phase 14.22 plan, historical external evidence and the frozen Phase 14.26 plan before running a gate.
2. Run `pytest tests/test_phase1426.py --assert=plain -p no:cacheprovider` with external basetemp and JUnit output. Run the full relevant Python suite, Ruff `check --no-cache src tests`, and strict mypy with an external cache. Retain failed receipts and corrective reruns. No Java contract changes are authorized here.
3. Inspect both historical and new alert metrics, including the eight original missed IDs. Require ten timely monitored alerts and zero missed obligations. Independently confirm that deliberately missing alerts block certification.
4. Inspect consume-before-callback, competing consumer, crash/restart, replay and surviving-witness rollback results. The complete-authority rollback counterexample must remain reproducible. Review accounting reconciliation, unresolved EOD positions and cross-session HALT/acknowledgment denial.
5. Run `scripts/research/verify_phase1426.py` twice using Python `-B`. The script creates only external synthetic replay directories. Compare the returned JSON bytes. The manifest records the final report, protected hashes, command receipts and initial failures. A separate external certificate binds the two replay receipts; a separate command receipt verifies the certificate.
6. Verify LF stability, filtered/unfiltered Git-byte equivalence, whitespace and secret/canary scans. Confirm no tracked/index changes and only the exact declared additions plus the preserved Phase 14.22 exception.

Any missing evidence, unexplained mismatch, unauthorized clearance, duplicate accounting operation or incomplete mandatory coverage prevents offline validation. Do not repair journals or delete ambiguous ownership/consumption records. Review the captured failure and require a separately authorized follow-up. Do not stage, commit, push, fetch, call Kite, send notifications, change capital or enable live access.

Genuine strategy qualification remains blocked. Gate A and Gate B remain NOT_AUTHORIZED, Stage B ZERO_CALL_ABORT, Phase 12 BLOCK, H1/H2 FROZEN, July SEALED and operational readiness NO_GO. Synthetic success does not change these decisions.
