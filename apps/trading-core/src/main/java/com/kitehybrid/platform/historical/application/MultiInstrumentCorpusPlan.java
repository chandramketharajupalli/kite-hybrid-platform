package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.HistoricalFingerprint;
import java.time.LocalDate;
import java.util.*;

/** Fixed research universe; validates the entire bounded plan before any provider call. */
public record MultiInstrumentCorpusPlan(String universeFingerprint,
                                       List<HistoricalCorpusPlan> members, int maximumRequests) {
    public MultiInstrumentCorpusPlan {
        if (universeFingerprint == null || !universeFingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("UNIVERSE_FINGERPRINT_INVALID");
        members = members.stream().sorted(Comparator.comparing(p -> p.instrumentId().value().toString())).toList();
        if (members.size() < 2 || members.size() > 10
                || members.stream().map(HistoricalCorpusPlan::instrumentId).distinct().count() != members.size())
            throw new IllegalArgumentException("UNIVERSE_MEMBERS_INVALID");
        var first = members.getFirst();
        if (first.endExclusive().isAfter(LocalDate.of(2026, 7, 1)))
            throw new IllegalArgumentException("PHASE120_JULY_OR_LATER_DENIED");
        for (var member : members) {
            if (!member.first().equals(first.first()) || !member.endExclusive().equals(first.endExclusive())
                    || !member.calendar().fingerprint().equals(first.calendar().fingerprint()))
                throw new IllegalArgumentException("COMMON_WINDOW_OR_CALENDAR_MISMATCH");
            member.chunks();
        }
        int sum = members.stream().mapToInt(HistoricalCorpusPlan::maximumRequests).sum();
        if (maximumRequests != sum || maximumRequests > 3660)
            throw new IllegalArgumentException("AGGREGATE_REQUEST_BUDGET_INVALID");
    }

    public int expectedRequests() {
        return members.stream().mapToInt(p -> p.chunks().size()).sum();
    }

    public String fingerprint() {
        var text = new StringBuilder("multi-instrument-corpus-plan-v1\n")
                .append(universeFingerprint).append('\n').append(maximumRequests).append('\n');
        members.forEach(p -> text.append(p.fingerprint()).append('\n'));
        return HistoricalFingerprint.sha256(text.toString());
    }
}
