# Phase 13.12 amendment 2: Windows ownership-query correction

Parent amendment 1 SHA-256:
`d5920d5e0f7aa2102689a8272e2b0c9af562f7c11e8c3b6b0b84504e5ba692de`.
The original frozen plan and amendment remain unchanged. This correction is within
the two corrective reruns already authorized; no resource or acceptance relaxation.

The first recovery run failed because Windows native argument handling removed the
embedded quotes in a Docker Go-template label lookup. Docker returned exit64 before
ownership could be verified. Cleanup was quarantined; every later fixture creation
was denied, so only one container was created and no resource overlap occurred.
Failsafe reports ten failures: nine test invocations plus the after-all failure.
95 unit tests passed. Preserve the log, XML, source and run-specific raw records.

Before resuming, the single stopped container was independently inspected as JSON;
its exact full ID and run-ownership label matched the retained creation record.
It was removed by that ID, and a successful Docker listing confirmed absence.
No unrelated container was touched. This resolves the specific cleanup uncertainty;
it does not erase the failed cleanup or bypass quarantine in a running verifier.

Replace only the quoted Go-template label lookup with a successful Docker listing
filtered by exact run label and exact name. Require the one expected name. Keep
successful absence checks, finally removal, failure propagation and denial on
unknown ownership. Run a fresh focused diagnostic process; broader suites remain
blocked until its cleanup, resource-budget and XML gates pass. No new acquisition,
host security change, operational authorization or historical evidence edit.
