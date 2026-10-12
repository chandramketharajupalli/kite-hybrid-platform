package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.*;
import java.util.*;

/** Dry-run only. Does not grant access or replace the existing continuity certification gate. */
public record FiveMinuteAcquisitionPlan(List<HistoricalCorpusPlan> members, int requests,
                                        Duration minimumPacedTime, int retryBudget) {
    public FiveMinuteAcquisitionPlan { members=List.copyOf(members); }
    public static FiveMinuteAcquisitionPlan create(Collection<InstrumentId> resolved, LocalDate from,
                                                    LocalDate to, TradingCalendar calendar) {
        if(resolved.isEmpty() || resolved.size()>10000) throw new IllegalArgumentException("INVALID_UNIVERSE");
        var members=resolved.stream().distinct().sorted(Comparator.comparing(i->i.value().toString()))
            .map(i->new HistoricalCorpusPlan(i,from,to,calendar,366,BarInterval.FIVE_MINUTE)).toList();
        int requests=members.stream().mapToInt(p->p.chunks().size()).sum();
        // Existing process-wide pacing: 1 request/sec, concurrency 1, zero automatic retries.
        return new FiveMinuteAcquisitionPlan(members,requests,Duration.ofSeconds(Math.max(0,requests-1)),0);
    }
}
