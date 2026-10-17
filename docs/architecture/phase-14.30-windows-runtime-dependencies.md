# Phase 14.30 Windows offline runtime boundary

The supported selected offline package imports the existing calendar/scheduler code,
which uses ZoneInfo("Asia/Kolkata"). Windows has no default IANA timezone search path
in the assessed interpreter. The installed and already locked tzdata version is 2026.4.
This is a runtime requirement, not merely a test convenience.

Declare tzdata==2026.4; sys_platform == 'win32'. Both the project lock dependency edge
and requires-dist metadata carry the same marker and exact constraint. No artifact
digest, other package version, dependency group or trading implementation changed.
Lock consistency is checked structurally against the committed lock, existing package
entry and installed versions; no resolver refresh or registry contact was performed.
The architecture guard permits tzdata but retains all prohibited broker imports.

The new assessment module validates metadata, classifies the active runtime graph,
checks installation inputs, rejects missing/undeclared installed packages, and verifies
wheel metadata and every RECORD member. It has no install, broker or recovery authority.
Build and environment creation live in explicit offline scripts, outside the wheel.

The selected wheel contains the unchanged Phase 14.28 software/resource allowlist.
Phase1428Resources.v1 remains pinned to
94d40ebdc73f7a4f2da540568e530f5c5cf63e9c5867c049920942d6abdc9e8d.
Canonical universe SHA-256 remains
4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729,
with 1,116 CRLF endings and 1,115 unique enabled NSE symbols in frozen order.
Configuration, risk v2, causal execution v1 and six strategy implementations are unchanged.

Source ZIP archives contain the selected offline build closure, not the complete
repository or a backend-produced full-project sdist. Cached Hatchling 1.27.0 is used
without network resolution. Reproducible outputs do not attest backend provenance.

The new wheel runs under the pre-existing isolated interpreter with checkout/credential
reads denied and a socket guard. This verifies that environment only. Fresh installation
is blocked by missing approved runtime archives. No dependency files were copied into
the fresh environment. Python guards do not establish OS-level production egress controls.
No claim covers new non-Windows runtime support, genuine qualification or live readiness.
