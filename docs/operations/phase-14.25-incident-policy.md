# Phase 14.25 synthetic incident policy

Version: Phase1425FaultPolicy.v1. Provenance: SYNTHETIC_FIXTURE.
The registry has 21 deterministic scenarios. Scheduler faults use existing named
crash boundaries; observation faults mutate synthetic commands; integrity faults
alter disposable external journals/reports. Fault ground truth is declared before
observing outcomes. Monitoring ground truth remains the separately authored
Phase1424IndependentTruth.v1 fixture with Phase1424Monitoring.v1 matching.

Timely alerts match one-to-one by run/session/category within the incident's
start/deadline. Duplicates and late alerts are false positives; no timely match is
a false negative. A missed critical incident marks monitoring evidence incomplete,
blocks that assessment, preserves HALT and requires restored alerting plus an
independent retest. Acknowledgment is neither resolution nor recovery authority.
No production effectiveness or time-to-recovery is inferred from these fixtures.
