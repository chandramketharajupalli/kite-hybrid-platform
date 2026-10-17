# Phase 14.28 standalone offline resource boundary

The supported new entry point is strategy_engine.phase1428.runtime.assess(). It uses
importlib.resources, validates the pinned Phase1428Resources.v1 envelope, two allowlisted
resources and a software inventory before evaluating forty synthetic candles through
the six unchanged strategy implementations. This is a bounded research-library probe,
not a production launcher, order executor or genuine qualification run.

The two packaged resources are data/universe.csv and data/configuration.json. The universe
is an exact byte copy of config/universes/phase14_unique_universe.csv, including CRLF.
Its SHA-256 is 4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729.
The historical 1,140 source rows, 25 duplicate removals, original spelling/order and
1,115 unique enabled NSE members remain authoritative. No normalization or regeneration
was applied. Package-local .gitattributes disables Git text conversion for this copy;
package-local .gitignore allows only the three intentional resource files under data.
The new configuration binds the original strategy, qualification, risk, execution,
monitoring and authority fingerprints. It contains no credential or genuine market data.

The manifest pins resource hashes, types, encodings, counts, symbol ordering and software
inventory; contract.py pins its raw digest. The contract bootstrap is trusted code, not
an independent signature or universal resistance to complete local-authority rollback.
The package loader has no repository, CWD, home or network fallback. Unexpected resource
names, incompatible pins, modified bytes and symlinks fail closed. Read-only resources
and unrelated CWD are supported. Hashes are checked before parsing or synthetic use.

All original source and evidence remain byte-identical. Consequently the historical
hardening.family_binding/verify_identity and evidence replay tools still require their
repository/evidence context. They are not redirected or silently patched. The new
standalone API is validated; full standalone equivalence of those historical research
APIs is NOT established. Do not call the old family-binding API a supported standalone
entry point. No trading methodology or historical execution output changed.

The selected wheel excludes tests, evidence archives, credentials, broker adapters and
launchers. It retains the original distribution metadata, including pydantic-settings;
that declared dependency and python-dotenv are installed as verified file copies for
metadata completeness, but neither credential/configuration module is imported by the
selected runtime. The unused dotenv console launcher is explicitly excluded.
