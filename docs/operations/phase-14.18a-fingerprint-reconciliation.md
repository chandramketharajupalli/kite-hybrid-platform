# Risk-policy identity reconciliation

The original Phase14.18 Policy hashed Pydantic model_dump_json(), whose nested maps
retain insertion order. The report writer used existing sorted-key canonical JSON.
After loading that JSON, the same sector values had different insertion order.
This discrepancy affects all three original synthetic examples and their recorded
control/journal policy hashes; it is not an economic change or a missing file.

Original fixture sector order SBIN,TCS,INFY:
a3c22ad01b3bb18df011c29d47736f821a9c3337e50eaeb7a7b0bd7ad2072981.
Recomputed legacy policy after report serialization, INFY,SBIN,TCS:
43ed6d2a4334ac9ebe1442d2a67feee787afbde5a55f896414c0314885cdd675.
Corrected synthetic policy under Phase1418RiskPolicyCanonical.v2:
3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1.

The exact 877-byte canonical payload is canonical-policy-payload.json in this phase's
generation-1 directory. Its raw SHA-256 is the corrected policy fingerprint. No newline
or alternate JSON rendering is included in the hash. Typed nodes distinguish maps,
sequences, integers, booleans, strings, null and Decimal values; map keys sort recursively.
Sequence order stays significant. Decimal sign/coefficient/exponent are normalized by
tuple manipulation, without arithmetic or ambient precision. Trailing zeros and signed
zero normalize by numeric value; significant digits are never rounded. Floats, nonfinite
Decimals, non-string mapping keys and unsupported objects reject at the canonical payload
boundary. Validated policy monetary fields remain Decimal under existing contracts.

The domain-separated payload uses schema Phase1418RiskPolicyCanonical.v2 and existing
compact sorted-key JSON with allow_nan=False, encoded as UTF-8. Unicode strings are
JSON-escaped by that existing encoder and retain their exact code points. There is no
new Unicode-equivalence rule or list sorting. No risk limit or approval requirement changed.

## Explicit compatibility boundary

Policy requires fingerprint_version; Control requires policy_fingerprint_version.
Missing, v1 and unknown versions fail validation. Runtime guards also reject bypassed
model-copy values. New plan and summary schemas are Phase1418Portfolio.v2 and
Phase1418PortfolioSummary.v2. Plans, controls, policy, summary and journal records carry
the version; summary and journal policy hashes match the canonical payload.

An existing v1 checkpoint rejects before writes with
PAPER_HALTED:INCOMPATIBLE_CHECKPOINT_VERSION. No automatic upgrade, repair or reinterpretation
occurs. Old journals remain v1 historical evidence. New simulation requires explicit v2
policy/control construction and a fresh external directory. Full request/data/software
bindings remain strict; this correction does not relax unrelated replay requirements.

Original Phase14.18 reports, evidence manifest, verifier, plans and receipts remain
byte-identical. Their old software fingerprint remains a historical claim; corrected
source necessarily has a new dependency fingerprint. The new manifest records exact
before/after hashes for portfolio.py and the existing test fixture constructor, plus
the new canonicalization module, regression tests and independent verifier.

All three corrected examples are additive under Phase14.18A. They have new run IDs and
audit hashes, while equivalent economic fields and event counts remain unchanged:
simultaneous 27 events, missing-EOD 11, cancelled 11. Normal/cancelled cash accounts
reconcile; missing-EOD retains the unresolved position and HALT. These are synthetic
mechanics, not genuine performance or risk approval. Genuine readiness remains NO_GO.
