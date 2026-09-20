package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.marketdata.application.MarketDataException;
import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataInstrumentMappingTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void productionCsvInfyMappingSurvivesSubscriptionAndIncomingTickRoundTrip() throws Exception {
        String csv = """
                instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
                408065,1594,INFY,INFOSYS,1500.00,,0,0.05,1,EQ,NSE,NSE
                """;
        // Start with the real mapper, not a hand-built broker mapping that could repeat an adapter bug.
        var mapped = new KiteInstrumentCsvMapper().map(csv);
        assertThat(mapped).hasSize(1);
        assertThat(mapped.getFirst().brokerId()).isEqualTo(new BrokerInstrumentId("ZERODHA", "408065"));
        try (var rig = new TestRig()) {
            rig.registry.replace(mapped, rig.clock.instant());
            var infy = rig.registry.findByExchangeAndSymbol("NSE", "INFY").orElseThrow();
            assertThat(rig.registry.resolve(infy.brokerId())).contains(infy.id());

            rig.gateway.subscribe(Set.of(infy.id()));
            assertThat(rig.gateway.health().desiredSubscriptions()).isEqualTo(1);
            assertThat(rig.transport.attempts).isEmpty();
            rig.gateway.start();
            rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(MarketDataGateway.State.CONNECTED);
            var commands = rig.transport.latest().socket.sent;
            assertThat(commands).hasSize(2);
            assertThat(JSON.readTree(commands.getFirst())).isEqualTo(JSON.readTree("{\"a\":\"subscribe\",\"v\":[408065]}"));
            assertThat(JSON.readTree(commands.getLast())).isEqualTo(JSON.readTree("{\"a\":\"mode\",\"v\":[\"ltp\",[408065]]}"));

            byte[] binary = ltp(408065, 150025);
            var wire = new KiteMarketDataDecoder().decode(binary).getFirst();
            assertThat(wire.instrumentToken()).isEqualTo(408065L);
            var normalized = new KiteMarketDataNormalizer().normalize(wire, rig.registry.snapshot(), rig.clock.instant());
            assertThat(normalized.instrumentId()).isEqualTo(infy.id());
            assertThat(normalized.lastPrice()).isEqualByComparingTo("1500.25");
            rig.frame(binary);
            rig.worker.runAll();
            assertThat(rig.store.latest(infy.id())).contains(normalized);
            assertThat(rig.gateway.health().status()).isEqualTo(MarketDataHealth.Status.FRESH);
        }
    }

    @ParameterizedTest @MethodSource("invalidMappings")
    void subscriptionRejectsWrongBrokerAndMalformedTokensAtomically(String broker, String token) {
        try (var rig = new TestRig()) {
            var invalid = instrument(broker, token);
            rig.registry.replace(List.of(invalid), rig.clock.instant());
            assertThatThrownBy(() -> rig.gateway.subscribe(Set.of(invalid.id())))
                    .isInstanceOfSatisfying(MarketDataException.class, error ->
                            assertThat(error.reason()).isEqualTo(MarketDataException.Reason.INVALID_BROKER_MAPPING));
            assertThat(rig.gateway.health().desiredSubscriptions()).isZero();
            assertThat(rig.transport.attempts).isEmpty();
        }
    }

    @ParameterizedTest @MethodSource("invalidMappings")
    void connectionRevalidationRetainsInvalidMappingReason(String broker, String token) {
        try (var rig = new TestRig()) {
            rig.gateway.subscribe(Set.of(A.id()));
            var changedMapping = instrument(broker, token);
            assertThat(changedMapping.id()).isEqualTo(A.id());
            rig.registry.replace(List.of(changedMapping), rig.clock.instant());
            rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(MarketDataGateway.State.DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.INVALID_BROKER_MAPPING);
            assertThat(rig.transport.attempts).isEmpty();
        }
    }

    @Test void missingPlatformInstrumentStillRetainsUnresolvedReason() {
        try (var rig = new TestRig()) {
            rig.gateway.subscribe(Set.of(A.id()));
            rig.registry.replace(List.of(B), rig.clock.instant());
            rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(MarketDataGateway.State.DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.UNRESOLVED_INSTRUMENT);
            assertThat(rig.transport.attempts).isEmpty();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"1", "408065", "4294967295"})
    void canonicalMappingRetainsPositiveUnsignedTokenRange(String token) throws Exception {
        try (var rig = new TestRig()) {
            var instrument = instrument(KiteBrokerIdentity.BROKER_ID, token);
            rig.registry.replace(List.of(instrument), rig.clock.instant());
            rig.gateway.subscribe(Set.of(instrument.id()));
            rig.gateway.start(); rig.transport.open();
            assertThat(JSON.readTree(rig.transport.latest().socket.sent.getFirst()).path("v").get(0).longValue())
                    .isEqualTo(Long.parseLong(token));
            var wire = new KiteMarketDataDecoder().decode(ltp(Integer.parseUnsignedInt(token), 100)).getFirst();
            assertThat(new KiteMarketDataNormalizer().normalize(wire, rig.registry.snapshot(), rig.clock.instant()).instrumentId())
                    .isEqualTo(instrument.id());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"KITE", "OTHER"})
    void inboundMappingDoesNotAcceptBrokerAliases(String broker) {
        try (var rig = new TestRig()) {
            rig.registry.replace(List.of(instrument(broker, "408065")), rig.clock.instant());
            var wire = new KiteMarketDataDecoder().decode(ltp(408065, 100)).getFirst();
            assertThatThrownBy(() -> new KiteMarketDataNormalizer().normalize(wire, rig.registry.snapshot(), rig.clock.instant()))
                    .isInstanceOfSatisfying(MarketDataException.class, error ->
                            assertThat(error.reason()).isEqualTo(MarketDataException.Reason.UNRESOLVED_INSTRUMENT));
        }
    }

    static Stream<Arguments> invalidMappings() {
        return Stream.concat(Stream.of("KITE", "OTHER").map(broker -> Arguments.of(broker, "408065")),
                Stream.of("0", "0001", "1.5", "INFY", "4294967296", "99999999999")
                        .map(token -> Arguments.of(KiteBrokerIdentity.BROKER_ID, token)));
    }

    private static Instrument instrument(String broker, String token) {
        return Instrument.create(new BrokerInstrumentId(broker, token), "INFY", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    }
}
