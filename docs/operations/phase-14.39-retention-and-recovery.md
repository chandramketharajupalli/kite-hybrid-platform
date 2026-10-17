# Phase 14.39 retention and recovery

Phase 14.37 and Phase 14.38 decisions retain UNVERIFIED genuine retention authority. No arbitrary temporary period, permanent database, raw candle cache, backup, or persistent symbol-token store is authorized. This implementation accepts only explicitly synthetic provenance and synthetic research purpose. Genuine inputs and persistence reject; there is no automatic file serialization or temporary-file creation.

Lifecycle binds identity, synthetic purpose, starts/expiry and explicit synthetic policy fingerprint. At expiry the in-memory session permanently closes, releases its checkpoint references and rejects later attempts even with an earlier clock value. Disposal receipts describe reference release, not secure memory erasure or deletion of independently retained caller objects. Genuine retention periods remain null. Caller-supplied clocks are trusted test inputs, not tamper-resistant production time.

Checkpoints bind version, batch/source/mapping/candle/lifecycle/retention fingerprints, accepted counts, zero rejected counts and validated synthetic state. Replay must exactly match a freshly recomputed checkpoint from supplied inputs. Missing/altered/partial/unknown-schema evidence is rejected without repair. A duplicate ID in an active session is rejected; changed content, generation or policy cannot replay under its previous checkpoint.

Recovery demonstrates deterministic reconstruction from explicitly supplied synthetic evidence in a fresh memory session. It is not durable crash recovery, a distributed idempotency ledger, rollback protection against replacement of all trusted evidence, or authority to persist market data. No source batch is silently resubmitted. Complete trusted history and independently authorized storage would require separate work.

Synthetic report files intentionally retain synthetic audit outputs in the repository; they are not writes performed by the runtime transient session and contain no genuine market observations. Historical evidence and the untracked Phase 14.22 plan are excluded from any disposal operation.
