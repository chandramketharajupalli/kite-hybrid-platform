# Phase 13.19 reproducible 12-month INR cost model

All complete totals TBD. [Quote ledger](phase-13.19-quote-ledger.md) contains fresh
P01-P20 provenance; previous Phase13.18 components remain historical. No binding
quote, checkout, reservation, account invoice or FX rate was obtained. Prices were
observed2026-10-11T18:01:13+00:00..18:02:20+00:00. Effective date/expiry unprovided;
revalidate before any later authorization. No free-tier, promotional credit or
assumed tax exemption is applied.

| Candidate | Fresh catalog component, original currency | Bundle/component arithmetic only | Setup INR | Monthly INR |12-month INR |
|---|---|---|---|---|---|
| A1 | USD999 display;32GB selected config unquoted (P01) | No configured sum possible | TBD | TBD | TBD |
| A2 | EUR832 base + EUR24032GB option (P03) | EUR1072 catalog sum;not India landed quote | TBD | TBD | TBD |
| B1 | USD96/month bundle (P07) | USD1152 for12full monthly bundles | TBD | TBD | TBD |
| B2 | USD84/month bundle (P08) | USD1008 for12full monthly bundles | TBD | TBD | TBD |
| C0 | Exact compatible kit/license unquoted (E18) | NO_VERIFIED_CANDIDATE | TBD | TBD | TBD |

A2 display includes21% EU tax; no automatic subtraction or India-tax conclusion.
P03/P09 describe possible local customs/tax collection. An attributable configured
India offer must establish tax basis, freight, import responsibility and warranty
costs. A1 starting price does not price the proposed RAM. Power-adapter wattage
is not measured average consumption and cannot supply an electricity budget.

B1 optional weekly backup=96*0.20=USD19.20/month or daily=96*0.30=USD28.80/month;
not both. Snapshot quantity S GB-month at USD0.06*S. P14 vendor18% GST gives an
illustrative bundle-only96*1.18=USD113.28 if applicable, not an account tax verdict.
B2 snapshot quantity S at USD0.05*S; extra allocated disk V at USD0.10*V. Included
320GB/GiB is not charged twice. Mumbai transfer allowance6TB/2=3TB per P08; exact
billable usage/direction/overage and any selected IP services still need pricing.

## Inputs and formulas

Let fxUSD and fxEUR be dated attributable INR per foreign unit, with transaction
spread/fees separately specified. FBIL public retrieval P15 supplied no usable
rate; both are null. No foreign subtotal is presented as a verified INR amount.

Hardware one-time H = configured machine/kit + freight + duties/taxes + installation/
license + initial backup/witness/storage. Monthly R = measured energy(kW*hours*tariff)
+ network allocation + backup/log retention + support/replacement provision +
independently administered witness.12monthINR = INR(H) + sum(month1..12 INR(Rm))
+ separately timed expenses. All physical maintenance/replacement inputs TBD.

Cloud one-time C = setup/access/security work + initial witness/backup setup.
Monthly Rm = bundle + chosen backup + snapshotGBmonths*rate + additionaldiskGBmonths*
rate + billableegress*regionalrate + logging/monitoring + IP/network services +
support + witness + applicabletax + FX/transactionfees.12monthINR = INR(C) +
sum(INR(Rm)) + decommission/retention expenses. Account selection/usage/fees unknown.
No flat12 multiplication of a subtotal is called a complete TCO.

Unknown mandatory component yields null/TBD; zero requires explicit no-cost/included
or not-applicable evidence. Setup/recurring/variable/unknown terms are distinct in
the manifest. No low/base/high scenario can be supported because mandatory inputs
are absent. Spending cap null, not a bundle multiple. Amounts use Decimal arithmetic.

## Ownership and cancellation exposure

A1/A2 need shipping/RMA/downtime/replacement/power/network/physical custody and
secure media disposal budgets. C0 additionally needs exact kit, labor, edition/
hypervisor and measured headroom, without assuming its upgrade is possible.
B1/B2 need account/IAM/provider administration, recurring charges, backup/collector
retention and separate witness authority. Administrative effort has no measured
hours or price here; no fabricated labor score or cheapest-host conclusion.

P20 and P16: stopping either ordinary cloud instance does not stop instance billing.
P12: DO associated resources are not removed by default and automatic backups persist
four weeks from creation. P17: Lightsail automatic snapshots are normally removed
unless retained, while other associated resources can remain billable. Final billed
inventory, retention expiry and authorized deletion receipts are prerequisites;
no destructive command is executed. Cancellation/renewal contract, provider erasure
and account-specific fees remain unknown. Physical deletion and zero bill are
separate proofs. All five procurement decisions remain incomplete.
