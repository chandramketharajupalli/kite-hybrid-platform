package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteOfflineInstrumentMaster;
import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.time.Instant;
import java.util.*;

/** Reuses the unchanged parser and resolver. No CSV export or network input. */
public final class NseMasterAdapter implements GateAOfflinePolicy.MasterValidator {
    private final DatedInstrumentMaster.Provenance provenance;
    private final List<OfflineUniverseResolution.Source> universe;
    private final Instant asOf;
    private List<OfflineUniverseResolution.Row> rows=List.of();
    public NseMasterAdapter(DatedInstrumentMaster.Provenance provenance,
                            List<OfflineUniverseResolution.Source> universe, Instant asOf) {
        if (provenance==null || !provenance.synthetic() || asOf==null || universe==null || universe.isEmpty()
                || universe.size()>1115 || provenance.observedAt().isAfter(asOf)) throw denied();
        this.provenance=provenance; this.universe=List.copyOf(universe); this.asOf=asOf;
    }
    @Override public synchronized boolean valid(byte[] bytes) {
        rows=List.of();
        try {
            if (bytes==null || bytes.length>GateAOfflinePolicy.LIMIT) return false;
            var master=new KiteOfflineInstrumentMaster().importBytes(bytes,provenance);
            var tokens=new HashSet<String>();
            for (var c:master.candidates()) {
                var i=c.instrument();
                if (!"NSE".equals(i.exchange()) || !"CASH".equals(i.segment()) || i.type()!=InstrumentType.CASH
                        || !tokens.add(i.brokerId().value()) || formula(i.tradingSymbol())) return false;
            }
            rows=new OfflineUniverseResolution().resolve(universe,Optional.of(master),asOf,Optional.empty());
            if (rows.stream().anyMatch(r->r.status()==OfflineUniverseResolution.Status.INVALID_SOURCE
                    || r.status()==OfflineUniverseResolution.Status.STALE_MASTER)) { rows=List.of(); return false; }
            // Missing/ambiguous remain ledger rows; never converted to mappings or discarded.
            return true;
        } catch (Exception invalid) { return false; }
    }
    private static boolean formula(String s) { return s==null || s.isBlank() || "=+-@".indexOf(s.strip().charAt(0))>=0; }
    public synchronized List<OfflineUniverseResolution.Row> rows() { return rows; }
    @Override public String toString() { return "NSE_MASTER_ADAPTER_REDACTED"; }
    private static IllegalStateException denied() { return new IllegalStateException("GATE_A_DENIED"); }
}
