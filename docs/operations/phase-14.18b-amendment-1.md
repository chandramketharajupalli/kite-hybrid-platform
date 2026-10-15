# Additive amendment 1: regression contract updates

Original plan SHA-256: 352ed86cb21603a93f99e7ee7deca9976718577d1c24e78d8e1c1eb9cedff8f6. The original plan remains unchanged.

Focused attempt 1: 175 passed, 5 failed. Receipt and JUnit remain external under focused-1. Failures are the old illiquid-exit exception expectation in test_phase1415.py, the old unfinished-entry-volume rejection in test_phase1417.py, and three old summary-v2 assertions in test_phase1418a.py.

Add these three existing test files to the allowed inventory. Preserve their original bytes externally before edits. Replace the exception expectation with an explicit unresolved-position assertion; replace future-volume denial with a timed, fixed-quantity fill and valid EOD closure; assert summary v3 for the three canonical-risk examples. No historical evidence or historical test receipts change. All 1,247 remaining baseline files must be unchanged. The causal policy and five-source implementation scope are unchanged.
