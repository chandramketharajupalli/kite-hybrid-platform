# Infrastructure options

| Candidate | Concrete evidence | Unresolved requirements | Selection |
|---|---|---|---|
| A existing workstation | Windows11Home, hypervisor present, VMP/WSL listed, Docker client | Approved provider/version, supported Windows guest, licensing, image, runtime and all containment | Not qualified |
| B dedicated Windows host | No host/provider supplied | Identity/version, privileges, licensing, acquisition and all guest/containment evidence | Unverified |
| C remote Windows host | No host approved; no connection attempted | All B requirements plus separate remote and export authorization | Unverified |
| D Windows containers | Client executable only | Backend/mode, host/image compatibility, Windows support, independent containment | Unverified |
| E defer | A-D lack sufficient evidence | Retain pending governance decision | Selected |

Per-candidate provider/version, privileges, compatibility, licensing, runtime, filesystem/network/credential/process/resource controls, export, recovery, complexity and approvals are explicitly recorded in infrastructure-assessment.json. Unknown identities remain null; no versions, image hashes, costs or licenses invented. No provider is qualified.
