# Phase 13.19 verified-source quote ledger

PROCUREMENT_EVIDENCE_INCOMPLETE. The title denotes source-attributed observations,
not verified procurement quotes. Exactly five inherited candidates; no substitutions.
All fresh public reads lie between2026-10-11T18:01:13+00:00 and
2026-10-11T18:02:20+00:00 (local-clock bounds, not exact per-page or publisher times).
20 unique primary pages;6 search queries;20 explicit opens/clicks;2 redirects
(shipping/warranty trailing slash). No authenticated quote/checkout/stock evidence.
No cart actions, vendor contact or artifact downloads. Static cart text is not action.

VERIFIED requires exact candidate-specific evidence; CATALOG_ONLY is published
text, not delivered hardware or account eligibility. UNKNOWN is missing proof;
CONFLICTING retains inconsistent sources. No candidate has a VERIFIED quote.
Vendor GB and GiB are preserved; user shorthand does not change disk capacity.

## Hardware fields

| Candidate | Field | State | Attributable finding and limit | Sources |
|---|---|---|---|---|
| A1 | CPU_RAM_storage | CATALOG_ONLY | meer10 Core3 100U,2x16GB DDR5,500GB PCIe4 NVMe proposed | P01 |
| A1 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | P01,P02 |
| A1 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | P01,P02 |
| A1 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | P01,P02 |
| A1 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | P01,P02 |
| A1 | Linux_support | CATALOG_ONLY | Ubuntu24.04 listed;Server/kernel not certified | P01 |
| A1 | power_India_voltage | CATALOG_ONLY | 19V90W adapter;worldwide-grid claim;actual input label/plug unverified | P01,P02 |
| A1 | shipping_India | UNKNOWN | India absent from public list;do not assume delivery | P02 |
| A1 | warranty_RMA | CATALOG_ONLY | 1/2/3year choices;international repair freight customer;exact purchased term absent | P05 |
| A1 | customs_GST_responsibility | CATALOG_ONLY | Vendor does not collect international import taxes;customer liability,amountsTBD | P02 |
| A1 | lead_time | CATALOG_ONLY | Typical10business-day build;not India arrival commitment | P02 |
| A1 | return_conditions | CATALOG_ONLY | 30days from shipment;RMA/condition;original freight excluded,return cost customer | P05 |
| A2 | CPU_RAM_storage | CATALOG_ONLY | ONE H255,2x16GB DDR5,500GB NVMe4.0 proposed | P03 |
| A2 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | P03,P06,P09 |
| A2 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | P03,P06,P09 |
| A2 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | P03,P06,P09 |
| A2 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | P03,P06,P09 |
| A2 | Linux_support | CATALOG_ONLY | Linux options;exactServer24.04 build not certified | P03,P04 |
| A2 | power_India_voltage | CATALOG_ONLY | 100-240V50-60Hz input,19V6.32A120W;actual India cord/earthing/label unverified | P04 |
| A2 | shipping_India | CATALOG_ONLY | India appears in coverage list;freight/address eligibility not quoted | P06 |
| A2 | warranty_RMA | CONFLICTING | FAQ1year international;FAQ return freight vendor versus Questions both legs customer;exact product/India applicability unresolved | P09,P10 |
| A2 | customs_GST_responsibility | CATALOG_ONLY | Non-EU local customs/tax may be collected;21%display not India landed tax;amountTBD | P03,P09 |
| A2 | lead_time | CATALOG_ONLY | 2-5dayassembly;shipping/customs/stock indicative | P03,P09 |
| A2 | return_conditions | CATALOG_ONLY | 15dayinitiation,authorized return received30days;voluntary shipping/fees excluded;India applicability unknown | P11 |
| C0 | CPU_RAM_storage | UNKNOWN | HP14s-dq2xxx family/board87FD;exactSKU/kit absent | E18 |
| C0 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | E18 |
| C0 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | E18 |
| C0 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | E18 |
| C0 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | E18 |
| C0 | Linux_support | UNKNOWN | Upgrade/hypervisor/license unverified | E18 |
| C0 | power_India_voltage | UNKNOWN | No new adapter inspection | E18 |
| C0 | shipping_India | UNKNOWN | No compatible kit or seller quote | E18 |
| C0 | warranty_RMA | UNKNOWN | Exact upgrade warranty effects unknown | E18 |
| C0 | customs_GST_responsibility | UNKNOWN | Local kit invoice/tax unknown | E18 |
| C0 | lead_time | UNKNOWN | TBD | E18 |
| C0 | return_conditions | UNKNOWN | TBD | E18 |

## Cloud fields

| Candidate | Field | State | Attributable finding and limit | Sources |
|---|---|---|---|---|
| B1 | bundle | CATALOG_ONLY | Basic Regular16GiB/8vCPU/320GiB,USD96monthly | P07 |
| B1 | region_eligibility | UNKNOWN | BLR1 catalog,unreadable exactsize cells | P13 |
| B1 | tenancy_architecture | UNKNOWN | Shared CPU historically documented;actual host/architecture/tenancy unmeasured | E18 |
| B1 | snapshots | CATALOG_ONLY | USD0.06/GBmonth;quantityTBD | P07 |
| B1 | backup | CATALOG_ONLY | Weekly20%/daily30%alternatives;selectionTBD | P07 |
| B1 | extra_storage | UNKNOWN | Included320GiB not billed again;extra quantity/rateTBD | P07 |
| B1 | egress | UNKNOWN | 6000GiB listedtransfer;actual billable usage/rateTBD | P07 |
| B1 | IPv4 | UNKNOWN | Exact selected IP product/separate fees not established | P07 |
| B1 | monitoring_logging | UNKNOWN | Sink selection/retention/export/chargesTBD | E18 |
| B1 | tax_FX | UNKNOWN | Vendor18%GST andUSD invoices;account treatment andFX unknown | P14,P15 |
| B1 | shutdown_cost | CATALOG_ONLY | Poweroff stillbilled;destroy required toend instance charge | P20 |
| B1 | persistent_deletion | CATALOG_ONLY | Associated resources notdeleted bydefault;backups expire4weeks fromcreation | P12 |
| B1 | provider_access | UNKNOWN | Providerphysical/operatoraccess andpurge attestation notestablished;guest control notprovider exclusion | P12 |
| B1 | account_organization_custody | UNKNOWN | No account orrights evidenced;organization/rootMFA/restoreauthority TBD | E18 |
| B1 | egress_enforcement | UNKNOWN | Publicfirewall catalog isnotactual Java default-deny proof | E15 |
| B1 | billing_quote | UNKNOWN | No account-specific quote orconfirmedcheckout | P07 |
| B2 | bundle | CATALOG_ONLY | Linux/Unix publicIPv4 GeneralPurpose16GB/4vCPU/320GB,USD84monthly | P08 |
| B2 | region_eligibility | UNKNOWN | Mumbai region catalog only;account/bundle quota unknown | P19 |
| B2 | tenancy_architecture | UNKNOWN | No dedicated tenancy or architecture established | E18 |
| B2 | snapshots | CATALOG_ONLY | USD0.05/GBmonth;quantityTBD | P08 |
| B2 | backup | CATALOG_ONLY | Snapshot-based retention selection/quantityTBD | P08,P17 |
| B2 | extra_storage | UNKNOWN | Included320GB not billed again;extra0.10USD/allocatedGBmonth,quantityTBD | P08 |
| B2 | egress | UNKNOWN | Mumbai3TB listed allowance;usage/direction/regional overageTBD | P08 |
| B2 | IPv4 | UNKNOWN | PublicIPv4 selected bundle;separate static-IP residual billing possible | P08,P17 |
| B2 | monitoring_logging | UNKNOWN | Sink selection/retention/export/chargesTBD | E18 |
| B2 | tax_FX | UNKNOWN | Account tax andFX unknown | P15 |
| B2 | shutdown_cost | CATALOG_ONLY | Stopped standard instance billed untildelete;noResearchdiscount | P16 |
| B2 | persistent_deletion | CATALOG_ONLY | Automatic snapshots deleted unlesskept;manualsnapshots/disks/IP/LB mayremainbillable | P17 |
| B2 | provider_access | UNKNOWN | Providerphysical/operatoraccess andpurge attestation notestablished;guest control notprovider exclusion | P18 |
| B2 | account_organization_custody | UNKNOWN | No account orrights evidenced;organization/rootMFA/restoreauthority TBD | E18 |
| B2 | egress_enforcement | UNKNOWN | Publicfirewall catalog isnotactual Java default-deny proof | E15 |
| B2 | billing_quote | UNKNOWN | No account-specific quote orconfirmedcheckout | P08 |

A1=System76 Meerkat meer10; A2=Slimbook ONE H255; B1=DigitalOcean Basic Regular,
proposedBLR1; B2=AWS Lightsail standard General Purpose, proposedMumbai;
C0=HP14s-dq2xxx/87FD upgrade NO_VERIFIED_CANDIDATE. Phase13.18 local model/memory
observations remain historical. Firmware32GiB maximum does not validate a kit.
C0 as-is cannot host the8GiB guest under historical RAM measurements. No new scan.

Slimbook warranty conflict is unresolved, not a claim that either page is fraudulent:
FAQ may differ in product/scope from laptop-oriented Questions. Obtain exact model,
India purchaser type and governing terms with an attributable answer only after
separately authorized contact. No guessed precedence or cost allowance.

## Primary-source register

Each row has evidence type PUBLIC_CATALOG_OR_DOCUMENTATION, unless retrieval
incomplete. Effective date and quote expiry NOT_STATED except P14's vendor guidance
as of2017-07-01/last-verified2026-06-22; current-rate effective date not independently
established. Observation interval above applies to each source. Historical E18 means
[Phase13.18](phase-13.18-validation.md); E15 is [gate source](phase-13.15-security-acceptance-matrix.md).
Tax statements are vendor billing descriptions; actual purchaser treatment unresolved.

| ID / publisher | Exact URL | Subject / region / currency | Observation |
|---|---|---|---|
| P01 / System76 | [source](https://system76.com/mini/meerkat) | meer10 Core3 100U /32GB(2x16)/500GB proposed; US/USD | 999 display is not selected configuration quote; Ubuntu24.04 and options listed;19V90W adapter; stock unconfirmed |
| P02 / System76 | [source](https://system76.com/shipping/) | International shipping; India eligibility/USD | India absent from country list; worldwide-grid claim, plug adapter may be needed; exact freight at checkout; customer import charges; typical10business-day build, not India promise |
| P03 / Slimbook | [source](https://slimbook.com/shop/product/one-amd-ryzen-7-h-255-1956?category=14) | ONE H255/2x16GB/500GB; Spain/EUR;India tax unresolved | 832 base plus240 RAM option;500GB option;21%display tax;non-EU customs possible;2-5day assembly, not delivery |
| P04 / Slimbook | [source](https://slimbook.com/en/one) | ONE H255; Global/EUR quote absent | 8cores16threads;Linux choices;100-240V50-60Hz input/19V6.32A120W;actual India plug/safety unverified |
| P05 / System76 | [source](https://system76.com/warranty/) | Hardware warranty/returns; International/USD amounts unquoted | 1/2/3year options;international repair freight customer;30days from shipment return, original freight excluded;RMA and condition requirements |
| P06 / Slimbook | [source](https://slimbook.com/shippinginfo) | Shipping coverage; India/EUR freight unquoted | India explicitly listed;destination-specific charges and commitment unknown |
| P07 / DigitalOcean | [source](https://www.digitalocean.com/pricing/droplets) | Basic Regular16GiB/8vCPU/320GiB; ProposedBLR1/USD | 96monthly;6000GiBtransfer;weekly20%/daily30% backup alternatives;snapshot0.06USD/GBmonth;not account quote |
| P08 / AWS | [source](https://aws.amazon.com/lightsail/pricing/) | Linux/Unix publicIPv4 General Purpose16GB/4vCPU/320GB; ProposedMumbai/USD | 84monthly;Mumbai half headline6TB=3TB;snapshots0.05USD/GBmonth;extra disk0.10USD/allocatedGBmonth;tax unknown |
| P09 / Slimbook | [source](https://slimbook.com/en/faq) | General FAQ; International/EUR fees unknown | International warranty1year listed;non-EU inward freight customer and repaired return vendor;stock indicative;applicability to exact India order unknown |
| P10 / Slimbook | [source](https://slimbook.com/en/questions) | General questions, laptop-oriented; OutsideEurope/currency N/A | Says customer pays both repair shipments outsideEurope;conflicts with FAQ;scope/precedence unresolved |
| P11 / Slimbook | [source](https://slimbook.com/en/legal/advice) | Published terms; International/EUR fees unknown | 15day return initiation from delivery-note date;authorized return received within30days;voluntary shipping/fees not refunded;exact India applicability unknown |
| P12 / DigitalOcean | [source](https://docs.digitalocean.com/products/droplets/how-to/destroy/) | Droplet destruction; Global/currency N/A | Associated snapshots/volumes not destroyed by default;automated backups retained four weeks from creation;provider physical erasure not proven |
| P13 / DigitalOcean | [source](https://docs.digitalocean.com/platform/regional-availability/) | BLR1 catalog; Bangalore/currency N/A | BLR1 listed but extracted size availability cells blank;not account quota evidence |
| P14 / DigitalOcean | [source](https://docs.digitalocean.com/platform/billing/taxes/ind/) | India tax guidance; India/USD | 18%GST vendor guidance;USD invoices;actual tax location/GSTIN treatment unverified |
| P15 / FBIL | [source](https://www.fbil.org.in/) | Dated USD/EUR-INR reference search; India/INR | Page returned zero extracted lines;no authoritative dated FX value obtained;third-party search results not used |
| P16 / AWS | [source](https://docs.aws.amazon.com/en_en/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html) | General Lightsail billing; Global/USD | Stopped instances still billed until deleted;not Lightsail for Research discount;account-specific terms unverified |
| P17 / AWS | [source](https://docs.aws.amazon.com/lightsail/latest/userguide/delete-an-amazon-lightsail-instance.html) | Instance deletion; Global/USD residual charges | Automatic snapshots deleted unless retained;static IP/manual snapshots/disks/load balancers can remain and incur charges;not physical erasure proof |
| P18 / AWS | [source](https://docs.aws.amazon.com/en_en/lightsail/latest/userguide/security.html) | Shared responsibility; Global/currency N/A | Provider protects infrastructure;customer retains service/data security obligations;not evidence of absent provider access |
| P19 / AWS | [source](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-regions-and-availability-zones-in-amazon-lightsail.html) | Lightsail region catalog; Mumbai ap-south-1/currency N/A | Mumbai listed;exact blueprint/bundle eligibility,quota and account approval absent |
| P20 / DigitalOcean | [source](https://docs.digitalocean.com/reference/doctl/reference/compute/droplet-action/power-off/) | Power-off billing documentation; Global/USD | Powered-off Droplets remain billable;read-only documentation, no command executed |

FBIL returned no extracted rate data; no dated USD/EUR-INR value was authenticated.
Do not use third-party snippets or yesterday's assumed exchange rate. DO availability
cells remain unreadable; account entitlement is not public-catalog evidence.
Published cancellation/deletion procedures do not prove forensic media erasure,
backup purge or closure of every billable resource. Actual cloud organization,
provider access, legal/contractual applicability and deletion receipts remain absent.
