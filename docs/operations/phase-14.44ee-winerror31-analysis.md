# Historical WinError31 and current observations

Original failing call: QueryFullProcessImageNameW, invoked by E-D supervisor.py line85 through image(handle,0,name,byref(size)). Exception: PermissionError, Windows error31. The historical process PID, creation time, executable, parent relationship, state at failure, last successful observation, exit-before-query and ACL involvement were not retained. All remain unknown. Do not infer a race or permissions root cause.

Natural WinError31 was not reproduced in completed observations (NON_REPRODUCIBLE within this bounded qualification). An injected WinError31 fixture exercised the error path; injection is not historical reproduction. Original E-D output matching still does not establish two supervised predecessor replays.

Current corrected qualification encountered a different real failure: active image query returned access denied / WinError5 for PID20416, creation FILETIME134367917340762588. Image identity remained null/unverified. The supervisor recorded PROCESS_ACCESS_DENIED, failed closed, terminated only its owned job and verified the job empty. Final launch exit125, duration4.313seconds. This does not establish why access was denied. Raw partial receipt is qualification-final.command.json in new durable custody.

No original failure receipt, executable, installed package or predecessor supervisor was modified. The first new qualification produced22passes and7fixture-label failures; corrected fixture labels use unique safe names, preserving all earlier receipts. The corrected qualification was interrupted before complete JUnit output; it cannot be counted as a passing suite.
