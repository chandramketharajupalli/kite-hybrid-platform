# Console-host trust boundary

Phase1444EGConsoleHostIdentity.v1 separates executable-byte identity, current signature assessment, historical process lifetime, console-session association and owned Job Object membership. None substitutes for the others. The original receipt verifies an exact conhost path/hash, creation time, direct parent lifetime and owned job membership. Historical signature and console-session association were not captured. Current file/catalog observations are not historical process metadata.

No name-only or PID-only trust. A later bounded allowance requires exact canonical Windows system path and raw SHA256, approved signature/revocation policy, retained handle/PID/creation identity, approved root lifetime and membership in the retained owned job. Console association alone grants no ownership or termination permission. An unknown child remains a fail-closed rejection; unrelated console hosts are never enrolled or terminated.

The pure policy function in verify_phase1444eg.py accepts only TEST_ONLY synthetic evidence. Its fixtures are not independent authentication or native Windows enforcement. It creates no process, opens no production credentials or database, performs no network access and grants no real authority. Tests establish policy predicates, not runtime patch integration, guest containment or supervisor certification.

Selected current alternative D: retain rejection and defer. Candidate B is an exact helper pin, with no wildcard or system-process class allowance. The candidate remains incomplete pending signature freshness policy, independently authenticated live approval and integration of those gates. The historical18remaining live cases and original31database tests remain NOT_EXECUTED.
