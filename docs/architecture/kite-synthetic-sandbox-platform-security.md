# Synthetic sandbox platform security: Phase 13.13

The scope is a disposable Java-owner fixture. Platform controls and governance
approval are separate. Passing a test does not authenticate a publisher, establish
an enforcing LSM, or certify production isolation.

## Image and platform trust

The cached Linux amd64 Temurin image retains platform digest
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
Local image ID equals that digest on this Docker engine. The distinct config digest
is `sha256:5b87e2cf305796b0082d40718341f9b75c2754e9cf81d228262b6902cae4e0b1`;
its five rootfs diff IDs match the inspected local image. The retained index,
platform, config and unsigned SLSA bytes are rehashed rather than trusted by tag.

[Adoptium's container documentation](https://adoptium.net/installation/containers)
identifies the official Docker image. Its
[GPG instructions](https://adoptium.net/installation/gpg-verification) describe
signatures for JDK binary downloads. Those sources do not establish an authenticated
signature over this OCI subject. The cached SLSA subject matches the platform digest,
but no trusted OCI signer/root/signature chain was verified. Record
IMAGE_SIGNATURE_NOT_VERIFIED, not signature failure or proof that no signature exists.
The retained SBOM is supporting package metadata, not current vulnerability clearance.

The measured backend is Docker Desktop's WSL2 Linux kernel. Docker advertises seccomp
and cgroup namespaces. Java diagnostic observations show an identity UID/GID map,
AppArmor disabled, and unavailable SELinux enforcement/securityfs observations.
There is no attributable LSM denial or remapped user identity. A numeric non-root
UID and seccomp cannot substitute for these controls. Docker documents
[user-namespace mapping](https://docs.docker.com/engine/security/userns-remap/) and
[AppArmor enforcement](https://docs.docker.com/engine/security/apparmor/) as distinct
mechanisms. A future separately authorized test host must supply those mechanisms;
this phase does not change daemon, WSL, host policy or profiles.

## Test resource boundary

PlatformResources is a test-only coordinator. It takes a cooperative cross-JVM file
lock and checks active/created/restarting/paused workloads before Docker creation.
Only the pinned Ryuk cleanup image is excluded. Three workload slots are the maximum.
Raw Docker creates are serialized; Testcontainers' create modifier checks the same
budget before its sequential fixture creates. This cannot prevent an unrelated
privileged actor from starting a workload between observations.

Each new resource is labeled with a unique run. Raw Docker creation returns an
immutable ID. Cleanup checks that ID and exact ownership label, removes by ID and
requires a successful absence listing. Cleanup uncertainty fences further creation.
The actual-owner fixture now removes each short-lived owner in finally, cleans
witness construction failures, verifies owned volume removal and retains final
cleanup for failed tests. Testcontainers retains its ID-based resource reaper and
explicit stop/close lifecycle. No unrelated stopped container or volume is removed.

The diagnostic fixture retains its injected-assertion cleanup and fourth-workload
denial regressions. Accepting the exact `/scratch` mountpoint diff is unchanged;
additional files or paths remain failures. The pinned-JDK attach denial still requires
the full expected IOException stack. No timeout or unrelated exception is accepted.

## Owner, diagnostics and sinks

Actual-owner execution remains the same SyntheticOwner.observe path, synthetic RSA
HALT and numeric-loopback witness/peer. No runtime Java source, seccomp policy or
module dependency changes. java.base restriction is measured separately from OS
denials. The owner image contains runtime classes and test probes only, not JDBC,
Testcontainers, trading application, credentials or a production launcher.

The sink inventory comprises Java stdout/stderr, the Docker json-file driver,
Docker CLI capture files, test result XML/text, dumpstreams, fixture inspect output,
bounded private scratch and the witness-only disposable volume. There is no configured
external forwarding in these fixtures. Host daemon collectors, privileged access,
external collectors outside the inspected setup and physical tmpfs swap retention are
not certified. A synthetic marker reaching an enumerated local sink does not prove
every future sink is known.

The positive heap dump intentionally contains a fake canary inside bounded tmpfs.
It is scanned in-container and never exported; only size/hash/presence metadata leaves.
Root destinations are denied. Native crash/error artifacts and attachment denial
remain separate diagnostic JVM tests, not evidence that an arbitrary owner crash
can never expose memory. Container removal disposes scratch without claiming physical
secure erase. Native reflection openings are not added to the owner process.

## Monotonic authority and retained limits

The owner, local journal/ledger, independent witness and retained coordinator root
remain distinct test boundaries. A surviving witness/root rejects restoration of
the local stores. Losing an acknowledgment consumes the attempt without retry.
Restoring all stores and checkpoints can still grant twice in the permanent no-HTTP
negative control. A signed restorable checkpoint is not a hardware monotonic root.
Physical power loss, privileged all-root rollback and multi-host consensus are unproven.

V2 approval, governance second review and four technical reviews remain PENDING.
Real access and implementation under V2 are NOT_AUTHORIZED; operational readiness
NO_GO and collateral-assisted MIS NOT_READY. See the validation and manifest for
fresh results, exact inventory, failure history and bounded assurance classifications.
