# Java validation readiness

JAVA_COMPILATION_NOT_EXECUTED. JUNIT_NOT_EXECUTED. Runtime authorization NOT_AUTHORIZED.

The source targets Java 21 and pins Maven wrapper 3.9.11. Executables were not launched or independently qualified. Wrapper presence does not prove distribution/dependency/plugin closure or containment. Candidate archives are absent. Installed executable identity and approved runner remain unverified.

Future proposals only, in a disposable copy with approved writable outputs, credential isolation and independently blocked network:
- mvnw.cmd -o -pl apps/trading-core -am -DskipTests compile
- mvnw.cmd -o -pl apps/trading-core -Dtest=TradingApiAuthorizationTest,TradingProjectionTest,TradingReadControllerTest,TradingFoundationTest test

The targeted command assumes verified reactor dependencies are already available; never silently expand tests or ignore missing tests. Re-freeze exact commands for the approved runner. No integration profile, broker diagnostic exec goal or full suite. Compiler/test compilation may touch existing sources; assess test-class loading before authorization.

Proposed review budgets, not approved/enforced limits: one guest, four processes, 300 seconds wall, 240 seconds CPU, 2 GiB memory, 10 MiB output, 1 GiB disposable writable storage, zero retries. Pin JDK/Maven/plugins/archives; verify child ownership, timeout cleanup, process creation identity, filesystem/network/credential isolation and unrelated-process protection. Preserve command/environment identities, stdout/stderr, JUnit XML, exit/resource receipts and failed runs. Cleanup authority remains unavailable.

Original 46/18/31 families NOT_EXECUTED. No Maven/JUnit/pytest/Ruff/mypy or native supervisor execution. No provider, broker, order or network operation.
