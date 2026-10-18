# Inactive supervisor integration boundary

This adapter validates pure TEST_ONLY contracts and never imports the native supervisor. real_authorization always rejects, independently of any fixture verdict. No process, network, file-write or credential APIs exist in integration.py. Existing tracked modules have no reference to it.

H evaluate_synthetic pins the earlier baseline 913a1014a0ffd5fa14fc9d228ffb3070b1d33d0f. Its real evaluate_authorization always rejects. I does not rebind H or claim delegated runtime integration; it checks exact source fingerprints and a separate current-baseline contract. Future activation requires a separately reviewed implementation and authenticated owner approval.

F run validates a sealed command-allowlist hash and command/source/image pins, creates a retained Job Object before suspended process resume, and checks handle/PID/creation identities. image_identity uses QueryFullProcessImageNameW and distinguishes errors; no pin means rejection. E provides native APIs and lifecycle classifications. Importing these modules initializes native APIs, so I imports neither. Exact APIs and hashes are retained in integration-assessment.json.

Job ownership alone does not establish console association, guest isolation or owner authority. Unknown identity, access-denied, creation mismatch, missing console relationship or unapproved cleanup must fail closed. Default conhost rejection and all historical sources remain unchanged. Historical signature, console association, revocation freshness and WinError5/31 causes remain UNVERIFIED.

Future process containment requires suspended-root assignment, retained handles, exact executable/command and parent lifetime, explicit console association, owned-job-only cleanup, partial receipts and unrelated sentinel survival. No arbitrary PID termination. Job Objects do not establish filesystem or network containment.
