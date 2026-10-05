package com.kitehybrid.platform.historical.infrastructure;

import com.fasterxml.jackson.databind.node.*;
import com.kitehybrid.platform.historical.application.HistoricalBarRepository;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.domain.*;
import java.time.*;
import java.util.*;

/** Explicit offline export boundary. No acquisition, Spring wiring or database writes. */
public final class HistoricalResearchExporter {
    private final HistoricalBarRepository repository;
    public HistoricalResearchExporter(HistoricalBarRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public String export(HistoricalBarRepository.Dataset pinned, TradingCalendar calendar, Instrument instrument) {
        var data = repository.replay(pinned);
        var query = data.query(); var window = query.window();
        if (!instrument.id().equals(window.instrumentId()) || !instrument.exchange().equals("NSE")
                || instrument.type()!=InstrumentType.CASH || data.bars().isEmpty()
                || !data.contentHash().equals(HistoricalFingerprint.bars(data.bars()))
                || !data.contentHash().equals(pinned.contentHash()) || !query.equals(pinned.query())
                || query.decisionCutoff().isBefore(window.to()) || calendar.days().size()>32
                || !HistoricalQuality.analyze(window,query.decisionCutoff(),data.bars(),calendar).complete())
            throw invalid();
        Instant previous = null;
        for (var bar : data.bars()) {
            if (!window.contains(bar) || bar.endTime().isAfter(query.decisionCutoff())
                    || (previous!=null && !bar.startTime().isAfter(previous))) throw invalid();
            previous=bar.startTime();
        }
        var node=JsonNodeFactory.instance.objectNode();
        node.put("schema_version","HistoricalResearchDataset.v1");
        node.put("instrument_id",instrument.id().value().toString());
        node.put("exchange",instrument.exchange()); node.put("symbol",instrument.tradingSymbol());
        node.put("segment",instrument.segment()); node.put("lot_size",instrument.lotSize());
        node.put("interval",window.interval().name()); node.put("timestamp_semantics","INTERVAL_START");
        node.put("adjustment_policy","UNSPECIFIED_NO_LOCAL_ADJUSTMENTS");
        node.put("from_inclusive",window.from().toString()); node.put("to_exclusive",window.to().toString());
        node.put("decision_cutoff",query.decisionCutoff().toString());
        node.put("dataset_cutoff",query.datasetCutoff().toString()); node.put("content_fingerprint",data.contentHash());
        var cal=node.putObject("calendar");
        cal.put("version",calendar.version()); cal.put("source",calendar.source());
        cal.put("fingerprint",calendar.fingerprint()); var days=cal.putArray("days");
        new TreeMap<>(calendar.days()).forEach((date,day)->{
            var out=days.addObject(); out.put("date",date.toString()); out.put("status",day.status().name());
            var sessions=out.putArray("sessions");
            for(var session:day.sessions()) {
                var s=sessions.addObject(); s.put("open",session.open().toString()); s.put("close",session.close().toString());
            }
        });
        // Stable semantic provenance, not random ingestion row IDs or wall-clock export time.
        var provenance=new TreeMap<String,ObjectNode>();
        for(var id:data.provenanceChunks()) {
            var evidence=repository.evidence(id).orElseThrow(HistoricalResearchExporter::invalid);
            if(!evidence.window().instrumentId().equals(instrument.id())
                    || !evidence.calendarFingerprint().equals(calendar.fingerprint())
                    || !evidence.source().matches("[A-Za-z0-9_.-]{1,80}")
                    || !evidence.sourceVersion().matches("[A-Za-z0-9_.-]{1,80}")
                    || !evidence.referenceFingerprint().matches("[a-f0-9]{64}")) throw invalid();
            var p=JsonNodeFactory.instance.objectNode();
            p.put("source",evidence.source()); p.put("source_version",evidence.sourceVersion());
            p.put("reference_fingerprint",evidence.referenceFingerprint());
            p.put("calendar_fingerprint",evidence.calendarFingerprint()); provenance.put(p.toString(),p);
        }
        if(provenance.isEmpty()) throw invalid();
        var sources=node.putArray("provenance"); provenance.values().forEach(sources::add);
        var bars=node.putArray("bars");
        for(var bar:data.bars()) {
            var b=bars.addObject(); b.put("start",bar.startTime().toString());
            b.put("open",bar.open().toPlainString()); b.put("high",bar.high().toPlainString());
            b.put("low",bar.low().toPlainString()); b.put("close",bar.close().toPlainString());
            b.put("volume",bar.volume());
            if(bar.openInterest().isPresent()) b.put("open_interest",bar.openInterest().orElseThrow().toPlainString());
            else b.putNull("open_interest");
        }
        return node.toString();
    }
    private static HistoricalDataException invalid() {
        return new HistoricalDataException(HistoricalDataException.Reason.INVALID_REQUEST);
    }
}
