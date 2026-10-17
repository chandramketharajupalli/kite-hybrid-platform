# Phase 14.31 artifact trust boundary

Gate 31-A performs local reads, canonical manifest construction and synthetic policy
validation only. The new module has no transport, subprocess, environment creation or
installation implementation. The preparation script uses Git read operations and the
existing bounded cache auditor. No source index is contacted and no cache is mutated.

The target is CPython 3.12.14 / Windows AMD64. Runtime closure is independently traversed
from the frozen lock graph and installed Requires-Dist metadata with markers applied.
Exact names, versions and direct/transitive relationships must agree. Each distribution
has exactly one compatible locked wheel; ambiguous alternatives fail closed. Optional
extras are not implicitly activated. Build/test/development groups are reported separately.

The eight runtime wheels are additional to the sealed local Phase 14.30 application
wheel. The cached Hatchling backend is not needed to install that already-built wheel.
The local Python ensurepip bundle is an installer candidate, not a runtime dependency
and not an approved installation input. Its raw hash is recorded without execution.
Installer identity, provenance and approval must be settled separately before Gate 31-C.

Trust levels remain distinct:

1. Independently reviewed expected artifact digest: absent for these eight artifacts.
2. Frozen uv.lock digest correspondence: available for all eight.
3. Installed distribution RECORD integrity: the historical 39 verifications remain.
4. Installed version correspondence: confirms the selected lock versions only.
5. Missing original artifacts: all eight runtime originals remain missing; the wider
   historical audit still records 38 missing third-party originals.

Official distribution URL candidates come from the exact lock entries under
files.pythonhosted.org. Host identity, HTTPS and filenames do not establish digest trust.
No mirror, internal repository, redirect or alternate source is approved. The selected
lock digest is not promoted to independent registry attestation.

Synthetic authorization checks require an independently supplied trusted decision
fingerprint and reviewed digest set. They do not implement signature verification,
establish owner identity or manufacture a trusted evidence store. The actual packet
contains no such evidence and remains PENDING. Passing a synthetic authorization test
does not authorize a network request. Future integration must verify owner decision
evidence through a separately approved custody mechanism before calling any transport.

Quarantine path validation rejects relative paths, traversal, checkout destinations,
existing destinations and symlink/junction ancestry. It does not create directories or
eliminate a later filesystem race. Future acquisition must use exclusive creation,
revalidate handles and enforce OS-level access/egress controls. Python checks do not
establish production containment, independent custody or power-loss durability.
