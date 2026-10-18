# Infrastructure alternatives
| Option | Design | Workload boundary | Remaining evidence |
|---|---|---|---|
| A | Local isolated Linux runtime | Pure A46 only, conditional portability | No suitable approved runtime established |
| B | Dedicated local Windows VM | Compatible families separately approved | Provider/version,licensed image,runtime,containment |
| C | Approved remote Windows VM | Compatible families separately approved | All B evidence plus remote/export authority |
| D | One approved Windows VM architecture | Separate fresh state,approval and receipts for each family | Shared architecture is not shared authorization |
| E | Deferral | No execution | Retain DEFER_PENDING_APPROVED_PROVIDER |

Complete field comparison is in external infrastructure-alternatives.json. All provider identities/versions,image hashes,privileges,licenses and prices unknown/null. Prior WSL/Docker presence evidence is not approval or usable guest proof. Cost methodology requires approved offline quotes; no price lookup.
Technical recommendation E is separate from the null owner-selection field.
