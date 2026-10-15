# Synthetic provenance round-trip correction

Independent example verification rejected shifted multi-day fixture input with ACQUISITION_BEFORE_COMPLETED_DATA. The fixture used model_copy to move candle/session times without moving acquired_at. This is a test-data provenance error, not a protected-file change; the same gate verified all 1,277 baseline hashes before reaching example deserialization.

Set each synthetic dataset acquisition timestamp to its shifted series as_of time. Add a Day JSON serialization/validation round-trip regression. Preserve the failed preservation/example-verification receipt and the pre-correction example package externally. Regenerate only the current unsealed Phase 14.20 examples, then rerun focused/full tests and fresh evidence verification. No existing source, historical evidence or risk threshold changes are authorized.
