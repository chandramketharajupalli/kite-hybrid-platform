# Containment, evidence transfer and recovery

Filesystem: guest-owned disposable storage, read-only byte-identical sources, no host sealed-custody or writable repository mounts, no production database or host credential access, reparse escape rejection, independent write inventory. Application path checks are insufficient.

Network: guest NIC disabled or independently blocked; no egress/DNS/broker/NAT/proxy/host bridge or guest reactivation. No host firewall changes. Socket monkeypatches are insufficient.
Credentials: no Kite keys/tokens/encryption keys, DB credentials, host stores, .env mounts, profile inheritance or broker configuration. Only synthetic canaries may test future enforcement.

Processes: exact executable identity and PID+creation time, retained handles, verified owned jobs and descendants, assignment before resume where applicable, no PID-only termination; timeout cleanup preserves unrelated processes and complete failure receipts. Historical WinError5/31 causes, conhost rejection and supervision limitations remain unresolved.

Proposed maxima:4processes,3children,300s wall,120s CPU,512MiB memory,8MiB output,64MiB writes,0retries. They are neither approved nor enforced. OS/job limits require qualification; output/storage/network require independent controls. Observations do not establish enforcement.

Guest evidence: preserve JUnit, raw stdout/stderr, exit/process/resource receipts and failed-run logs with SHA256 manifest. Export only through separately approved one-way or host-controlled transfer into fresh quarantine with size/type limits and manifest reconciliation. Guest never writes host sealed custody; only host seals accepted evidence. On crash preserve available evidence under explicit authority, revoke lease, stop without retry. Shutdown/destruction require owner cleanup authority and post-destruction checks for residual storage, jobs and connections. Historical custody cannot be rolled back or cleaned.

All filesystem/network/credential/process/resource enforcement remains UNVERIFIED.
