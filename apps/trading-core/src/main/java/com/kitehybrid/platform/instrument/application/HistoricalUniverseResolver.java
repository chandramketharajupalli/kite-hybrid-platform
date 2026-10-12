package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.*;
import java.time.*;
import java.util.*;

/** Offline evidence review before publishing a validated registry generation. No broker I/O. */
public final class HistoricalUniverseResolver {
    public enum Status { RESOLVED, UNRESOLVED, AMBIGUOUS, INACTIVE, NON_EQUITY }
    public record Symbol(String exchange, String symbol) {
        public Symbol {
            if(exchange==null || symbol==null || exchange.isBlank() || symbol.isBlank())
                throw new IllegalArgumentException("EMPTY_SYMBOL");
        }
        String key() { return exchange.strip().toUpperCase(Locale.ROOT)+"|"+symbol.strip().toUpperCase(Locale.ROOT); }
    }
    public record Resolution(Symbol requested, Status status, Optional<Instrument> instrument,
                             Instant snapshotAt, String snapshotHash, String reason) { }
    public List<Resolution> resolve(List<Symbol> universe, List<Instrument> master, Instant snapshotAt,
                                   String snapshotHash, Instant asOf, List<Instrument> previous,
                                   Set<Symbol> confirmedInactive) {
        Objects.requireNonNull(snapshotAt); Objects.requireNonNull(asOf);
        if(snapshotHash==null || !snapshotHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("MASTER_HASH_REQUIRED");
        var unique=new LinkedHashMap<String,Symbol>();
        universe.forEach(s->unique.putIfAbsent(s.key(),s));
        var result=new ArrayList<Resolution>();
        for(var s:unique.values()) {
            Status status; String reason; Instrument found=null;
            var matches=master.stream().filter(i->new Symbol(i.exchange(),i.tradingSymbol()).key().equals(s.key())).toList();
            if(!s.exchange().strip().equalsIgnoreCase("NSE")) { status=Status.UNRESOLVED;reason="EXCHANGE_MISMATCH"; }
            else if(snapshotAt.isAfter(asOf) || Duration.between(snapshotAt,asOf).compareTo(Duration.ofDays(1))>0) {
                status=Status.UNRESOLVED;reason="STALE_OR_FUTURE_MASTER";
            } else if(confirmedInactive.stream().anyMatch(i->i.key().equals(s.key()))) { status=Status.INACTIVE;reason="DATED_INACTIVE_EVIDENCE"; }
            else if(matches.isEmpty()) { status=Status.UNRESOLVED;reason="ABSENT_NO_RENAME_INFERENCE"; }
            else if(matches.size()!=1) { status=Status.AMBIGUOUS;reason="MULTIPLE_SYMBOL_MATCHES"; }
            else {
                var candidate=matches.getFirst();
                boolean reused=master.stream().filter(i->i.brokerId().equals(candidate.brokerId())).count()!=1
                    || previous.stream().anyMatch(i->i.brokerId().equals(candidate.brokerId()) && !i.id().equals(candidate.id()));
                boolean changed=previous.stream().anyMatch(i->i.id().equals(candidate.id()) && !i.brokerId().equals(candidate.brokerId()));
                if(!candidate.brokerId().broker().equals("KITE")
                    || !candidate.brokerId().value().matches("[1-9][0-9]{0,9}")
                    || Long.parseLong(candidate.brokerId().value())>4294967295L) {
                    status=Status.UNRESOLVED;reason="INVALID_KITE_TOKEN_EVIDENCE";
                }
                else if(reused || changed) { status=Status.AMBIGUOUS;reason="TOKEN_REUSE_OR_MAPPING_CHANGE"; }
                else if(candidate.type()!=InstrumentType.CASH) { status=Status.NON_EQUITY;reason="NOT_CASH_EQUITY"; }
                else { status=Status.RESOLVED;reason="CURRENT_MAPPING_ONLY_NOT_POINT_IN_TIME_CERTIFICATION";found=candidate; }
            }
            result.add(new Resolution(s,status,Optional.ofNullable(found),snapshotAt,snapshotHash,reason));
        }
        return List.copyOf(result);
    }
}
