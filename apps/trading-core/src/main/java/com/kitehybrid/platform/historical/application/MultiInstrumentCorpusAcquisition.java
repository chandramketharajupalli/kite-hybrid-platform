package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.HistoricalFingerprint;
import java.util.*;

/** Sequential composition only; restart/replay/persistence remain in the existing acquisition. */
public final class MultiInstrumentCorpusAcquisition {
    private final HistoricalCorpusAcquisition acquisition;

    public MultiInstrumentCorpusAcquisition(HistoricalCorpusAcquisition acquisition) {
        this.acquisition = Objects.requireNonNull(acquisition);
    }

    public record Result(String planFingerprint, List<HistoricalCorpusAcquisition.Result> members,
                         int providerCalls, int inserted, String aggregateFingerprint) {
        public Result { members = List.copyOf(members); }
    }

    public Result acquire(MultiInstrumentCorpusPlan plan) {
        // Retained signature fails closed. There is no legacy path around certification.
        return acquire(plan, null, null);
    }

    public Result acquire(MultiInstrumentCorpusPlan plan, String policyArtifact, String certificateArtifact) {
        Objects.requireNonNull(plan);
        HistoricalContinuityGate.require(plan, policyArtifact, certificateArtifact);
        var results = new ArrayList<HistoricalCorpusAcquisition.Result>();
        int calls = 0, inserted = 0;
        var content = new StringBuilder("multi-instrument-acquisition-v1\n")
                .append(plan.fingerprint()).append('\n');
        for (var member : plan.members()) {
            // Failure propagates. Earlier committed sessions survive; no partial universe result.
            var result = acquisition.acquire(member);
            if (!result.planFingerprint().equals(member.fingerprint())
                    || result.providerCalls() < 0 || result.providerCalls() > member.maximumRequests()
                    || result.inserted() < 0 || result.sessions().size() != member.chunks().size())
                throw new IllegalStateException("MULTI_MEMBER_ACQUISITION_MISMATCH");
            calls += result.providerCalls();
            inserted += result.inserted();
            if (calls > plan.maximumRequests()) throw new IllegalStateException("MULTI_REQUEST_BUDGET_EXCEEDED");
            content.append(member.instrumentId().value()).append('|').append(result.contentFingerprint()).append('\n');
            results.add(result);
        }
        return new Result(plan.fingerprint(), results, calls, inserted,
                HistoricalFingerprint.sha256(content.toString()));
    }
}
