# Windows capability assessment

Read-only observation: Microsoft Windows 11 Home Single Language, version 10.0.26200/build 26200, 64-bit, Intel i3-1125G4. Current token is not administrator. HypervisorPresent is true, while processor virtualization firmware, VM monitor extensions and second-level translation fields are false. These observations do not establish a usable Windows guest provider or prove hardware incapability.

Get-VM, Get-VMHost, WindowsSandbox.exe, VBoxManage.exe and vmrun.exe were not found by command lookup. Docker client and vmcompute.exe exist; hns and vmcompute services are running. Installed presence is not approval. Hyper-V, Sandbox, Containers and VirtualMachinePlatform feature-state queries require elevation, so their states remain UNVERIFIED. No elevation or feature change was attempted.

Docker Desktop backend remains UNVERIFIED: no daemon request or settings/credential-file read was made. No approved existing VM was identified in inspected governance evidence. Provider compatibility, image licensing/provenance, runtime closure and actual network/process/filesystem containment remain UNVERIFIED. No guest was provisioned, no host firewall changed and no network retrieval occurred. External windows-capability.json retains exact query observations and limitations.
