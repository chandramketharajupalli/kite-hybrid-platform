# Disposable Java-owner policy fixture

`owner-seccomp.json` derives from Moby profiles commit
`2ceae35d351c156cb5a8efc0fdc4a08cf94569d8`, `seccomp/default.json`:
https://github.com/moby/profiles/blob/2ceae35d351c156cb5a8efc0fdc4a08cf94569d8/seccomp/default.json
The accompanying `MOBY-LICENSE` is the upstream Apache-2.0 license.

Changes: remove fork/vfork and every original clone allow rule; add an amd64 clone
rule that preserves the original namespace exclusion mask and requires CLONE_THREAD.
The default remains ERRNO/EPERM. clone3's existing non-SYS_ADMIN ENOSYS fallback
remains. Runtime containers drop ALL capabilities; this is not a privileged profile.
Other original syscall restrictions remain. No claim for other architectures.

Only test fixtures use this policy. It permits JVM threads but denies the measured
Java ProcessBuilder child-creation path. It is not a general proof against every
possible native-code process-creation strategy, privileged Docker control or kernel
exploits. Network isolation also requires the tested network-none namespace; this
seccomp profile alone is not an egress firewall. See the Phase 13.11 plan and report.
