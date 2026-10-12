# Phase 14.14 host comparison

NO_PRODUCTION_HOST_SELECTED. No candidate has passed all twelve gates.
Historical basis: [Phase13.18 shortlist](phase-13.18-candidate-shortlist.md),
[Phase13.20 costs](phase-13.20-landed-and-cloud-cost-model.md),
[Phase14.13 decision](phase-14.13-host-and-authority-decision.md).
Fresh public observations and cost limitations: [cost ledger](phase-14.14-cost-and-custody.md).

| ID | Concrete candidate | Capacity/platform evidence | Classification | Missing acceptance evidence |
|---|---|---|---|---|
| A | Current Windows HP 14s-dq2xxx family / board87FD | Historical 7.65GiB RAM,0.76-0.85GiB free; failed proposed8GiB guest headroom screen then | INSUFFICIENT_EVIDENCE | Current capacity, exact SKU, measured Linux isolation and independent authority |
| B | Upgraded existing Windows with isolated Linux VM | Proposed32GiB host and8GiB guest; compatibility and hypervisor/license not verified | INSUFFICIENT_EVIDENCE | Manufacturer-supported upgrade, quote, supported OS licensing, guest/host restore separation |
| C1 | System76 Meerkat meer10, Core3 100U, proposed32GB DDR5/500GB PCIe4 NVMe, Ubuntu24.04 | Retained catalog configuration; current quote and India support not established | CONDITIONALLY_FEASIBLE | Exact vendor configuration, landed quote, patch policy, enforcing LSM/userns and measured egress |
| C2 | Slimbook ONE Ryzen7 H255, proposed32GB DDR5/500GB NVMe, Ubuntu Server24.04 | Retained configuration, not a certified current order | CONDITIONALLY_FEASIBLE | Current availability, Linux support, India shipping/warranty and measured security |
| D1 | DigitalOcean BLR1 Bangalore, Basic Regular16GiB/8shared vCPU/320GiB SSD, proposed Ubuntu24.04 | Current public price; exact regional size/account quota unverified | CONDITIONALLY_FEASIBLE | Account eligibility, region stock, image provenance, measured kernel/LSM/userns, IAM/backup/log custody |
| D2 | AWS Lightsail ap-south-1 Mumbai, Linux/Unix publicIPv4 GeneralPurpose16GB/4vCPU/320GB SSD | Current public price; exact account/bundle availability unverified | CONDITIONALLY_FEASIBLE | Entitlement, image/patch evidence, security enforcement, egress and independent authority |

CONDITIONALLY_FEASIBLE means plausible design, not qualified, purchased, selected or deployed.
For B, 8GiB guest+2GiB virtualization overhead+4GiB host margin is only a14GiB available-memory
planning screen; no current measurement or performance promise. No new host probe occurred.
All C/D candidates require an independently administered witness, separate recovery authority,
owner-held session custody and external logging. Linux vendor support does not certify the
Gate A runtime. Windows host control over a Linux guest defeats claimed independent custody
when the same administrator can restore both guest and witness.
