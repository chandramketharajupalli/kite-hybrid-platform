"""Deterministic synthetic integration report; explicit test fixture dependency."""

from typing import Any

from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1439 import pipeline as p
from test_phase14 import tariff
from test_phase1439 import NOW, fixture


def report() -> dict[str, Any]:
    mapping, batch, window, life = fixture()
    raw = p.canonical(mapping.model_dump(mode="json"))
    mapping = p.master(raw, p.sha(raw), NOW)
    session = p.Transient(life)
    checkpoint = session.accept(batch, mapping, NOW)
    result = p.research(batch, mapping, NOW, window, tariff(), session)
    return dict(version="Phase1439SyntheticPipelineReport.v1", scope="OFFLINE_SYNTHETIC_ONLY",
                mapping_count=len(mapping.records), mapping_fingerprint=p.fingerprint(mapping),
                source_digest=p.sha(raw), candle_fingerprint=p.fingerprint(batch),
                calendar=batch.calendar.model_dump(mode="json"),
                completeness=p.completeness(batch, mapping, NOW), checkpoint=checkpoint,
                research=result, research_fingerprint=p.sha(p.canonical(result)),
                integration_symbols=[batch.series.symbol], integration_windows_months=[6],
                family_combinations=13380, full_universe_end_to_end=False,
                causal_policy=execution_fingerprint(), cleanup=session.dispose(),
                genuine_retention="UNVERIFIED", genuine_qualified=0, real_kite_requests=0,
                Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED", operational="NO_GO")


if __name__ == "__main__":
    print(p.canonical(report()).decode())
