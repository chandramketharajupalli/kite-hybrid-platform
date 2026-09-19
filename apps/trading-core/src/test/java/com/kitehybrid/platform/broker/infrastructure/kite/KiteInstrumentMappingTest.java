package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KiteInstrumentMappingTest {
    static final String HEADER = "instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange\n";
    static final String CASH = "256,1,ABC,\"ABC, Limited\",100.0,,0,0.05000001,1,EQ,NSE,NSE\n";
    final KiteInstrumentCsvMapper mapper = new KiteInstrumentCsvMapper();

    @Test void cashCsvPreservesDecimalAndUsesPlatformIdentityNotToken() {
        var first = mapper.map(HEADER + CASH).getFirst();
        var remapped = mapper.map(HEADER + CASH.replace("256,1,", "512,1,")).getFirst();
        assertThat(first.id()).isEqualTo(remapped.id());
        assertThat(first.brokerId()).isNotEqualTo(remapped.brokerId());
        assertThat(first.type()).isEqualTo(InstrumentType.CASH);
        assertThat(first.tickSize()).isEqualByComparingTo(new BigDecimal("0.05000001"));
        assertThat(first.expiry()).isEmpty();
        assertThat(first.strike()).isEmpty();
    }
    @Test void derivativesAndIndexHaveExplicitSemantics() {
        var records = mapper.map(HEADER
                + "257,1,ABCFUT,ABC,0,2026-10-29,0,0.05,25,FUT,NFO-FUT,NFO\n"
                + "258,1,ABC100CE,ABC,0,2026-10-29,100.10,0.05,25,CE,NFO-OPT,NFO\n"
                + "259,1,ABC100PE,ABC,0,2026-10-29,100.10,0.05,25,PE,NFO-OPT,NFO\n"
                + "260,1,NIFTY 50,NIFTY,0,,0,0,0,EQ,INDICES,NSE\n");
        assertThat(records.get(0).type()).isEqualTo(InstrumentType.FUTURE);
        assertThat(records.get(0).expiry()).contains(LocalDate.of(2026, 10, 29));
        assertThat(records.get(1).strike().orElseThrow()).isEqualByComparingTo(new BigDecimal("100.10"));
        assertThat(records.get(2).type()).isEqualTo(InstrumentType.PUT_OPTION);
        assertThat(records.get(3).type()).isEqualTo(InstrumentType.INDEX);
        assertThat(records.get(3).lotSize()).isZero();
    }
    @Test void documentedBlankNonOptionStrikeAndMcxFutureAliasAreAccepted() {
        var records = mapper.map(HEADER
                + CASH.replace(",,0,0.05000001", ",,,0.05000001")
                + "900,2,SYNTHFUT,,0,2026-10-29,,1,1,FUT,MCX,MCX\n"
                + "901,3,SYNTHFUT2,,0,2026-10-29,,1,1,FUT,MCX-FUT,MCX\n");
        assertThat(records).hasSize(3);
        assertThat(records).allSatisfy(instrument -> assertThat(instrument.strike()).isEmpty());
        assertThat(records.get(1).type()).isEqualTo(InstrumentType.FUTURE);
        assertThatThrownBy(() -> mapper.map(HEADER
                + "902,3,SYNTHCE,,0,2026-10-29,,1,1,CE,MCX-OPT,MCX\n"))
                .isInstanceOf(BrokerReadException.class);
    }
    @Test void reorderedHeadersAndUnknownColumnsAreSafe() {
        var reordered = "exchange, segment, instrument_type, lot_size, tick_size, strike, expiry, tradingsymbol, instrument_token, future_column\n"
                + "NSE,NSE,EQ,1,0.05,,,ABC,256,ignored\n";
        var instrument = mapper.map(reordered).getFirst();
        assertThat(instrument.tradingSymbol()).isEqualTo("ABC");
        assertThat(instrument.tickSize()).isEqualByComparingTo("0.05");
    }
    static Stream<String> invalidCsv() {
        return Stream.of(
                "", HEADER, HEADER + CASH.replace(",1,EQ,", ",0,EQ,"),
                HEADER + CASH.replace("0.05000001", "0"),
                HEADER + CASH.replace("0.05000001", "NaN"),
                HEADER + CASH.replace("0.05000001", "5e-2"),
                HEADER + CASH.replace(",EQ,NSE,", ",BOGUS,NSE,"),
                HEADER + CASH.replace("256,1,", "0,1,"),
                HEADER + CASH.replace("256,1,", "4294967296,1,"),
                HEADER + CASH.replace("ABC,\"ABC", ",\"ABC"),
                HEADER + CASH.replace(",,0,0.05000001", ",2026-10-29,0,0.05000001"),
                HEADER + CASH.replace(",,0,0.05000001", ",,100,0.05000001"),
                HEADER + CASH.stripTrailing() + ",\n",
                HEADER + CASH.replace(",NSE,NSE", ",NSE"),
                HEADER + CASH + "\n",
                HEADER.replace("instrument_token", "wrong_column") + CASH,
                HEADER.replace("exchange_token", "instrument_token") + CASH,
                HEADER + CASH.replace("\"ABC, Limited\"", "\"unclosed"),
                HEADER + CASH + CASH.replace("0.05000001", "-0.01")
        );
    }
    @ParameterizedTest @MethodSource("invalidCsv")
    void rejectsMalformedCandidateWithoutPartialAcceptance(String body) {
        assertThatThrownBy(() -> mapper.map(body))
                .isInstanceOfSatisfying(BrokerReadException.class,
                        e -> assertThat(e.category()).isEqualTo(BrokerReadException.Category.INVALID_RESPONSE));
    }
    @Test void providerUsesOnlyReadOnlyMasterRouteAndReturnsInternalModels() {
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.kite.trade/instruments"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(HEADER + CASH, new MediaType("text", "csv")));
        var session = new KiteSession(new KiteProperties("dummykey", "", "dummytoken", true));
        var adapter = new KiteInstrumentMasterAdapter(new KiteRestTransport(builder.build(), session));
        assertThat(adapter.retrieve()).hasSize(1);
        server.verify();
    }
}
