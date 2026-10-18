# Future offline build readiness

Require pinned Java21 full JDK and existing wrapper3.9.11 or approved Maven. Repository enforcer specifies JDK[21,22), Maven[3.9.11,4). Compiler3.14.1, Surefire/Failsafe3.5.4, enforcer3.6.2 and exec3.6.3 are source-bound settings; direct plugin presence does not prove transitive plugin closure.

Require complete isolated offline dependencies/plugins, immutable source copy, disposable writable output, independently denied network, no production credentials/user Maven settings, process identity/child ownership and approved CPU/memory/process/output/storage/timeout limits. No approved limits invented.

Targeted compilation/JUnit and existing Kite callback compatibility tests require separate exact runner/command approval. Synthetic broker/identity mocks only: validate state/nonce consumption, callback errors, route restrictions/static status and no broker/order effects. No full suite, network Maven or historical46/18/31 tests.

JAVA_COMPILATION_NOT_EXECUTED; JUNIT_NOT_EXECUTED. Maven not run. Tools/cache presence does not qualify runner. Implementation/runtime/GateA/B NOT_AUTHORIZED; live orders DISABLED; operational NO_GO.
