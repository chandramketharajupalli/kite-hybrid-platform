# Phase 13.18 attributed component prices and India cost model

[Source register](phase-13.18-candidate-shortlist.md) gives exact URLs, versions,
currencies and bounded UTC retrieval interval. Prices below are public catalog
observations, not accepted quotes, stock reservations or total-cost commitments.
All quote expiry dates are NOT_STATED; revalidation is mandatory before purchase.
No sourced FX rate was obtained: no foreign amount is converted to INR.

| Candidate | Observed component | Monthly component | Twelve-month component | Complete setup / monthly /12-month INR total |
|---|---|---|---|---|
| A1 Meerkat meer10 | USD999 starting system, not32GB configured quote (S01-S03) | TBD | TBD | TBD / TBD / TBD |
| A2 Slimbook ONE H255 | EUR832 base16GB/500GB + EUR24032GB RAM option = EUR1072 catalog component sum (S04-S06) | TBD | TBD | TBD / TBD / TBD |
| B1 DO Basic Regular BLR1 proposed | Setup TBD; bundled16GiB/8vCPU/320GiB (S07-S10) | USD96 compute bundle | USD96*12=USD1152 bundle only | TBD / TBD / TBD |
| B2 Lightsail Mumbai proposed | Setup TBD; Linux/Unix publicIPv4 16GB/4vCPU/320GB (S11-S12) | USD84 compute bundle | USD84*12=USD1008 bundle only | TBD / TBD / TBD |
| C0 HP upgrade, unqualified | Exact RAM kit/license/install quote absent | TBD | TBD | TBD / TBD / TBD |

A2 catalog display includes21% EU tax. Vendor says non-EU orders do not incur its
own tax but may incur customs; no India checkout or tax-adjusted offer was obtained.
EUR1072 is not an India landed quote and no tax subtraction is silently applied.
Assembly2-5days is not delivery assurance. India freight, customs, warranty and
returns remain TBD. A1 India shipping is not established; international warranty
freight is not covered by published terms and selected warranty terms remain TBD.

B1 published backup alternatives: weekly20%*96=USD19.20/month, daily30%*96=
USD28.80/month; do not add both. Snapshot rateUSD0.06 per GB-month, quantity TBD.
Vendor India billing guidance lists18% GST: if applicable to this bundle and account,
96*1.18=USD113.28/month, an illustrative component only. Account-specific treatment
is unverified. Billing currency USD; no FX fee/rate assumption. Exact BLR1 size
availability and quota remain unknown.6000GiB listed transfer is not free unlimited
egress; destination/rate and overage quantity must be priced.

B2 published snapshot rateUSD0.05/GB-month and additional block storageUSD0.10 per
allocated GB-month. Included320GB must not be charged again as extra storage.
Mumbai receives half the headline6TB transfer allowance, hence3TB; exact overage
rate, billable-direction rules, tax/account treatment and quantities remain TBD.
PublicIPv4 bundle is the reviewed subject; no cheaper IPv6 substitution. No free-tier
or promotion is included in the model. Four vCPUs are not a measured dedicated-core
or sustained-performance guarantee.

## Reproducible formulas and unknown propagation

Hardware setup = exact configured purchase + shipping + applicable import/tax +
installation/license + initial backup/witness equipment. Hardware monthly =
electricity + network allocation + backup/log retention + support/replacement reserve
+ separately administered witness. Electricity = measured average kW * scheduled
hours/month * applicable tariff; all three inputs TBD.12-month total = setup +
12*monthly + nonmonthly charges. Unknown mandatory term makes total TBD, not zero.

Cloud monthly = bundle + selected backup + snapshotGBmonths*rate + extra allocated
diskGBmonths*rate + billable egress*regional rate + logging/retention + access/network
services + support + independently administered witness + applicable taxes/FX fees.
Cloud12-month total = setup +12*monthly + nonmonthly charges. Usage/billing months
must be specified; a monthly bundle multiplication is not a prediction of uptime or
traffic. Mandatory unknowns remain null in the machine-readable evidence.

For every candidate: network, backups, retention, witness custody, maintenance labor,
electricity where applicable, tax treatment, support and decommission costs need
explicit included/quoted/not-applicable evidence. No unknown is deemed zero. Storage
quantity must include rollback space and reserve, not just60GiB workload. Historical
Phase13.16 reserve model is not fresh free-space evidence. No 12-month affordability
ranking or spending cap can be justified; approval packet cap remains TBD.
