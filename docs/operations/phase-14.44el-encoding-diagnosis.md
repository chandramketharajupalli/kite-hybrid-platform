# Exact failed source diagnosis

The failed file is D:/Yogendra/offline-validation-evidence/phase-14.44E-K/generation-1/capture.py, SHA25641ed20518c6f9d194a23614c357c77e0a1114987a6c0b62a58965f7757429224.
Repository scripts/research/collect_phase1444ek.py SHA2564c805fd170aafa6eda7fbbbf5d23ea3f1cbf5e7e7ea2b66a8a5311398bc83f27 compiles unchanged and was never reached by the failed launcher.

The failed source begins c3afc2bbc2bf. Strict UTF8 decoding succeeds: first codepoints U+00EF,U+00BB,U+00BF. U+00BB is encoded as c2bb at zero-based byte offsets2-3, source line1column2. This is valid UTF8 text containing an invalid Python syntax token, not malformed UTF8 and not a raw efbbbf UTF8BOM. In-memory compile reproduces SyntaxError invalid character U+00BB; source is not executed. The adaptation history is consistent with BOM bytes decoded under an incompatible locale and then encoded as UTF8; observed bytes establish the token issue, not a universal claim about Unicode source.

Historical tool exit1/command/interpreter path are retained in failed-helper-diagnosis.json with the original K error receipt. No helper stdout/stderr files or process completion receipt existed because parsing failed before execution. None are reconstructed. Current interpreter pin is separately recorded and is not retroactive proof of historical binary identity.

New capture_phase1444el.py is ASCII-only valid UTF8 with no BOM, created from new source without decoding/rewriting old helpers. In-memory compile passes and produces no pyc. No process creation/termination APIs exist. Source identity and static results are external. Containment remains UNVERIFIED; section5 requires non-launching preparation mode.
