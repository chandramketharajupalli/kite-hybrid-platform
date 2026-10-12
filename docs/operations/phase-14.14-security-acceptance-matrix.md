# Phase 14.14 security acceptance matrix

U = UNKNOWN; D = DESIGN_FEASIBLE_NOT_TESTED. No cell is MEASURED_PASS or
MEASURED_FAIL for a current candidate: no host was provisioned or tested. Historical
container measurements are not candidate certification. NOT_APPLICABLE is not used
to waive required controls. Host IDs refer to [host comparison](phase-14.14-host-comparison.md).

## Twelve mandatory gates

| Control | A | B | C1 | C2 | D1 | D2 |
|---|---|---|---|---|---|---|
| Supported host capacity | U | U | U | U | U | U |
| OS/kernel support | U | D | D | D | D | D |
| Enforcing LSM | U | D | D | D | D | D |
| User namespace/privilege isolation | U | D | D | D | D | D |
| Verified network egress | U | U | U | U | U | U |
| Credential custody | U | U | U | U | U | U |
| Independent rollback authority | U | U | U | U | U | U |
| External logging custody | U | U | U | U | U | U |
| Recovery/fail-closed behavior | U | D | D | D | D | D |
| Cost/administrative feasibility | U | U | U | U | U | U |
| Source authenticity/patch policy | U | U | U | U | U | U |
| Owner/governance authorization | U | U | U | U | U | U |

## Linux control enforceability

| Control | A | B | C1 | C2 | D1 | D2 |
|---|---|---|---|---|---|---|
| Non-root identity | U | D | D | D | D | D |
| Capability removal | U | D | D | D | D | D |
| No-new-privileges | U | D | D | D | D | D |
| Seccomp | U | D | D | D | D | D |
| Enforcing AppArmor/SELinux | U | D | D | D | D | D |
| User-namespace remapping | U | D | D | D | D | D |
| Read-only filesystem | U | D | D | D | D | D |
| Bounded scratch | U | D | D | D | D | D |
| CPU limits | U | D | D | D | D | D |
| Memory limits | U | D | D | D | D | D |
| PID limits | U | D | D | D | D | D |
| Exact destination egress | U | U | U | U | U | U |
| DNS/proxy escape prevention | U | U | U | U | U | U |
| Credential isolation | U | U | U | U | U | U |
| External log containment | U | U | U | U | U | U |
| Secure deletion/retention | U | U | U | U | U | U |

D cells are engineering inferences from documented Linux mechanisms and retained bounded recovery tests, not measured enforcement. The official Ubuntu AppArmor and Docker namespace sources are recorded in [cost and custody](phase-14.14-cost-and-custody.md). All twelve gates require deployment-specific evidence before qualification.

PRODUCTION_EGRESS_NOT_VERIFIED; INDEPENDENT_ROLLBACK_AUTHORITY_NOT_ESTABLISHED; PHYSICAL_POWER_LOSS_NOT_VERIFIED. External sinks, credentials and independent administration remain unverified. Exact HTTPS method/path requires trusted application-layer enforcement in addition to destination isolation; no IP-only certification.
