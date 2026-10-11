# Phase 13.11 plan amendment 2 — integration housekeeping budget

Parent plan SHA-256: b913dba5cfadc1f8d432f3fa356b55fbbee2a17cb463ebcc94030042d1279807.
Amendment 1 SHA-256: ffbb9060a3c9dc6edaca1b3aec1640b6b4800f85331f24c6bc646864231964b9.

The combined focused run starts Testcontainers' existing Ryuk cleanup container for
PostgreSQL tests. It remains running while the direct-Docker sandbox tests execute.
The two-owner race therefore can overlap three workload containers plus one cleanup
container. The original three-total-container budget did not account for this
housekeeping overlap; record it as a budget discrepancy, not as an isolation pass.

For the remaining full integration run, bound concurrent workload containers to
three, owner instances to two, and total including Ryuk to four. Do not disable the
existing cleanup mechanism or modify historical tests. Each owner/witness retains
its existing CPU/memory/PID/network restrictions. No security acceptance criterion,
network acquisition budget, phase duration, retry budget or operational authorization
changes. This additive amendment does not retroactively claim the original budget
was met during the combined focused run.
