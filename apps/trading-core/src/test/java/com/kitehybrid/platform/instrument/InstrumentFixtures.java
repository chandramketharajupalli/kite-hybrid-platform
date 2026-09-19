package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

final class InstrumentFixtures {
    private InstrumentFixtures() {}

    static Instrument cash(String token, String symbol) {
        return Instrument.create(new BrokerInstrumentId("KITE", token), symbol, "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    }

    static List<Instrument> universe(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> cash(Integer.toString(i), "SYMBOL" + i)).toList();
    }
}
