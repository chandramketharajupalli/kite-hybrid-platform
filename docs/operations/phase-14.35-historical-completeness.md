# Historical completeness contract

For each canonical symbol and six/twelve-month window, bind exact dates, mapping epoch, authorized request receipt, source/dataset fingerprints and a verified calendar. Expected intervals derive from actual trading dates, special/partial sessions, holidays, listing dates and evidenced suspensions. Unexplained missing bars remain missing, never zero-volume or interpolated. Genuine calendar coverage and exact date windows are currently unresolved.

Report expected/observed/unique interval counts, missing intervals, duplicates, invalid OHLCV, out-of-session timestamps, ordering/timezone errors and source conflicts. Classify each symbol/window COMPLETE only with all required coverage and provenance; otherwise INCOMPLETE or UNVERIFIED with reasons. Do not treat a suspension as explained without evidence. Preserve actual zero-volume bars separately from absent bars. UTC storage and Asia/Kolkata session interpretation must agree with the frozen calendar methodology.

The synthetic validator checks explicit expected slots; it does not construct exchange calendars, certify listing history, check real API entitlement or establish twelve-month availability. Calendar/action review and window-specific authorization are preconditions. Data acquisition, rates and pagination contracts remain pending verified source evidence; no API calls were made.
