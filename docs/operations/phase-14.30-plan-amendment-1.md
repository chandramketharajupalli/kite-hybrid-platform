# Phase 14.30 additive plan amendment 1

The original plan remains frozen at
16f6d5825affb63cd3eeaab2c7dcfd3247fc4d87543f94c12b04a2a5843b905b.

Focused receipt focused-1 records 49 passes and one architecture failure: the exact
project dependency allowlist lacks the newly authorized Windows timezone dependency.
Permit one additional tracked edit to tests/test_architecture.py under the strategy
engine: add tzdata to ALLOWED_PROJECT_DEPENDENCIES. Preserve every forbidden import
and existing signal-only assertion. The Phase 14.30 tests separately enforce the
exact Windows marker, version and lockfile edge, including negative cases.

This narrowly corrects the plan's prohibition on existing test edits. It does not
permit changes to execution, calendar behavior, research, resources or evidence.
Record the test file's baseline and final raw hashes as an intentional change;
all other protected hashes must still match. Retain focused-1 and the initial lint
receipt (formatting and mixed root/Python lint configuration), then run corrective
checks using the strategy engine's declared Ruff configuration.
