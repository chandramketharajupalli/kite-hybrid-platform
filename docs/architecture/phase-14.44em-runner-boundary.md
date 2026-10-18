# Offline runner boundary

The additive policy is inactive and pure. It validates supplied fixture fields; it does not authenticate observations, construct a Job Object, issue a real lease or launch a process. evaluate_fixture returning no errors means only that synthetic inputs agree. real_execution_authorized always returns False.

Identity requires exact executable path/hash, pinned argument array and working directory, retained handle, PID plus creation time, parent PID plus parent creation time, explicit owned-job membership and assignment before resume. Failed image queries cannot verify identity. A normal exited state differs from access denial. Child ownership cannot be inferred from a recycled parent PID. Cleanup may terminate only a verified owned job, never arbitrary PIDs or a sentinel outside that job.

OS enforcement is independent of this model. A job can support process count/CPU/process or job commit-memory limits when correctly configured; this is not proof of working-set, writable-storage, output, network or credential containment. A wall deadline needs qualified supervision. No native runner is implemented or activated here.
