# Phase 14.15 additive plan amendment 1

The frozen plan remains81ddd0c02da72621806cb6ca070d5253c7b076d97b8726efbda4880217bace6c.
Review found UTC-midnight windows span a partial final IST calendar date. The preserved
Phase14 Series calendar requires every covered local date to be classified; using UTC00:00
could incorrectly require an actual trading date to be called closed. Do not weaken Series.

Correction before final acceptance: report date means midnight Asia/Kolkata, represented
as UTC18:30 on the preceding date. Subtract six/twelve calendar months in Asia/Kolkata;
training/validation/holdout boundaries are whole IST days. All instants remain UTC typed.
Tests must cover leap-month clamping, exact IST dates and final-day coverage with no invented
closed days. First focused failures exposed a related synthetic calendar-date mismatch;
its temporary inclusive-date fixture fix is superseded by this whole-day convention.

Preserve preliminary generation-1/pair-results.csv and pair-results.json byte-for-byte:
CSV63b46725ca1decf52a6ea812b0d1b575d6170b378cc6c294416919d2d5a6a749;
JSON16ff163b78ed62a7b47a0d62baec2b60c4df7d97076a0a54de74379f2c9d4926.
These are SUPERSEDED_WINDOW_CONFIGURATION evidence, all13380rows UNAVAILABLE, no market
performance. Corrected authoritative reports go under generation-1/ist-corrected/.
These two additional files plus this amendment are necessary to retain the initial attempt.
No prior phase or source is modified. Existing prohibitions and qualification gates unchanged.
