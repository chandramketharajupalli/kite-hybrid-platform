# Phase 13.18 concrete research shortlist

Result: CONCRETE_HOST_SELECTION_INCOMPLETE. Four named research configurations;
none is procurement-ready or security-certified. Category A has two conditional
catalog candidates, not two confirmed India-deliverable quotes; B has two published
plans, not two account-available allocations. C has NO_VERIFIED_CANDIDATE.
[Architecture](../architecture/kite-concrete-linux-host-candidates.md),
[cost model](phase-13.18-verified-cost-model.md),
[security matrix](phase-13.18-security-evidence-matrix.md).

| ID / exact candidate | CPU / memory / storage | Availability, OS and management | Price evidence and blockers |
|---|---|---|---|
| A1 System76 Meerkat meer10 | x86_64 Intel;6physical hybrid cores per S02,not6dedicated performance cores; 32GB proposed listed option; 500GB proposed listed option | Core3 100U,32GB(2x16)DDR5,500GB PCIe4 NVMe,Ubuntu24.04 proposed; US storefront;India delivery unverified; CONDITIONAL_RESEARCH_CANDIDATE | S01,S02,S03; India shipment not established; 32GB configured quote absent; actual Server24.04 image/kernel and security tests absent; witness/sink custody absent |
| A2 Slimbook ONE AMD Ryzen7 H255 | x86_64 AMD;8cores/16threads per S04/S06; 32GB listed upgrade option; 500GB NVMe listed base option | 32GB(2x16)DDR5,500GB NVMe4.0;Ubuntu Server24.04 intended,not factory-certified; Spain/EU listing;India landed terms unverified; CONDITIONAL_RESEARCH_CANDIDATE | S04,S05,S06; India freight/customs/warranty and quote absent; catalog sum not checkout quote; exact Server24.04/kernel support unverified; witness/sink custody absent |
| B1 DigitalOcean Basic Regular16GiB/8vCPU/320GiB | 8shared vCPUs;exact silicon/architecture instance evidence unverified; 16GiB vendor allocation; 320GiB SSD included | Proposed BLR1;Linux VM;exact image ID/TBD; BLR1 Bangalore proposed;exact plan availability unknown; CONDITIONAL_RESEARCH_CANDIDATE | S07,S08,S09,S10; Regional availability cells not readable;quota/stock unknown; shared CPU sustained workload unknown; exact Ubuntu image/architecture unverified; account/remote access/tax/witness/sinks unknown |
| B2 AWS Lightsail Linux/Unix General Purpose16GB/4vCPU/320GB publicIPv4 bundle | 4vCPUs;architecture/dedication not established by pricing page; 16GB vendor unit;not silently relabeledGiB; 320GB SSD vendor unit included | Proposed ap-south-1 Mumbai;exact blueprint/bundle ID TBD; ap-south-1 Mumbai catalogued;account quota unknown; CONDITIONAL_RESEARCH_CANDIDATE | S11,S12; Blueprint/architecture/quota/tax unverified; publicIPv4 bundle needs explicit network rejection tests,no listener authorized; provider/admin/log/witness custody absent |
| C0 HP Laptop14s-dq2xxx upgrade path | Historical4cores/8logical;no freshCPU read; Observed8GiB module;CIM maximum32GiB is firmware claim only; HistoricalD257.88GiB free;not refreshed | Exact sales SKU not established;board87FD;8GiB module observed;upgrade kit unverified; Existing user host;India upgrade quote TBD; NO_VERIFIED_CANDIDATE | LOCAL_CIM,S16,E16; Only family model,not exactSKU; HPmanual403;no validated compatibility/kit; current8GiB guest target fails historicalRAM; license/hypervisor/headroom unverified |

A1/A2 are independently administered bare-metal proposals; no virtualization
capability, firmware setting, admin access or physical custody has been measured.
Their32GB choices are listed configurations, not measured14GiB available RAM.
B1/B2 avoid Windows guest-RAM contention but add provider,account,snapshot and
remote-management authorities. A guest allocation is not a dedicated physical core
or independent root. Each needs workload headroom and exact kernel/image proof.
No nested-virtualization capability is assumed or needed for ordinary containers.

CIM observation2026-10-11T17:38:19.5908844+00:00..17:38:20.8490342+00:00:
HP Laptop14s-dq2xxx,HP87FD board/version57.26,one8589934592byte module,part
NT8GA64D88CX3S-JR,speed3200,FormFactor12,SMBIOSMemoryType26,arrayMaxCapacity33554432KiB
andMemoryDevices2. No serial/service tag/UUID collected.32GiB firmware-reported max
is not manufacturer-supported upgrade proof. No exact retail SKU can be inferred.
HP family manual returned403; forum suggestions were discovery only, not authority.
Historical E16 available0.76-0.85GiB/total7.65GiB and4cores/8logical remain historical.
No RAM purchase, chassis opening, benchmark, firmware or edition change.

Screened leads not promoted: Dell QCM1255 lists Ubuntu24.04 and suitable options but
requires an unrequested sales callback for pricing; QBM1250 redirected to a challenge.
Lenovo discovery gave no verified India configured quote. No seller was contacted.

## Public source register

Retrieval timestamps are bounded local UTC intervals, not exact per-request times
or publisher attestations. Web tool did not supply authoritative receipt timestamps.
Short paraphrases preserve claims, not full copyrighted pages. No source contact,
cart action,account login,downloaded image or quote acceptance. Static shopping-page
cart text is not an executed transaction. Search snippets were discovery only.
Prices are observations, with expiry NOT_STATED and revalidation mandatory.

| ID / publisher / version | Source | Region/currency | Attributable observation and limit |
|---|---|---|---|
| S01 / System76 / meer10 / Ubuntu24.04 | [source](https://system76.com/desktops/meerkat) | US storefront; USD via S03 | Catalog says starting999; model CPU choices and Linux OS; not selected-config price or stock guarantee. |
| S02 / System76 / meer10 Core3 100U /32GB/500GB proposed | [source](https://system76.com/mini/meerkat) | US storefront; USD | Listed6-core CPU,32GB(2x16)DDR5 and500GB PCIe4 choices. Default display999 is not a quoted32GB configuration.1/2/3-year warranty options; international repair shipping excluded. |
| S03 / System76 / International shipping policy | [source](https://system76.com/shipping) | USD; India delivery NOT_ESTABLISHED | Prices USD; shipping and import duties separate. India not found in retrieved country list; no forwarding workaround or checkout. |
| S04 / Slimbook / ONE Ryzen7 H255 | [source](https://slimbook.com/en/one) | Spain/EU; India terms TBD | 8cores/16threads,DDR5 slots,NVMe and Linux options; Spanish3-year/European2-year warranty does not establish India warranty. |
| S05 / Slimbook / ONE Ryzen7 H255 listing | [source](https://slimbook.com/shop/category/sobremesa-one-14) | EUR; Spain/EU display | 832EUR catalog listing, promotional label; no guaranteed quote expiry or India stock. |
| S06 / Slimbook / ONE Ryzen7 H255 /32GB(2x16)/500GB NVMe proposed | [source](https://slimbook.com/shop/product/one-amd-ryzen-7-h-255-1956?category=14) | EUR;21%tax displayed; non-EU recalculation required | 832 base and240 upgrade delta imply1072 component sum.500GB base option; Ubuntu-derived OS choice, not exact Server24.04 certification.2-5day assembly statement not India delivery promise.India freight/customs/warranty TBD. |
| S07 / DigitalOcean / Basic Regular16GiB/8vCPU/320GiB | [source](https://www.digitalocean.com/pricing/droplets) | USD via S10; proposed BLR1 | 96/month displayed;6000GiB transfer.Weekly backup20% or daily30%;snapshots0.06/GB-month.Only bundle subtotal established; no regional quote/account quota. |
| S08 / DigitalOcean / BLR1 regional catalog | [source](https://docs.digitalocean.com/platform/regional-availability/) | Bangalore,India; currency not applicable | BLR1 listed; rendered plan availability cells blank, so exact Basic-size stock/availability not verified. |
| S09 / DigitalOcean / Basic Droplets | [source](https://docs.digitalocean.com/products/droplets/details/features/) | Global;currency not applicable | Linux virtual machines;Basic shared CPUs.Exact processor,Ubuntu24.04 image build and kernel access not established. |
| S10 / DigitalOcean / India billing guidance last verified2026-06-22 | [source](https://docs.digitalocean.com/platform/billing/taxes/ind/) | India;USD | Vendor says invoices/payments USD,18%GST for India tax location; validGSTIN alters invoice treatment with RCM.Actual user tax treatment not established. |
| S11 / AWS / Linux/Unix General Purpose publicIPv4 16GB/4vCPU/320GB | [source](https://aws.amazon.com/lightsail/pricing/) | USD;Mumbai proposed | 84/month bundle;Mumbai transfer half6TB=3TB.84*12=1008 bundle-only.Snapshots0.05USD/GB-month;block storage0.10USD/allocatedGB-month.India tax and egress rate for this exact bundle TBD. |
| S12 / AWS / Lightsail region catalog | [source](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-regions-and-availability-zones-in-amazon-lightsail.html) | ap-south-1 Mumbai;currency not applicable | Region documented;exact bundle quota/availability,CPU architecture and image build require confirmation. |
| S13 / Canonical / ubuntu-24.04.5-live-server-amd64.iso | [source](https://releases.ubuntu.com/24.04/SHA256SUMS) | Global;currency not applicable | Published text SHA25697f3d7ffb032c3eb3b23d2c8be9cc76e60c2c1f2c0146ba5ba9fe01cafae0fd8;ISO bytes and signed checksum chain NOT_VERIFIED;no ISO retrieval. |
| S14 / Canonical / Ubuntu AppArmor documentation | [source](https://documentation.ubuntu.com/server/how-to/security/apparmor/) | Global;currency not applicable | Loaded/enforcing profile and audit guidance supports proposed tests, not measured candidate enforcement. |
| S15 / Docker / Docker Engine userns-remap documentation | [source](https://docs.docker.com/engine/security/userns-remap/) | Global;currency not applicable | Documented UID remapping mechanism;candidate kernel/subordinate IDs/actual process mapping not tested. |
| S16 / HP / HP14s-dq2000 family manual portal | [source](https://support.hp.com/us-en/product/setup-user-guides/hp-laptop-pc-14-d2000/model/36683215) | Global;currency not applicable | 403Forbidden;no exact local SKU/manual compatibility proof.Only family-level local CIM model available. |
| S17 / Adoptium / Temurin Linux installation documentation | [source](https://adoptium.net/installation/linux) | Global;currency not applicable | Package repository/GPG guidance is not OCI image authentication.Containers documentation click failed;no signature verification or installation. |
| S18 / Dell / Dell Pro Micro QCM1255 | [source](https://www.dell.com/en-in/shop/desktop-computers/new-dell-pro-micro-desktop/spd/dell-pro-qcm1255-micro) | India;INR price TBD | Call-to-order only;Ubuntu24.04 listed with Ryzen5PRO8600GE6cores and32GB/512GB options.No priced configured SKU/stock,so screened lead not accepted acquisition candidate. |
| S19 / Dell / QBM1250 | [source](https://www.dell.com/en-in/shop/desktop-computers/dell-pro-micro-plus-desktop/spd/dell-pro-qbm1250-plus-micro) | India;currency unverified | Redirect to challenge page;no price or configured candidate accepted. |
| S20 / DigitalOcean / Tax by country | [source](https://docs.digitalocean.com/products/billing/taxes/) | India;USD via S10 | 18%India listed;tax applicability not independently established for operator. |

All sources retrieved within 2026-10-11T17:38:20.8490342+00:00 to 2026-10-11T17:40:44.577625+00:00. Final URLs/redirects,failed reads and per-source conditions are retained in the manifest. No INR exchange conversion; no sourced rate obtained.
