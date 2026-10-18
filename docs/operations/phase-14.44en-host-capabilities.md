# Host capability assessment

Local CIM reports Microsoft Windows 11 Home Single Language, 64-bit, version10.0.26200; registry build26200 UBR9711 and DisplayVersion25H2. Registry ProductName says Windows10; retain this discrepancy rather than normalize source observations.

HypervisorPresent is true. CPU Architecture9; VirtualizationFirmwareEnabled, VMMonitorModeExtensions and SLAT flags are false. These concurrent observations do not prove hardware virtualization is disabled or identify a usable guest provider. Firmware and supported guest capability remain UNVERIFIED.

VirtualMachinePlatform and WSL are returned with InstallState1. Hyper-V/Containers/Sandbox were not returned by the filtered local query; WindowsSandbox.exe absent at queried path. vmcompute Running. No elevation performed; current administrator-role test false. D: NTFS,275993206784 bytes free at discovery (capacity may change).

Docker client exists in the user's DockerDesktop resources/bin path; file version metadata0.0.0.0 is not a verified Docker product version. VBoxManage/vmrun not found on PATH; standard VirtualBox/VMware/Docker/Sandbox paths absent. This is a bounded search, not proof no tools exist elsewhere. Docker backend/container mode and provider approval UNVERIFIED: no daemon/context/private-settings access or remote connection. No qualification implied by presence.

Exact queries, raw values, durations and limitations are retained in host-discovery.json. Every permitted query completed within read-only limits. No optional feature enabled or security setting changed.
