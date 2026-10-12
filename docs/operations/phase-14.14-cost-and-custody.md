# Phase 14.14 cost and custody evidence

Public-source observation interval: 2026-10-12T15:25:09Z through 2026-10-12T15:28:11Z.
These are bounded tool observation times, not vendor publication times. No account login,
cart, billing quote or vendor contact. No current FX conversion. Historical Phase13.17-13.20
quotes remain historical; component prices are not complete India landed totals.

| Official source | Observation and permitted inference |
|---|---|
| https://www.digitalocean.com/pricing/droplets | Read: Basic Regular16GiB/8vCPU/320GiB,6000GiB transfer at USD96/month; weekly backup20%,daily30%. Monthly-cap planning, no account quote. |
| https://docs.digitalocean.com/platform/regional-availability/ | BLR1 column exists; rendered availability cells insufficient to verify exact size stock. |
| https://aws.amazon.com/lightsail/pricing/ | Read: Linux/Unix publicIPv4 GeneralPurpose16GB/4vCPU/320GB at USD84/month. Mumbai has half headline6TB allowance, hence3TB. Taxes/account eligibility not established. |
| https://system76.com/mini/meerkat | Initial page metadata available; price/config finds returned no matches; subsequent open returned tool InternalError, no HTTP status supplied. Current exact configuration price remains TBD. |
| https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html | Conditional put/update documented; useful atomic condition primitive, not proof of privileged anti-rollback. |
| https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/PointInTimeRecovery_Howitworks.html | PITR restores a new table. Preventing privileged endpoint/table substitution remains a separate design obligation. |
| https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/PointInTimeRecovery.html | Initial URL redirected to developer-guide root without target content; corrected URL above read successfully. Failure retained. |
| https://docs.docker.com/engine/security/userns-remap/ | Documents user namespace mechanism; daemon remains privileged. No candidate-host enforcement inferred. |
| https://ubuntu.com/server/docs/how-to/security/apparmor/ | Canonical redirect from documentation.ubuntu.com/server/how-to/security/apparmor/; enforce/status procedures documented, not executed. |

Historical hardware anchors: System76 startingUSD999 is not a32GB configured quote;
Slimbook EUR832 base+EUR240 RAM=EUR1072 catalog subtotal from Phase13.18-13.20, not a fresh
quote. Do not subtract historical21% EU VAT or assume India export exemptions. Storage,
shipping, import classification, GST/duty, insurance, currency fees and warranty all require
attributable quotes. Current availability and final configuration remain TBD.

| Component calculation, native USD | Monthly | Twelve months | Scope |
|---|---:|---:|---|
| D1 compute only |96.00|1152.00|Partial published component |
| D1 compute+weekly backup20% |115.20|1382.40|Partial alternative |
| D1 compute+daily backup30% |124.80|1497.60|Partial alternative, not added to weekly |
| D2 compute only |84.00|1008.00|Partial published component |

| Candidate | Setup | Complete monthly | Complete12month | Mandatory unknowns |
|---|---|---|---|---|
| A |TBD|TBD|TBD|Capacity remediation, power/network, witness, logs, backup, administration |
| B |TBD|TBD|TBD|Compatible RAM/hardware, license/hypervisor, setup plus recurring controls |
| C1 |TBD|TBD|TBD|Exact configuration, India freight/import/tax/support, power/network, authority/logs |
| C2 |TBD|TBD|TBD|Same landed/operational unknowns; no tax exemption assumed |
| D1 |TBD|TBD|TBD|Tax, setup, storage/transfer overage, logging, independent witness, backup custody/admin |
| D2 |TBD|TBD|TBD|Tax, setup, backup/storage/transfer, logging, independent witness/admin |

Hardware setup = exact configuration+freight+insurance+handling+import/tax+FX fees+installation.
Cloud recurring = compute+backup+extra storage/IP+transfer+logs+witness+administration.
Complete12month = setup+12*complete monthly+retention/deletion+applicable tax/fees.
Any mandatory unknown propagates TBD; zero traffic allowances do not prove zero billing.
Managed witness pricing, region, read/write volume, audit retention, backup and custodian
fees are unspecified: all five authority options have complete monthly/annual cost TBD.
No exchange-rate conversion, procurement approval or subscription is performed.

| Proposed role | Current evidence | Required acceptance |
|---|---|---|
| Project owner/operator | User-declared sole developer/operator; not independently authenticated | Attributable scoped decision |
| Kite session owner | AUTHENTICATED_SESSION_NOT_ESTABLISHED | Legitimate owner-held short-lived injection; no restoration/env/CLI/log serialization |
| Gate A host administrator | Proposed, permissions unverified | Cannot administer witness/root/restore policy |
| Witness administrator | No independent custodian established | Independent IAM and measured denial of host administrator access |
| Backup/restore administrator | Unassigned/unverified | Cannot roll back all roots; restoration ceremony and retained checkpoint |
| Signing-key custodian | Synthetic keys only | Restricted signer, rotation/revocation and auditable use |
| HALT/revocation authority | Production authority not established | Current scope-bound issuer and emergency revocation |
| External logging custodian | Unassigned/unverified | Separate write/read/delete policies, redaction, retention and incident handling |
| Emergency recovery reviewer | No independent reviewer established | No automatic rearm; attributable review and immutable recovery record |

Physical separation is not administrative independence. Self-review remains self-review.
No new person, account, key, session or independent approval is invented. Phase13.5K second
review, V2 and four technical reviews remain pending.
