# Offline process-supervision boundary

Phase1444EEProcessSupervisor.v1 is new, unqualified diagnostic support. No production module is wired to it. Its TEST_ONLY marker is a scope restriction, not authentication. It does not authorize arbitrary guest or trading actions, and Windows Job Objects do not establish filesystem, network or credential isolation.

The corrected lifecycle creates an owned job with kill-on-close, launches a disposable Python process suspended, verifies pinned image bytes and creation identity, assigns before resume, and retains handles. Observed children must be job members with consistent creation identity and a retained parent lifetime interval. Timeout/error cleanup targets only the retained owned job; a pre-assignment failure targets only the owned suspended root handle. No PID-only process tree termination. Normal cleanup closes handles once; failures retain a finally receipt.

Image handling distinguishes PROCESS_EXITED, PROCESS_ACCESS_DENIED, PROCESS_IMAGE_QUERY_FAILED, PROCESS_IDENTITY_UNVERIFIED and PROCESS_IDENTITY_VERIFIED. Exit is separate from identity: an exit does not prove image identity. A failed image query cannot be promoted by retry or matching output. An unverified active process stops supervision; inaccessible exited children are recorded as coverage gaps. Sampling can miss short-lived processes, and coverage is not perfect.

Qualification stopped on an unexpected live WinError5 image query. Owned-job cleanup succeeded and the receipt survived. This demonstrates fail-closed behavior for that observation, not reliability closure. Historical E-D WinError31 evidence and supervisor remain unchanged. No guest containment or owner authentication is established.
