# Phase 13.20 landed and cloud cost model

**Full12-month INR totals: TBD for all five candidates.** Mandatory unknowns are not zero. Reference FX permits indicative components, not bounded full estimates or spending caps.

[RBI](https://www.rbi.org.in/) credits FBIL: INR96.6149 perUSD1 and INR108.4728 perEUR1; rate date2026-10-09, publisher1.00pm with timezone NOT_STATED (Q17). Retrieval bounds are in the [ledger](phase-13.20-quote-evidence-ledger.md). Reference observations are not bank/card executable quotes. Settlement date, spread and fees remain unknown. No guessed FX or tax deduction.

Decimal calculation: original currency amount x dated reference rate, rounded once to paise using ROUND_HALF_UP. Annual = unrounded foreign monthly amount x12 before conversion, not rounded INR monthly x12. Twelve unchanged full monthly caps are a modeling assumption, not a renewal guarantee.

| Candidate/component | Original expression | Currency/amount | Period | Indicative INR component |
|---|---|---|---|---|
| A1 Base display only | 999 | USD 999 | one time base | 96518.29 |
| A2 Configuration catalog sum | 832+240 | EUR 1072 | one time not landed | 116282.84 |
| B1 Compute monthly cap | 96 | USD 96 | one full month | 9275.03 |
| B1 Compute twelve caps | 96*12 | USD 1152 | 12fullmonths | 111300.36 |
| B1 Weekly backup alternative | 96*0.20 | USD 19.20 | one month optional | 1855.01 |
| B1 Daily backup alternative | 96*0.30 | USD 28.80 | one month optional | 2782.51 |
| B1 Compute plus weekly backup scenario | 96*1.20*12 | USD 1382.40 | 12months partial services | 133560.44 |
| B1 Compute plus daily backup scenario | 96*1.30*12 | USD 1497.60 | 12months partial services | 144690.47 |
| B1 Illustrative compute with vendorGST | 96*1.18 | USD 113.28 | one month if applicable | 10944.54 |
| B2 Bundle monthly | 84 | USD 84 | one full month | 8115.65 |
| B2 Bundle twelve months | 84*12 | USD 1008 | 12fullmonths | 97387.82 |


System76 USD999 is not the selected32GB/500GB price. Slimbook EUR1072 is catalog832+240, not confirmed basket; displayed21%EU tax is not India tax. Do not divide by1.21 or assume refund. DO per-second billing from2026-01-01 has minimum60seconds orUSD0.01; rounded0.14286/hour is not an exact invoice algorithm. Standard Lightsail hourly billing caps at bundle price; stopped instances billed until deletion. No Research-product discount/free-tier assumption.

## Full-cost formulas and required inputs

Hardware setup = exact configured price + freight + handling/insurance + customs/duty/GST + settlement fees + plug/support/installation. Full12-month cost adds12 x electricity/network/backup/logging/witness/admin plus replacement and decommission/retention exposure. Rates, consumption, quantities and repair freight unknown; no fabricated tariff/failure-rate assumption.

Cloud = setup/security/access +12 x (compute + selected backups/snapshots + additional storage + IP + logging/monitoring + egress + witness/admin) + variable retention/deletion + applicable tax and settlement fees. Included disk is not billed twice. DO weekly20%/daily30% backups are alternatives; snapshot USD0.06/GB-month. AWS snapshots USD0.05/GB-month, added disk USD0.10/allocatedGB-month. All selected quantities unknown. DO transfer6000GiB; Mumbai Lightsail half headline6TB=3TB, both directions count allowance and excess outbound billed regionally. No assumed free ancillary costs.

| Candidate | Setup INR | Monthly INR | Full12-month INR | Reason |
|---|---|---|---|---|
| A1 | TBD | TBD | TBD | LANDED_COST_TBD |
| A2 | TBD | TBD | TBD | LANDED_COST_TBD |
| B1 | TBD | TBD | TBD | FULL_ACCOUNT_COST_TBD |
| B2 | TBD | TBD | TBD | FULL_ACCOUNT_COST_TBD |
| C0 | TBD | TBD | TBD | NO_VERIFIED_CANDIDATE |


No grounded complete low/base/high scenarios. DO optional-backup component scenarios deliberately exclude unknown services, usage, tax and custody. HP exactSKU/topology/manufacturer-supported kit/price/warranty absent: NO_VERIFIED_CANDIDATE. Historical7.65GiB host total cannot support8GiB guest; no new host measurement.

[Terms](phase-13.20-warranty-and-terms-conflicts.md) and [custody/security](phase-13.20-security-custody-matrix.md) remain blockers. V2 contract, governance second review and four technical reviews PENDING. Operational NO_GO; MIS NOT_READY; Phase12 BLOCK; H1/H2 FROZEN; July TEST SEALED; INR10,000 ceiling unchanged; StageB ZERO_CALL_ABORT preserved. Acquisition, provisioning and real access NOT_AUTHORIZED.
