# Containment and evidence cleanup requirements
Filesystem:read-only byte-identical source,guest-only disposable storage,no writable repository or sealed-custody mounts,no production DB or host credential files,reparse rejection and independent write inventory.
Network:no egress,DNS,broker,NAT or inherited proxy; independent enforcement required.
Credentials:no Kite/API/token/encryption material,production DB secrets,profile inheritance or host stores.
Process:exact executable,PID+creation identity,verified owned children/jobs,CPU/memory/process limits,owned-only timeout cleanup,no unrelated-process termination. All controls UNVERIFIED.

Guest generates complete logs,Junit,process/resource/exit and failed-run receipts. Export by separately approved controlled one-way path to fresh host quarantine; verify SHA256,size/type and manifest before host seals. Guest cannot write sealed host custody.
On crash preserve partial evidence,stop and revoke any future lease; no automatic retries. Guest destruction and post-destruction verification require explicit cleanup authority. Rollback only disposable authorized assets,never predecessor evidence. No cleanup performed here.

Owner trust NOT_ESTABLISHED,trusted time/replay/resource enforcement UNVERIFIED,qualification/installation/provisioning/native activation NOT_AUTHORIZED. GateA/B NOT_AUTHORIZED,StageB ZERO_CALL_ABORT,Phase12 BLOCK,H1/H2 FROZEN,July SEALED,Seriesv2 PROPOSED_NOT_ADOPTED,amendment NOT_AUTHORIZED,NO_GO.
