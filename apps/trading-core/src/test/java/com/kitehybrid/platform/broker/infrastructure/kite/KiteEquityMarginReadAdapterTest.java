package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.risk.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;
import static com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.*;

class KiteEquityMarginReadAdapterTest {
    static final Instant NOW = Instant.parse("2026-10-10T05:00:00Z");
    static final String BASE = "http://127.0.0.1";
    static final String BODY = KiteTradingReadFixtures.envelope(KiteTradingReadFixtures.SEGMENT);
    static final Instrument REF = Instrument.create(new BrokerInstrumentId("ZERODHA", "123456"), "SYNTHETIC",
            "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    static final OrderMarginQuote.Request REQUEST = new OrderMarginQuote.Request(REF.id(), "NSE", "SYNTHETIC",
            TradingReadTypes.Side.BUY, TradingReadTypes.OrderType.MARKET, TradingReadTypes.Product.INTRADAY,
            TradingReadTypes.Validity.DAY, TradingReadTypes.Variety.REGULAR, 1);
    static final RiskLimits LIMITS = new RiskLimits(true, 10, new BigDecimal("10000"), 10, new BigDecimal("10000"),
            Duration.ofSeconds(5), Duration.ofSeconds(60), new BigDecimal("1.10"), BigDecimal.ONE);
    static OrderMarginQuote quote() { return new OrderMarginQuote(REQUEST, new BigDecimal("2000"),
            new BigDecimal("1.25"), Optional.empty(), NOW); }
    static class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static class Fixture {
        final MutableClock clock = new MutableClock();
        final KiteSession session = new KiteSession(new KiteProperties("syntheticEquityKey", "", "", true), clock);
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final KiteRestTransport transport = new KiteRestTransport(builder.build(), session);
        final KiteEquityMarginReadAdapter adapter = new KiteEquityMarginReadAdapter(transport, session, clock, true);
        final AtomicInteger calls = new AtomicInteger();
        Fixture() { login(); }
        void login() {
            session.install(new KiteAccessToken("syntheticEquityToken", NOW.minusSeconds(1), NOW.plusSeconds(600)));
            session.profileValidated();
        }
        org.springframework.test.web.client.ResponseActions expect() {
            return server.expect(requestTo(BASE + "/user/margins/equity")).andExpect(method(HttpMethod.GET))
                    .andExpect(header("X-Kite-Version", "3"))
                    .andExpect(header("Authorization", "token syntheticEquityKey:syntheticEquityToken"))
                    .andExpect(r -> calls.incrementAndGet());
        }
        Diagnostic diagnostic(KiteEquityMarginReadAdapter.Snapshot snapshot) {
            var observation = adapter.observation(snapshot);
            return new Diagnostic(observation.provenance(), IntradayFundingEvidence.equityObservation(
                    quote(),REQUEST,REF,LIMITS,observation.receivedAt(),NOW,clock.instant(),true,Origin.SYNTHETIC));
        }
    }

    record Diagnostic(KiteEquityMarginReadAdapter.Provenance provenance, CollateralContract contract) {}

    @Test void oneExactGetPreservesDecimalsAndCannotProveEligibility() throws Exception {
        var f = new Fixture();
        f.expect().andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        var snapshot = f.adapter.read();
        assertThat(snapshot.equity().net()).isEqualTo(new BigDecimal("99725.05000000002"));
        assertThat(snapshot.equity().utilised().unrealisedMarkToMarket()).isEqualByComparingTo("-5");
        assertThat(snapshot.receivedAt()).isEqualTo(NOW);
        var diagnostic = f.diagnostic(snapshot);
        assertThat(diagnostic.provenance()).isEqualTo(KiteEquityMarginReadAdapter.Provenance.SYNTHETIC_TRANSPORT);
        assertThat(diagnostic.contract().cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(diagnostic.contract().questions().values()).allMatch(t -> t.status() == Proof.UNKNOWN);
        assertThat(diagnostic.contract().observations().get("aggregateNet").classification()).isEqualTo(Evidence.OBSERVED);
        assertThat(diagnostic.contract().observations().values()).allMatch(v -> v.independentAuthority() == Proof.UNKNOWN);
        assertThat(diagnostic.contract().collateralAssistedReadiness()).isEqualTo(Readiness.NOT_READY);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(json.writeValueAsString(diagnostic) + snapshot).doesNotContain("99725", "245431", "syntheticEquity",
                f.session.executionIdentity().orElseThrow().toString(), "123456");
        assertThat(snapshot.toString()).isEqualTo("EquityMarginSnapshot[REDACTED]");
        assertThat(f.calls).hasValue(1); f.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"disabled", "missing", "unverified", "expired"})
    void deniedBeforeHttp(String fault) {
        var f = new Fixture();
        var adapter = fault.equals("disabled") ? new KiteEquityMarginReadAdapter(f.transport,f.session,f.clock) : f.adapter;
        if (fault.equals("missing")) f.session.clear();
        if (fault.equals("unverified")) f.session.install(new KiteAccessToken("syntheticUnverified", NOW, NOW.plusSeconds(60)));
        if (fault.equals("expired")) f.clock.now = NOW.plusSeconds(600);
        assertThat(assertThrows(BrokerReadException.class,adapter::read).category())
                .isEqualTo(fault.equals("disabled") ? CONFIGURATION : AUTHENTICATION);
        assertThat(f.calls).hasValue(0); f.server.verify();
    }

    @Test void mismatchedSessionCannotBePairedWithAnotherTransport() {
        var first = new Fixture(); var other = new Fixture();
        assertThat(assertThrows(BrokerReadException.class, () ->
                new KiteEquityMarginReadAdapter(first.transport,other.session,other.clock,true)).category()).isEqualTo(CONFIGURATION);
        first.server.verify(); other.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"empty", "null", "array", "malformed", "missing", "null-field", "string",
            "bool", "duplicate", "trailing", "segments", "commodity", "both", "label", "null-label", "nan", "huge", "oversized", "error"})
    void malformedOrAmbiguousEquityResponseFailsOnceWithoutLeaking(String fault) {
        var f = new Fixture();
        String body = switch (fault) {
            case "empty" -> ""; case "null" -> "null"; case "array" -> "[]"; case "malformed" -> "{syntheticUpstreamSecret";
            case "missing" -> BODY.replace("\"cash\":245431.6,", "");
            case "null-field" -> BODY.replace("245431.6", "null");
            case "string" -> BODY.replace("245431.6", "\"syntheticUpstreamSecret\"");
            case "bool" -> BODY.replace("\"enabled\":true", "\"enabled\":1");
            case "duplicate" -> BODY.replace("\"enabled\":true", "\"enabled\":true,\"enabled\":false");
            case "trailing" -> BODY + "{}";
            case "segments" -> KiteTradingReadFixtures.envelope("{\"segments\":{\"equity\":"+KiteTradingReadFixtures.SEGMENT+"}}");
            case "commodity" -> KiteTradingReadFixtures.envelope("{\"commodity\":"+KiteTradingReadFixtures.SEGMENT+"}");
            case "both" -> KiteTradingReadFixtures.MARGINS;
            case "label" -> BODY.replace("\"enabled\"", "\"segment\":\"COMMODITY\",\"enabled\"");
            case "null-label" -> BODY.replace("\"enabled\"", "\"segment\":null,\"enabled\"");
            case "nan" -> BODY.replace("245431.6", "NaN");
            case "huge" -> BODY.replace("245431.6", "1e30");
            case "oversized" -> " ".repeat(65537);
            case "error" -> "{\"status\":\"error\",\"message\":\"syntheticUpstreamSecret\"}";
            default -> throw new AssertionError();
        };
        f.expect().andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var failure = assertThrows(BrokerReadException.class,f.adapter::read);
        assertThat(failure.category()).isEqualTo(fault.equals("error") ? BROKER_API : INVALID_RESPONSE);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.toString()).doesNotContain("syntheticUpstreamSecret", "syntheticEquityToken");
        assertThat(f.session.authenticated()).isTrue(); assertThat(f.calls).hasValue(1); f.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"negative-cash", "negative-collateral", "fully-utilised", "unknown-category", "disabled-equity", "equity-label"})
    void observationsIncludingSignedOrUnclassifiedAmountsNeverBecomeFreeCapacity(String fault) {
        var f = new Fixture();
        var body = switch (fault) {
            case "negative-cash" -> BODY.replace("\"cash\":245431.6", "\"cash\":-0.01");
            case "negative-collateral" -> BODY.replace("\"stock_collateral\":0", "\"stock_collateral\":-1000");
            case "fully-utilised" -> BODY.replace("\"collateral\":0", "\"collateral\":1000")
                    .replace("\"stock_collateral\":0", "\"stock_collateral\":1000");
            case "unknown-category" -> BODY.replace("\"enabled\"", "\"category\":\"cashEquivalent\",\"eligible\":true,\"enabled\"");
            case "disabled-equity" -> BODY.replace("\"enabled\":true", "\"enabled\":false");
            case "equity-label" -> BODY.replace("\"enabled\"", "\"segment\":\"EQUITY\",\"enabled\"");
            default -> throw new AssertionError();
        };
        f.expect().andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var result = f.diagnostic(f.adapter.read()).contract();
        assertThat(result.questions().values()).allMatch(t -> t.status() == Proof.UNKNOWN);
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.collateralAssistedReadiness()).isEqualTo(Readiness.NOT_READY); f.server.verify();
    }

    @ParameterizedTest @ValueSource(ints={401,403,429,500,503})
    void errorsAreSanitizedAndOnlyAuthenticationRejectionInvalidatesMemory(int status) {
        var f = new Fixture();
        f.expect().andRespond(withStatus(HttpStatus.valueOf(status)).body("syntheticUpstreamSecret"));
        var failure=assertThrows(BrokerReadException.class,f.adapter::read);
        assertThat(failure.category()).isEqualTo(status==401 || status==403 ? AUTHENTICATION : BROKER_API);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(failure.getCause()).isNull(); assertThat(failure.toString()).doesNotContain("syntheticUpstreamSecret");
        assertThat(f.session.authenticated()).isEqualTo(status!=401 && status!=403);
        assertThat(f.calls).hasValue(1); f.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"timeout", "lost-connectivity", "token-envelope", "malformed-token-envelope"})
    void transportAndTokenEnvelopeBehaviorRetainsExistingTaxonomy(String fault) {
        var f = new Fixture();
        var expectation = f.expect();
        if (fault.equals("timeout")) expectation.andRespond(withException(new java.net.SocketTimeoutException("syntheticUpstreamSecret")));
        else if (fault.equals("lost-connectivity")) expectation.andRespond(withException(new java.net.ConnectException("syntheticUpstreamSecret")));
        else expectation.andRespond(withSuccess("{\"status\":\"error\",\"error_type\":\"TokenException\"}"
                + (fault.equals("malformed-token-envelope") ? "{}" : ""),MediaType.APPLICATION_JSON));
        var failure = assertThrows(BrokerReadException.class,f.adapter::read);
        assertThat(failure.category()).isEqualTo(fault.equals("token-envelope") ? AUTHENTICATION
                : fault.equals("malformed-token-envelope") ? INVALID_RESPONSE : TRANSPORT);
        assertThat(f.session.authenticated()).isEqualTo(!fault.equals("token-envelope"));
        assertThat(failure.getCause()).isNull(); assertThat(f.calls).hasValue(1); f.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"stale", "future", "quote-increase", "request", "reference"})
    void reconciliationRetainsFreshnessAndExactRequestReferenceBinding(String fault) {
        var f = new Fixture(); f.expect().andRespond(withSuccess(BODY,MediaType.APPLICATION_JSON));
        var snapshot = f.adapter.read();
        if (fault.equals("stale")) f.clock.now = NOW.plusSeconds(5);
        if (fault.equals("future")) f.clock.now = NOW.minusNanos(1);
        var expected = fault.equals("request") ? new OrderMarginQuote.Request(REF.id(),"NSE","SYNTHETIC",REQUEST.side(),
                REQUEST.orderType(),REQUEST.product(),REQUEST.validity(),REQUEST.variety(),2) : REQUEST;
        var reference = fault.equals("reference") ? Instrument.create(new BrokerInstrumentId("ZERODHA","654321"),
                "OTHER","NSE","CASH",InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1) : REF;
        var q = fault.equals("quote-increase") ? new OrderMarginQuote(REQUEST,new BigDecimal("2001"),new BigDecimal("1.26"),Optional.empty(),NOW) : quote();
        var observation = f.adapter.observation(snapshot);
        var result = IntradayFundingEvidence.equityObservation(q,expected,reference,LIMITS,
                observation.receivedAt(),NOW,f.clock.instant(),true,Origin.SYNTHETIC);
        assertThat(result.questions().values()).allMatch(t -> t.status() == (fault.equals("stale") ? Proof.STALE
                : fault.equals("quote-increase") ? Proof.UNKNOWN : Proof.CONFLICTING));
        assertThat(result.cashOnlyFunding()).isEqualTo(RiskReason.MIS_MARGIN_UNAVAILABLE);
        assertThat(result.scope().requestFingerprint()).hasSize(64);
        assertThat(result.scope().instrumentMappingFingerprint()).hasSize(64);
        assertThat(f.calls).hasValue(1); f.server.verify();
    }

    @ParameterizedTest @ValueSource(strings={"replacement", "lost-auth", "wrong-owner", "other-account"})
    void snapshotCannotBeReusedAfterSessionChangeOrByAnotherReader(String fault) {
        var f = new Fixture(); f.expect().andRespond(withSuccess(BODY,MediaType.APPLICATION_JSON));
        var snapshot = f.adapter.read();
        if (fault.equals("replacement")) f.login();
        if (fault.equals("lost-auth")) f.session.invalidate();
        var other = new Fixture();
        var reader = fault.equals("other-account") ? other.adapter : fault.equals("wrong-owner")
                ? new KiteEquityMarginReadAdapter(f.transport,f.session,f.clock,true) : f.adapter;
        assertThat(assertThrows(BrokerReadException.class, () -> reader.observation(snapshot)).category())
                .isEqualTo(AUTHENTICATION);
        f.server.verify(); other.server.verify();
    }

    @Test void sessionReplacementDuringResponseCannotPublishOldObservation() {
        var f = new Fixture();
        f.expect().andRespond(r -> { f.login(); return withSuccess(BODY,MediaType.APPLICATION_JSON).createResponse(r); });
        assertThat(assertThrows(BrokerReadException.class,f.adapter::read).category()).isEqualTo(AUTHENTICATION);
        f.server.verify();
    }

    @Test void productionFactoryProvenanceIsConfigurationOnlyAndDefaultAdapterMakesNoCall() {
        var f = new Fixture(); var production = KiteRestTransport.production(f.session);
        assertThat(production.officialOrigin()).isTrue(); assertThat(f.transport.officialOrigin()).isFalse();
        assertThat(assertThrows(BrokerReadException.class, () -> new KiteEquityMarginReadAdapter(production,f.session,f.clock).read())
                .category()).isEqualTo(CONFIGURATION);
    }
}
