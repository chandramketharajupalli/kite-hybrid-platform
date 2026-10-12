# Phase 14.12 additive build correction

Frozen original plan remains unchanged (SHA-256
e3b8f62dbe83aa4d11ba8a2914ff7d53a50cd1bd140e78724148c991332ff91c).
First offline adapter verify attempt exited 1 before compilation/tests because
build-helper 3.6.1 requires uncached file-management 3.2.0. No download attempted.
Replace build-helper with compiler 3.14.1's editable compileSourceRoots parameter,
verified from its locally cached META-INF/maven/plugin.xml. Keep exactly the same
explicit source allowlist; set implicit=none. No parser copy or trading artifact
dependency. This additive amendment is the sole extra documentation path required
by the frozen plan's correction rule. Retain the first failure in validation.
