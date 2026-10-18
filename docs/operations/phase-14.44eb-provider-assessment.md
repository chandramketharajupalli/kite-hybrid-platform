# Local Windows provider assessment

Observed host: Windows11Home Single Language10.0.26200/build26200,64bit. Query sources: local CIM OS/CPU/computer-system properties, Get-Command, Get-Service, optional-feature read queries and Docker executable file-version metadata. No network/provider daemon requests. Optional-feature reads failed with elevation-required; no elevation requested or feature modified. HypervisorPresent=true with false CPU capability fields is ambiguous and cannot certify available virtualization.

| Candidate | Local identity/availability | Windows compatibility / privileges | Isolation, image, offline and recovery status |
| --- | --- | --- | --- |
| Hyper-V | Get-VM/Get-VMHost absent; feature state UNVERIFIED | Host edition/build observed; actual supported host/guest matrix and required privilege grant UNVERIFIED | No approved supervisor/VM inventory; storage/process/network/image/snapshot guarantees UNVERIFIED |
| Windows Sandbox | WindowsSandbox.exe not discovered; feature query requires elevation | Host/guest compatibility and feature eligibility UNVERIFIED; no enablement authorized | No approved image, offline runtime or export/destruction design verified |
| Windows containers | Docker client file version29.8.0 installed; containerd command absent; vmcompute10.0.26100.9711 and running vmcompute/hns observed | Client presence does not prove daemon mode, Windows-container support, image compatibility or privileges; daemon not queried | No approved Windows image/supervisor; process-isolated containers not accepted as sufficient by assumption; network/mount/recovery unverified |
| Existing approved VM/supervisor | None identified in inspected local governance records; no disk-wide VM search | Exact identity/version/authority null | No existing guest reused or inspected as approved; no provisioning |

For every candidate, filesystem/process/network isolation, image provenance, offline operation and snapshot/recovery support require provider-specific independently reviewed evidence. Required virtualization hardware/privilege capabilities are unknown where queries are unavailable. Do not equate installed/running with approved. Current decision ISOLATION_PROVIDER_UNAVAILABLE; provider selection=null. No host firewall, network, feature, service or virtualization setting changed.
