package com.kitehybrid.platform.broker.infrastructure.kite;
import com.kitehybrid.platform.instrument.application.InstrumentMasterProvider;
import com.kitehybrid.platform.instrument.domain.Instrument;
import java.util.List;

public final class KiteInstrumentMasterAdapter implements InstrumentMasterProvider {
    private final KiteRestTransport transport;
    private final KiteInstrumentCsvMapper mapper = new KiteInstrumentCsvMapper();
    KiteInstrumentMasterAdapter(KiteRestTransport transport) { this.transport = transport; }
    @Override public List<Instrument> retrieve() {
        return mapper.map(transport.get(KiteRestTransport.Endpoint.INSTRUMENTS));
    }
}
