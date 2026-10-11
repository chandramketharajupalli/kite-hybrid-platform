# Phase 13.17 cost and residual-risk model

All totals are TBD, not zero. No quote, account, tariff, tax status, exchange rate,
hardware model or approved spending cap was supplied. Access date2026-10-11 UTC;
see [source register](phase-13.17-option-comparison.md). Local option cost currency
is proposed INR (India); remote candidate is ap-south-1 (Mumbai), currency/rate TBD
pending exact billable SKU and payer terms. No currency conversion is performed.
P07 pricing guidance excludes applicable taxes; no numeric regional SKU rate was
established. Free tiers, discounts, commitments and credits are not assumed.

| Cost/effort | A dedicated Linux | B remote Linux VM | C upgraded Windows + VM |
|---|---|---|---|
| Setup amount | TBD machine/storage/UPS/installation/transport/tax | TBD security setup, image preparation, access design | TBD compatible RAM/labor/edition/hypervisor/storage/licenses |
| Monthly amount | TBD electricity, support, storage replacement provision, witness, labor | TBD compute hours, EBS/IOPS/throughput, snapshots, egress/IP, logs/audit, keys/support, witness, labor | TBD incremental electricity/support/license amortization, witness, labor |
| 12-month total | TBD | TBD | TBD |
| Admin effort | Proposed hardware/physical custody, patches and recovery; hours TBD | Proposed IAM/MFA, billing/quota, guest and provider audit; hours TBD | Proposed compatibility/coexistence, guest plus Windows patching; hours TBD |
| Residency/exposure | Site/address/custodian TBD; physical and backup access | Proposed Mumbai compute only; logs/backups/support/control-plane residency NOT_VERIFIED | Existing workstation exposure plus guest/snapshot/backup custodians |
| Migration effort | Sanitized source/artifact-only rebuild; duration TBD | Same plus approved remote transfer/access/evidence export | Same plus measured contention and upgrade validation |

Model (planning formulas, no fabricated inputs):

- Local monthly electricity = measured mean kW * authorized operating hours *
  applicable currency/kWh tariff. Add cooling/UPS losses only when measured.
- B monthly = compute hourly rate * actual billable hours + persistent storage +
  provisioned performance + backup + outbound transfer + approved network/access
  services + logs/audit + keys/support + independently administered witness + labor.
  Stopping compute does not establish zero storage/backup/other charges.
- For each option,12-month total = setup + sum(month1..month12 recurring costs) +
  one-time replacement/decommission cost + applicable taxes. No double-counted setup
  amortization; unknown input propagates UNKNOWN/TBD. Currency must match before sum.
- No cost ranking or claimed savings until like-for-like quotes include security,
  witness and retention costs. Authorized spending is NONE; this is not a zero-cost offer.

Current-host arithmetic is historical and separate from price:14GiB available RAM
screen=8guest+2overhead+4host reserve; D screen=60+60+20+40=180GiB, yielding
257.878807-180=77.878807GiB historical surplus. C reserve20GiB yields42.444843GiB.
Available RAM0.762306-0.849621GiB fails.16GiB nominal minus14GiB leaves only2GiB
for already-running host demand in this simplified screen; capacity is not proven.
32GiB is preferable for investigation, still subject to hardware fit and measurement.

| Risk / severity (engineering assessment) | Option exposure / evidence | Required closure and accountable function |
|---|---|---|
| Insufficient capacity / high | A hardware U; B quota/headroom U; C E16 RAM negative, upgrade U | Operator/platform custodian: measured sustained headroom, exact compatibility and cost |
| Unauthenticated image / critical | A/B/C OCI_SIGNATURE_NOT_VERIFIED | Security/authentication: exact-subject authorized chain; no waiver by digest |
| LSM/userns ineffective / critical | A/B/C proposed only | Packaging/logging + runtime: actual Java audit/remapping negatives, restart persistence |
| Shared restore authority / critical | A/B/C custodian U; historical all-root counterexample | Runtime/DB + separate root custodian: explicit survival and recovery evidence; universal claim excluded |
| Dump/collector exposure / critical | Local physical/Windows or remote cloud sinks U | Packaging/logging + sink owners: full destination/access/retention/canary evidence |
| Remote account/network takeover / critical | B extra provider/IAM/metadata/management boundary | Operator/network custodian: MFA/least privilege, audited access, explicit guest + network denies |
| Physical/admin tampering / high | A physical; C shared workstation; B provider privileged scope | Host/account owner: documented threat limits, custody/audit, independent evidence retention |
| Cost and lock-in / high | All amounts U; B recurring/egress, C upgrade compatibility | Operator: approved itemized quote, finite spend/time caps, exit/retention costs |
| Mistaking assessment for consent / critical | All four reviews and governance pending | Four domain roles: attributable scoped decisions; no real access implied |

Likelihood is UNKNOWN; no invented probabilities. No risk is accepted for the user.
Design discussion can proceed; mandatory unresolved safety properties block testbed
security acceptance, and provisioning is not authorized. Preferred A/fallback B
remain conditional, final selection SELECTION_PENDING_EVIDENCE.
