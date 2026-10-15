# Phase 14.18B execution methodology

Execution policy: Phase1418BCausalExecution.v1. The canonical JSON policy dictionary lives in phase1415/execution.py; its exact sorted compact JSON is SHA-256 hashed. The risk policy remains Phase1418RiskPolicyCanonical.v2; execution assumptions have their own independent identity.

The causal decision at 09:35 IST observes the completed 09:30-09:35 candle and the 09:35 opening-price event. Quantity is fixed at order creation from that information, available cash, predeclared participation, costs, stop distance and risk/exposure constraints. The 09:35-09:40 candle's total volume cannot influence this quantity. Liquidity observation, order creation, quantity decision, earliest eligible execution, opening-price observation, fill confirmation and data cutoff are explicitly recorded. The signal-completion timestamp is the preceding candle end.

Prior-volume participation is a declared conservative proxy. It does not certify the entry candle's realized liquidity. Insufficient proxy volume rejects the requested executor quantity; paper/portfolio calculate a risk-sized integer quantity before reservation. Once reserved, the order fills wholly or is cancelled, with no retroactive resizing. Missing volume is invalid input; zero volume cannot authorize a fill.

Exit prices retain the existing stop-first collision convention: stop gaps use the worse opening price, targets use the conservative target level even after a favorable gap, and EOD uses the final close. Exit eligibility uses completed exit-candle volume, and confirmation time equals its completion. Insufficient exit liquidity retains exposure and halts rather than fabricating a closure.

Historical Phase14.15-14.18A reports remain unchanged. Their entry liquidity assumptions differ and their fingerprints are not valid for replay under this correction. The earlier 09:35 quantity 10 versus 1 diagnostic is preserved externally; corrected examples are additive. The 70% target, 13,380-combination qualification family, strategy versions and genuine-performance separation are unchanged. Synthetic results never qualify genuine opportunities. All operational approvals remain blocked.
