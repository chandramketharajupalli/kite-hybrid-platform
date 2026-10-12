# Phase 13.20 public quote evidence ledger

PROCUREMENT_EVIDENCE_INCOMPLETE. No formal quotation, basket, checkout, vendor contact or stock reservation occurred. Public pages are not binding offers.

Fresh reads bounded by2026-10-11T18:22:25+00:00 and2026-10-11T18:23:20+00:00 from tool clock:20 unique official pages,22 explicit opens including revisits,19 finds,zero searches/observed redirects. Exact per-page receipts and publisher timezones are unavailable. These bounds do not authenticate publisher dates. No reseller evidence used.

[Parent ledger](phase-13.19-quote-ledger.md) remains historical. E19 references identify its immutable manifest evidence IDs; Q references below are fresh. Prices do not guarantee continuing availability. Hardware destination India is unconfirmed; cloud regions BLR1/Mumbai are proposed, not account eligibility. Tax/order applicability and price expiry are unknown unless specified. GB/GiB preserve vendor units.

| ID | Candidate / proposed configuration | Quote classification |
|---|---|---|
| A1 | System76 Meerkat meer10; Core3 100U,32GB(2x16)DDR5,500GB PCIe4 NVMe,Ubuntu24.04 proposed | LANDED_COST_TBD |
| A2 | Slimbook ONE AMD Ryzen7 H255; 32GB(2x16)DDR5,500GB NVMe4.0;Ubuntu Server24.04 intended,not factory-certified | LANDED_COST_TBD |
| B1 | DigitalOcean Basic Regular16GiB/8vCPU/320GiB; Proposed BLR1;Linux VM;exact image ID/TBD | FULL_ACCOUNT_COST_TBD |
| B2 | AWS Lightsail Linux/Unix General Purpose16GB/4vCPU/320GB publicIPv4 bundle; Proposed ap-south-1 Mumbai;exact blueprint/bundle ID TBD | FULL_ACCOUNT_COST_TBD |
| C0 | HP Laptop14s-dq2xxx upgrade path; Exact sales SKU not established;board87FD;8GiB module observed;upgrade kit unverified | NO_VERIFIED_CANDIDATE |


## Field-level records

| Candidate | Field | Status | Observation | Source/freshness |
|---|---|---|---|---|
| A1 | CPU_RAM_storage | CATALOG_ONLY | meer10 Core3 100U,2x16GB DDR5,500GB PCIe4 NVMe proposed | Q01;CURRENT_PUBLIC_SOURCE |
| A1 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | Q01,Q02;CURRENT_PUBLIC_SOURCE |
| A1 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | Q01,Q02;CURRENT_PUBLIC_SOURCE |
| A1 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | Q01,Q02;CURRENT_PUBLIC_SOURCE |
| A1 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | Q01,Q02;CURRENT_PUBLIC_SOURCE |
| A1 | Linux_support | CATALOG_ONLY | Ubuntu24.04 listed;Server/kernel not certified | Q01;CURRENT_PUBLIC_SOURCE |
| A1 | power_India_voltage | CATALOG_ONLY | 19V90W adapter;worldwide-grid claim;actual input label/plug unverified | Q01,Q02;CURRENT_PUBLIC_SOURCE |
| A1 | shipping_India | UNKNOWN | India absent from public list;do not assume delivery | Q02;CURRENT_PUBLIC_SOURCE |
| A1 | warranty_RMA | CATALOG_ONLY | 1/2/3year choices;international repair freight customer;exact purchased term absent | Q03;CURRENT_PUBLIC_SOURCE |
| A1 | customs_GST_responsibility | CATALOG_ONLY | Vendor does not collect international import taxes;customer liability,amountsTBD | Q02;CURRENT_PUBLIC_SOURCE |
| A1 | lead_time | CATALOG_ONLY | Typical10business-day build;not India arrival commitment | Q02;CURRENT_PUBLIC_SOURCE |
| A1 | return_conditions | CATALOG_ONLY | 30days from shipment;RMA/condition;original freight excluded,return cost customer | Q03;CURRENT_PUBLIC_SOURCE |
| A2 | CPU_RAM_storage | CATALOG_ONLY | ONE H255,2x16GB DDR5,500GB NVMe4.0 proposed | Q04;CURRENT_PUBLIC_SOURCE |
| A2 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | Q04,Q05,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | Q04,Q05,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | Q04,Q05,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | Q04,Q05,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | Linux_support | CATALOG_ONLY | Linux options;exactServer24.04 build not certified | Q04,Q08;CURRENT_PUBLIC_SOURCE |
| A2 | power_India_voltage | CATALOG_ONLY | Historical100-240V50-60Hz/19V6.32A120W;not freshly reconfirmed;India plug/input label unknown | E19/P04;HISTORICAL_NOT_RECONFIRMED |
| A2 | shipping_India | CATALOG_ONLY | India appears in coverage list;freight/address eligibility not quoted | Q05;CURRENT_PUBLIC_SOURCE |
| A2 | warranty_RMA | CONFLICTING | FAQ1year international;FAQ return freight vendor versus Questions both legs customer;exact product/India applicability unresolved | Q06,Q07;CURRENT_PUBLIC_SOURCE |
| A2 | customs_GST_responsibility | CATALOG_ONLY | Non-EU local customs/tax may be collected;21%display not India landed tax;amountTBD | Q04,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | lead_time | CATALOG_ONLY | 2-5dayassembly;shipping/customs/stock indicative | Q04,Q06;CURRENT_PUBLIC_SOURCE |
| A2 | return_conditions | CATALOG_ONLY | 15dayinitiation,authorized return received30days;voluntary shipping/fees excluded;India applicability unknown | Q18;CURRENT_PUBLIC_SOURCE |
| C0 | CPU_RAM_storage | UNKNOWN | HP14s-dq2xxx family/board87FD;exactSKU/kit absent | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | exact_selected_quote | UNKNOWN | TBD;no checkout,contact or binding quote | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | landed_price | UNKNOWN | TBD;no checkout,contact or binding quote | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | confirmed_stock | UNKNOWN | TBD;no checkout,contact or binding quote | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | India_delivery_commitment | UNKNOWN | TBD;no checkout,contact or binding quote | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | Linux_support | UNKNOWN | Upgrade/hypervisor/license unverified | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | power_India_voltage | UNKNOWN | No new adapter inspection | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | shipping_India | UNKNOWN | No compatible kit or seller quote | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | warranty_RMA | UNKNOWN | Exact upgrade warranty effects unknown | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | customs_GST_responsibility | UNKNOWN | Local kit invoice/tax unknown | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | lead_time | UNKNOWN | TBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| C0 | return_conditions | UNKNOWN | TBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| B1 | bundle | CATALOG_ONLY | Basic Regular16GiB/8vCPU/320GiB,USD96monthly | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | region_eligibility | UNKNOWN | BLR1 catalog,unreadable exactsize cells | Q10;CURRENT_PUBLIC_SOURCE |
| B1 | tenancy_architecture | UNKNOWN | Shared CPU historically documented;actual host/architecture/tenancy unmeasured | E19/E18;HISTORICAL_OR_UNKNOWN |
| B1 | snapshots | CATALOG_ONLY | USD0.06/GBmonth;quantityTBD | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | backup | CATALOG_ONLY | Weekly20%/daily30%alternatives;selectionTBD | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | extra_storage | UNKNOWN | Included320GiB not billed again;extra quantity/rateTBD | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | egress | UNKNOWN | 6000GiB listedtransfer;actual billable usage/rateTBD | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | IPv4 | UNKNOWN | Exact selected IP product/separate fees not established | Q09;CURRENT_PUBLIC_SOURCE |
| B1 | monitoring_logging | UNKNOWN | Sink selection/retention/export/chargesTBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| B1 | tax_FX | UNKNOWN | USD/INR96.6149 reference obtained;settlement fees/spread and exact account tax unknown | Q11,Q17;CURRENT_PUBLIC_SOURCE |
| B1 | shutdown_cost | CATALOG_ONLY | Poweroff stillbilled;destroy required toend instance charge | Q20;CURRENT_PUBLIC_SOURCE |
| B1 | persistent_deletion | CATALOG_ONLY | Associated resources notdeleted bydefault;backups expire4weeks fromcreation | Q12;CURRENT_PUBLIC_SOURCE |
| B1 | provider_access | UNKNOWN | Providerphysical/operatoraccess andpurge attestation notestablished;guest control notprovider exclusion | Q12;CURRENT_PUBLIC_SOURCE |
| B1 | account_organization_custody | UNKNOWN | No account orrights evidenced;organization/rootMFA/restoreauthority TBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| B1 | egress_enforcement | UNKNOWN | Publicfirewall catalog isnotactual Java default-deny proof | E19/E15;HISTORICAL_OR_UNKNOWN |
| B1 | billing_quote | UNKNOWN | No account-specific quote orconfirmedcheckout | Q09;CURRENT_PUBLIC_SOURCE |
| B2 | bundle | CATALOG_ONLY | Linux/Unix publicIPv4 GeneralPurpose16GB/4vCPU/320GB,USD84monthly | Q13;CURRENT_PUBLIC_SOURCE |
| B2 | region_eligibility | UNKNOWN | Mumbai region catalog only;account/bundle quota unknown | Q19;CURRENT_PUBLIC_SOURCE |
| B2 | tenancy_architecture | UNKNOWN | No dedicated tenancy or architecture established | E19/E18;HISTORICAL_OR_UNKNOWN |
| B2 | snapshots | CATALOG_ONLY | USD0.05/GBmonth;quantityTBD | Q13;CURRENT_PUBLIC_SOURCE |
| B2 | backup | CATALOG_ONLY | Snapshot-based retention selection/quantityTBD | Q13,Q15;CURRENT_PUBLIC_SOURCE |
| B2 | extra_storage | UNKNOWN | Included320GB not billed again;extra0.10USD/allocatedGBmonth,quantityTBD | Q13;CURRENT_PUBLIC_SOURCE |
| B2 | egress | UNKNOWN | Mumbai3TB listed allowance;usage/direction/regional overageTBD | Q13;CURRENT_PUBLIC_SOURCE |
| B2 | IPv4 | UNKNOWN | PublicIPv4 selected bundle;separate static-IP residual billing possible | Q13,Q15;CURRENT_PUBLIC_SOURCE |
| B2 | monitoring_logging | UNKNOWN | Sink selection/retention/export/chargesTBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| B2 | tax_FX | UNKNOWN | USD/INR96.6149 reference obtained;settlement fees/spread and exact account tax unknown | Q17;CURRENT_PUBLIC_SOURCE |
| B2 | shutdown_cost | CATALOG_ONLY | Stopped standard instance billed untildelete;noResearchdiscount | Q14;CURRENT_PUBLIC_SOURCE |
| B2 | persistent_deletion | CATALOG_ONLY | Automatic snapshots deleted unlesskept;manualsnapshots/disks/IP/LB mayremainbillable | Q15;CURRENT_PUBLIC_SOURCE |
| B2 | provider_access | UNKNOWN | Providerphysical/operatoraccess andpurge attestation notestablished;guest control notprovider exclusion | E19/P18;HISTORICAL_OR_UNKNOWN |
| B2 | account_organization_custody | UNKNOWN | No account orrights evidenced;organization/rootMFA/restoreauthority TBD | E19/E18;HISTORICAL_OR_UNKNOWN |
| B2 | egress_enforcement | UNKNOWN | Publicfirewall catalog isnotactual Java default-deny proof | E19/E15;HISTORICAL_OR_UNKNOWN |
| B2 | billing_quote | UNKNOWN | No account-specific quote orconfirmedcheckout | Q13;CURRENT_PUBLIC_SOURCE |
| A1 | display_price | CATALOG_ONLY | USD999 base,not selected32GB/500GB quote | Q01;CURRENT_PUBLIC_SOURCE |
| A2 | display_price | CATALOG_ONLY | EUR832+24032GB option=1072catalogsum;21%display not India landed tax | Q04;CURRENT_PUBLIC_SOURCE |
| B1 | billing_cadence | CATALOG_ONLY | Per-second from2026-01-01;minimum60seconds orUSD0.01;USD96monthly cap | Q09;CURRENT_PUBLIC_SOURCE |
| B2 | billing_cadence | CATALOG_ONLY | Hourly up to monthly cap;stopped standard instance billed until deletion | Q14;CURRENT_PUBLIC_SOURCE |


## Source register

- **Q01** [System76: meer10 Core3 100U /32GB(2x16)/500GB proposed](https://system76.com/mini/meerkat). LIST_PRICE; US/USD. 999 display is not selected configuration quote; Ubuntu24.04 and options listed;19V90W adapter; stock unconfirmed. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q02** [System76: International shipping](https://system76.com/shipping/). PUBLIC_TERMS; India eligibility/USD. India absent from country list; worldwide-grid claim, plug adapter may be needed; exact freight at checkout; customer import charges; typical10business-day build, not India promise. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q03** [System76: Hardware warranty/returns](https://system76.com/warranty/). PUBLIC_TERMS; International/USD amounts unquoted. 1/2/3year options;international repair freight customer;30days from shipment return, original freight excluded;RMA and condition requirements. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q04** [Slimbook: ONE H255/2x16GB/500GB](https://slimbook.com/shop/product/one-amd-ryzen-7-h-255-1956?category=14). LIST_PRICE; Spain/EUR;India tax unresolved. 832 base plus240 RAM option;500GB option;21%display tax;non-EU customs possible;2-5day assembly, not delivery. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q05** [Slimbook: Shipping coverage](https://slimbook.com/shippinginfo). PUBLIC_TERMS; India/EUR freight unquoted. India explicitly listed;destination-specific charges and commitment unknown. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q06** [Slimbook: General FAQ](https://slimbook.com/en/faq). PUBLIC_TERMS; International/EUR fees unknown. International warranty1year listed;non-EU inward freight customer and repaired return vendor;stock indicative;applicability to exact India order unknown. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q07** [Slimbook: General questions, laptop-oriented](https://slimbook.com/en/questions). PUBLIC_TERMS; OutsideEurope/currency N/A. Says customer pays both repair shipments outsideEurope;conflicts with FAQ;scope/precedence unresolved. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q08** [Slimbook: ONE H255](https://slimbook.com/en/one). PUBLIC_TERMS; Global/EUR quote absent. Page read; exact power numbers not freshly reconfirmed; E19/P04 remains historical. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q09** [DigitalOcean: Basic Regular16GiB/8vCPU/320GiB](https://www.digitalocean.com/pricing/droplets). LIST_PRICE; ProposedBLR1/USD. 96monthly;6000GiBtransfer;weekly20%/daily30% backup alternatives;snapshot0.06USD/GBmonth;not account quote;per-second from2026-01-01;minimum60seconds orUSD0.01 whichever higher;displayed0.14286hour is rounded. Publisher date: Per-second billing2026-01-01;price expiry NOT_STATED; quote expiry: NOT_STATED.
- **Q10** [DigitalOcean: BLR1 catalog](https://docs.digitalocean.com/platform/regional-availability/). PUBLIC_TERMS; Bangalore/currency N/A. BLR1 listed but extracted size availability cells blank;not account quota evidence. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q11** [DigitalOcean: India tax guidance](https://docs.digitalocean.com/platform/billing/taxes/ind/). PUBLIC_TERMS; India/USD. 18%GST vendor guidance;USD invoices;actual tax location/GSTIN treatment unverified. Publisher date: Vendor tax guidance as of2017-07-01; last verified2026-06-22; current rate effective date not separately established; quote expiry: NOT_STATED.
- **Q12** [DigitalOcean: Droplet destruction](https://docs.digitalocean.com/products/droplets/how-to/destroy/). PUBLIC_TERMS; Global/currency N/A. Associated snapshots/volumes not destroyed by default;automated backups retained four weeks from creation;provider physical erasure not proven. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q13** [AWS: Linux/Unix publicIPv4 General Purpose16GB/4vCPU/320GB](https://aws.amazon.com/lightsail/pricing/). LIST_PRICE; ProposedMumbai/USD. 84monthly;Mumbai half headline6TB=3TB;snapshots0.05USD/GBmonth;extra disk0.10USD/allocatedGBmonth;tax unknown. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q14** [AWS: General Lightsail billing](https://docs.aws.amazon.com/en_en/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html). PUBLIC_TERMS; Global/USD. Stopped instances still billed until deleted;not Lightsail for Research discount;account-specific terms unverified. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q15** [AWS: Instance deletion](https://docs.aws.amazon.com/lightsail/latest/userguide/delete-an-amazon-lightsail-instance.html). PUBLIC_TERMS; Global/USD residual charges. Automatic snapshots deleted unless retained;static IP/manual snapshots/disks/load balancers can remain and incur charges;not physical erasure proof. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q16** [FBIL: Dated USD/EUR-INR reference search](https://www.fbil.org.in/). NOT_RETRIEVED; India/INR. Page returned zero extracted lines;no authoritative dated FX value obtained;third-party search results not used. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q17** [Reserve Bank of India; rates credited to FBIL: INR reference rates per1USD/EUR](https://www.rbi.org.in/). PUBLIC_TERMS; India/INR. USD96.6149;EUR108.4728;1.00pm October09,2026;timezone not stated;not executable settlement quote. Publisher date: 2026-10-09;publisher1.00pm;timezone NOT_STATED; quote expiry: NOT_STATED.
- **Q18** [Slimbook: Published terms](https://slimbook.com/en/legal/advice). PUBLIC_TERMS; International/EUR fees unknown. 15day return initiation from delivery-note date;authorized return received within30days;voluntary shipping/fees not refunded;exact India applicability unknown. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q19** [AWS: Lightsail region catalog](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-regions-and-availability-zones-in-amazon-lightsail.html). PUBLIC_TERMS; Mumbai ap-south-1/currency N/A. Mumbai listed;exact blueprint/bundle eligibility,quota and account approval absent. Publisher date: NOT_STATED; quote expiry: NOT_STATED.
- **Q20** [DigitalOcean: Power-off billing documentation](https://docs.digitalocean.com/reference/doctl/reference/compute/droplet-action/power-off/). PUBLIC_TERMS; Global/USD. Powered-off Droplets remain billable;read-only documentation, no command executed. Publisher date: NOT_STATED; quote expiry: NOT_STATED.

Q16 yielded zero extracted lines: NOT_RETRIEVED for usable FX. Q17 independently supplies reference rates without erasing that failure. Q10 blank size cells cannot verify BLR1 bundle availability. Q08 did not reconfirm exact adapter figures; E19/P04 stays historical. No tax/contract statement is an account-specific ruling.

V2 contract, governance second review and four technical reviews PENDING. Operational NO_GO; MIS NOT_READY; Phase12 BLOCK; H1/H2 FROZEN; July TEST SEALED; INR10,000 ceiling unchanged; StageB ZERO_CALL_ABORT preserved. Acquisition, provisioning and real access NOT_AUTHORIZED.
