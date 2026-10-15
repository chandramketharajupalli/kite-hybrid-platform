# Phase 14.15 immutable strategy catalog

Source: explicit user Phase14.15 definitions; no claimed official Streak catalog or proprietary
strategy. All IDs version1,long-only,5minute,2%stop/2%target/EOD default. Exact serialized
fingerprints and parameter metadata are in ist-corrected/pair-results.json registry.

| ID | Name / provenance | Completed-bar entry rule |
|---|---|---|
| S01 | RSI/EMA Reversal; USER_SUPPLIED_DEFINITION | RSI14 crosses above30 AND close crosses above EMA21 on SAME bar |
| S02 | EMA Trend Crossover; RESEARCH_EQUIVALENT | EMA9 crosses above EMA21 |
| S03 | RSI Momentum; RESEARCH_EQUIVALENT | RSI14 crosses above50 AND close>EMA21 |
| S04 | Bollinger Reversion; RESEARCH_EQUIVALENT | Close crosses above lower20-period population band,2SD |
| S05 | VWAP Reclaim; RESEARCH_EQUIVALENT | Close crosses HLC3-volume session VWAP,current volume>0,cumulative>=1000shares |
| S06 | Opening Range Breakout; RESEARCH_EQUIVALENT | After first3bars of09:15IST session,close crosses above their maximum high |

Above cross:previousLeft<=previousRight AND currentLeft>currentRight. Below is inverse.
Thus EMA crossing below close is exactly close crossing above EMA under this convention.
No latching of S01's two conditions on separate bars. S06 requires a cross,not repeated
entries merely for staying above the range. Range low is also calculated,not an extra signal.

EMA seeds with SMA of firstN closes; RSI seeds with firstN differences then Wilder smoothing.
RSI first valid on barN+1; flatRSI50,only gains100. SMA requiresN bars; BB uses population
variance and Decimal sqrt at precision40. VWAP uses cumulative(H+L+C)/3 times volume divided
by cumulative volume; no value with zero cumulative volume. Zero current volume cannot
trigger S05. Session resets explicitly invalidate warmup; early closes may provide too few
bars, yielding no signal. S06 rejects special sessions not starting09:15IST.

Registry identity/version/condition strings are fixed; only risk fractions and S05 volume
parameter can vary explicitly before a run. Changed parameters change fingerprint and
checkpoint configuration. No post-hoc tuning,short strategies or automatic catalogue imports.
Registry models are frozen and duplicate IDs rejected. New semantics require a new version
and evidence, not editing names while retaining a fingerprint.
