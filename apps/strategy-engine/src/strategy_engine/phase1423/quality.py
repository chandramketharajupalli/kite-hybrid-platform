"""Causal reference measurements over independently validated accepted fills."""

from collections import Counter
from datetime import date, datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field, ValidationError, model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money
from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, publish, read_json, sha
from strategy_engine.phase1421.analytics import inventory as upstream_inventory
from strategy_engine.phase1421.analytics import require, symbols, validate

D = Decimal
POLICY = dict(
    version="Phase1423DecisionQuote.v1",
    scope="SYNTHETIC_FIXTURE",
    reference_type="CAUSAL_DECISION_QUOTE",
    cutoff="ACCEPTED_FILL_DECISION",
    buy="(fill-reference)*quantity",
    sell="(reference-fill)*quantity",
    positive="ADVERSE",
    bps="signed_per_share/reference*10000",
    executable_liquidity_claim=False,
    additional_cash_charge=False,
    post_trade_benchmark="REJECTED_BY_CAUSAL_CONVENTION",
)
LIMIT = 50000


class Fill(Frozen):
    fill_id: Hash
    run_id: Hash
    session_date: date
    symbol: str = Field(min_length=1)
    strategy_id: Literal["S01", "S02", "S03", "S04", "S05", "S06"]
    side: Literal["BUY", "SELL"]
    price: Money = Field(gt=0)
    at: Instant
    cutoff: Instant
    quantity: int = Field(gt=0, strict=True)
    fees: Money = Field(ge=0)
    cash_delta: Decimal = Field(allow_inf_nan=False)
    event_hash: Hash


class Reference(Frozen):
    schema_version: Literal["Phase1423Reference.v1"]
    policy_hash: Hash
    evidence_id: Hash
    run_id: Hash
    session_date: date
    symbol: str = Field(min_length=1)
    strategy_id: Literal["S01", "S02", "S03", "S04", "S05", "S06"]
    fill_id: Hash
    side: Literal["BUY", "SELL"]
    reference_type: Literal["CAUSAL_DECISION_QUOTE"]
    reference_price: Money = Field(gt=0)
    observed_at: Instant
    available_at: Instant
    measurement_cutoff: Instant
    fill_price: Money = Field(gt=0)
    fill_at: Instant
    quantity: int = Field(gt=0, strict=True)
    source: Literal["SYNTHETIC_FIXTURE"]
    fingerprint: Hash

    @model_validator(mode="after")
    def verify(self) -> "Reference":
        require(self.policy_hash == sha(canonical(POLICY)), "MEASUREMENT_POLICY_MISMATCH")
        require(
            self.observed_at <= self.available_at <= self.measurement_cutoff <= self.fill_at,
            "REFERENCE_NOT_CAUSALLY_AVAILABLE",
        )
        require(
            self.fill_at.astimezone(NSE).date() == self.session_date
            and self.observed_at.astimezone(NSE).date() == self.session_date,
            "REFERENCE_SESSION",
        )
        require(
            self.fingerprint
            == sha(canonical(self.model_dump(mode="json", exclude={"fingerprint"}))),
            "REFERENCE_FINGERPRINT",
        )
        return self


def seal_reference(values: dict[str, Any]) -> dict[str, Any]:
    """Canonical fixture constructor; validation still occurs at the ingestion boundary."""
    body = dict(values)
    body.pop("fingerprint", None)
    return body | {"fingerprint": sha(canonical(body))}


def fixture_reference(fill: Fill, price: Decimal) -> dict[str, Any]:
    require(price.is_finite() and price > 0, "INVALID_REFERENCE_PRICE")
    body = dict(
        schema_version="Phase1423Reference.v1",
        policy_hash=sha(canonical(POLICY)),
        evidence_id=sha(canonical(dict(fill_id=fill.fill_id, policy=POLICY))),
        run_id=fill.run_id,
        session_date=fill.session_date.isoformat(),
        symbol=fill.symbol,
        strategy_id=fill.strategy_id,
        fill_id=fill.fill_id,
        side=fill.side,
        reference_type="CAUSAL_DECISION_QUOTE",
        reference_price=str(price),
        observed_at=fill.cutoff.isoformat().replace("+00:00", "Z"),
        available_at=fill.cutoff.isoformat().replace("+00:00", "Z"),
        measurement_cutoff=fill.cutoff.isoformat().replace("+00:00", "Z"),
        fill_price=str(fill.price),
        fill_at=fill.at.isoformat().replace("+00:00", "Z"),
        quantity=fill.quantity,
        source="SYNTHETIC_FIXTURE",
    )
    return Reference.model_validate(seal_reference(body)).model_dump(mode="json")


def inventory() -> dict[str, str]:
    directory = ROOT / "apps/strategy-engine/src/strategy_engine/phase1423"
    paths = sorted(directory.glob("*.py"))
    require({p.name for p in paths} == {"__init__.py", "quality.py"}, "SOFTWARE_INVENTORY")
    return dict(
        sorted(
            (
                upstream_inventory()
                | {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in paths}
            ).items()
        )
    )


def accepted_fills(source: dict[str, Any], expected_hash: str) -> tuple[Fill, ...]:
    require(sha(canonical(source)) == expected_hash, "SOURCE_HASH")
    require(len(canonical(source)) <= 64 * 1024 * 1024, "SOURCE_LIMIT")
    outcomes = validate(source)
    fills = []
    event_ids: set[str] = set()
    with localcontext(PRECISION):
        for outcome in outcomes:
            orders = {o["order_id"]: o for o in outcome["reports"]["orders.json"]}
            events = [
                e for s in outcome["reports"]["session-events.json"] for e in s["portfolio_events"]
            ]
            for e in events:
                require(e["event_id"] not in event_ids, "DUPLICATE_ACCEPTED_EVENT")
                event_ids.add(e["event_id"])
                if e["state"] not in {"FILLED", "CLOSED"}:
                    continue
                order = orders[e["order_id"]]
                buy = e["state"] == "FILLED"
                price, qty, fees = D(e["details"]["price"]), order["quantity"], D(e["fees"])
                require(
                    price == D(order["entry" if buy else "exit"])
                    and e["timestamp"] == order["entry_at" if buy else "exit_at"],
                    "ACCEPTED_FILL_LINK",
                )
                at = datetime.fromisoformat(e["timestamp"])
                cutoff = (
                    datetime.fromisoformat(e["details"]["timing"]["data_availability_cutoff"])
                    if buy
                    else at
                )
                require(cutoff == at, "UNSUPPORTED_EXECUTION_CUTOFF")
                cash = -(price * qty + fees) if buy else price * qty - fees
                require(cash == D(e["cash_delta"]), "ACCEPTED_CASH_RECONCILIATION")
                fills.append(
                    Fill(
                        fill_id=e["event_id"],
                        run_id=outcome["binding"]["run_id"],
                        session_date=at.astimezone(NSE).date(),
                        symbol=e["symbol"],
                        strategy_id=e["strategy_id"],
                        side="BUY" if buy else "SELL",
                        price=price,
                        at=at,
                        cutoff=cutoff,
                        quantity=qty,
                        fees=fees,
                        cash_delta=cash,
                        event_hash=e["current_hash"],
                    )
                )
                require(len(fills) <= LIMIT, "FILL_BUDGET")
    require(len({f.fill_id for f in fills}) == len(fills), "DUPLICATE_FILL")
    return tuple(fills)


def measure(fill: Fill, reference: Reference) -> dict[str, str]:
    require(
        (
            reference.run_id,
            reference.session_date,
            reference.symbol,
            reference.strategy_id,
            reference.fill_id,
            reference.side,
            reference.fill_price,
            reference.fill_at,
            reference.quantity,
            reference.measurement_cutoff,
        )
        == (
            fill.run_id,
            fill.session_date,
            fill.symbol,
            fill.strategy_id,
            fill.fill_id,
            fill.side,
            fill.price,
            fill.at,
            fill.quantity,
            fill.cutoff,
        ),
        "FILL_LINKAGE_MISMATCH",
    )
    with localcontext(PRECISION):
        delta = fill.price - reference.reference_price
        if fill.side == "SELL":
            delta = -delta
        signed = delta * fill.quantity
        return dict(
            signed=str(signed),
            absolute=str(abs(signed)),
            per_share=str(delta),
            bps=str(delta / reference.reference_price * 10000),
        )


def attribute(fills: tuple[Fill, ...], records: list[dict[str, Any]]) -> dict[str, Any]:
    """Internal aggregation boundary; public assess() obtains fills from validated outcomes."""
    require(len(fills) <= LIMIT and len(records) <= LIMIT, "MEASUREMENT_BUDGET")
    require(len({f.fill_id for f in fills}) == len(fills), "DUPLICATE_FILL")
    universe = symbols()
    require(all(f.symbol in universe for f in fills), "UNKNOWN_SYMBOL")
    ids = Counter(str(r.get("evidence_id")) for r in records)
    links = Counter(str(r.get("fill_id")) for r in records)
    index = {f.fill_id: f for f in fills}
    accepted: dict[str, dict[str, Any]] = {}
    rejected = []
    for raw in sorted(records, key=lambda r: canonical(r)):
        reason = None
        if ids[str(raw.get("evidence_id"))] != 1:
            reason = "DUPLICATE_REFERENCE_ID"
        elif links[str(raw.get("fill_id"))] != 1:
            reason = "DUPLICATE_FILL_ATTRIBUTION"
        else:
            try:
                ref = Reference.model_validate(raw)
                require(ref.fill_id in index, "UNKNOWN_FILL_ID")
                values = measure(index[ref.fill_id], ref)
                accepted[ref.fill_id] = dict(evidence=ref.model_dump(mode="json"), **values)
            except ValidationError:
                reason = "INVALID_REFERENCE_CONTRACT_OR_CAUSALITY"
            except ValueError as exc:
                reason = str(exc)
        if reason:
            rejected.append(dict(input_hash=sha(canonical(raw)), reason=reason))
    with localcontext(PRECISION):

        def summary(selected: tuple[Fill, ...]) -> dict[str, Any]:
            measured = [accepted[f.fill_id] for f in selected if f.fill_id in accepted]
            covered = len(measured)
            signed = sum((D(m["signed"]) for m in measured), D(0))
            absolute = sum((D(m["absolute"]) for m in measured), D(0))
            complete = bool(selected) and covered == len(selected)
            return dict(
                accepted_fills=len(selected),
                measured_fills=covered,
                not_computable=len(selected) - covered,
                status="COMPUTED" if complete else "PARTIAL" if covered else "NOT_COMPUTABLE",
                signed_total=str(signed) if complete else None,
                absolute_total=str(absolute) if complete else None,
                measured_subset_signed=str(signed) if covered else None,
                measured_subset_absolute=str(absolute) if covered else None,
                coverage=str(D(covered) / len(selected)) if selected else None,
                fees=str(sum((f.fees for f in selected), D(0))),
                cash_delta=str(sum((f.cash_delta for f in selected), D(0))),
                slippage_added_to_cash="0",
            )

        portfolio = summary(fills)
        strategies = [
            dict(strategy_id=s, **summary(tuple(f for f in fills if f.strategy_id == s)))
            for s in (f"S{i:02}" for i in range(1, 7))
        ]
        stocks = [
            dict(symbol=s, **summary(tuple(f for f in fills if f.symbol == s))) for s in universe
        ]
        for table in (strategies, stocks):
            for key in ("measured_subset_absolute", "fees", "cash_delta"):
                require(
                    sum((D(row[key] or "0") for row in table), D(0)) == D(portfolio[key] or "0"),
                    "ATTRIBUTION_RECONCILIATION",
                )
            require(
                sum((D(row["measured_subset_signed"] or "0") for row in table), D(0))
                == D(portfolio["measured_subset_signed"] or "0"),
                "ATTRIBUTION_RECONCILIATION",
            )
            require(
                sum(row["measured_fills"] for row in table) == portfolio["measured_fills"],
                "COVERAGE_RECONCILIATION",
            )
        ledger = [
            dict(
                fill=f.model_dump(mode="json"),
                status="COMPUTED" if f.fill_id in accepted else "NOT_COMPUTABLE",
                measurement=accepted.get(f.fill_id),
            )
            for f in fills
        ]
        return dict(
            reference_evidence_ledger=[accepted[k]["evidence"] for k in sorted(accepted)],
            fill_coverage=ledger,
            strategy_slippage=strategies,
            symbol_slippage=stocks,
            portfolio=portfolio,
            rejections=rejected,
            policy=POLICY,
            policy_hash=sha(canonical(POLICY)),
            scope="SYNTHETIC_FIXTURE",
            executable_reference_claim=False,
            Gate_A="NOT_AUTHORIZED",
            Gate_B="NOT_AUTHORIZED",
            genuine_qualified=0,
            operational="NO_GO",
        )


def assess(
    source: dict[str, Any], expected_hash: str, references: list[dict[str, Any]]
) -> dict[str, Any]:
    fills = accepted_fills(source, expected_hash)
    result = attribute(fills, references)
    return dict(
        schema="Phase1423ExecutionQuality.v1",
        source_hash=expected_hash,
        accepted_fill_hash=sha(canonical([f.model_dump(mode="json") for f in fills])),
        reference_input_hash=sha(canonical(sorted(references, key=canonical))),
        software_hash=sha(canonical(inventory())),
        **result,
    )


def run(
    source_path: Path, source_hash: str, reference_path: Path, reference_hash: str, directory: Path
) -> dict[str, Any]:
    raw, source = read_json(source_path, 64 * 1024 * 1024)
    refs_raw, refs = read_json(reference_path, 16 * 1024 * 1024)
    require(sha(raw) == source_hash and sha(refs_raw) == reference_hash, "INPUT_HASH")
    require(isinstance(source, dict) and isinstance(refs, list), "INPUT_CONTRACT")
    result = assess(source, sha(canonical(source)), refs)
    payload = canonical(result)
    require(len(payload) <= 64 * 1024 * 1024, "OUTPUT_BUDGET")
    directory = external(directory)
    publish(directory / "execution-quality.json", payload)
    receipt = dict(
        schema="Phase1423Publication.v1",
        source_hash=source_hash,
        reference_hash=reference_hash,
        report_hash=sha(payload),
        software_hash=result["software_hash"],
        policy_hash=result["policy_hash"],
    )
    publish(directory / "completion.json", canonical(receipt))
    return receipt
