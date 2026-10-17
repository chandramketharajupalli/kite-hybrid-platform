# Offline readiness runbook

1. Verify develop, HEAD/local origin, tracked hashes, existing untracked preservation set and sealed historical manifests. Stop on discrepancy; never repair historical evidence.
2. Verify retained installation certificate, command receipts, archive hashes, installed inventory and replay outputs without rerunning installation or accessing brokers.
3. Receive an explicitly identified approved local original compliance record. Record custody/hash privately; expose only approved sanitized clauses. Verify sender/date/source authenticity independently. Never search mail or infer missing metadata.
4. Populate the clause matrix and category-specific retention/deletion policy from verified evidence; resolve ambiguity conservatively. Define exact calendar/action coverage and genuine data windows.
5. Run pure synthetic validators and tests with external caches. No genuine token or performance substitutions. Preserve failures and reruns.
6. Verify new artifact hashes, scans and two manifest replays. Preserve existing untracked Phase 14.34A inputs and Phase 14.22 exception separately.
7. Stop with acquisition NOT_AUTHORIZED and RC CONDITIONAL until all missing evidence and separate governance/owner decisions exist. Do not stage, commit, push, acquire data or send external messages.

Reproduce focused checks with Python -B -m pytest --assert=plain -p no:cacheprovider scripts/research/test_phase1435.py, PYTHONDONTWRITEBYTECODE=1 and an explicit external --basetemp. Ruff uses --no-cache; strict mypy uses an external --cache-dir. The evidence manifest records exact final receipts and inventory; external sealing certificate and replay receipts avoid self-hash cycles.
