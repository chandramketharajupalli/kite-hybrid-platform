package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.TradingProjectionService;
import com.kitehybrid.platform.trading.domain.TradingProjection;
import com.kitehybrid.platform.trading.infrastructure.TradingReadController;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradingReadControllerTest {
    @Test void directHandlersDenyBeforeCallingAnyProjectionIncludingRisk() {
        var projections = mock(TradingProjectionService.class);
        var controller = new TradingReadController(projections);
        for (var response : List.of(controller.instruments(), controller.strategies(),
                controller.qualifications(), controller.riskStatus())) {
            assertEquals(401, response.getStatusCode().value());
            assertEquals("no-store", response.getHeaders().getCacheControl());
        }
        verifyNoInteractions(projections);
    }

    @Test void unavailableEvidenceHasNoFabricatedRowsOrMetrics() {
        for (var kind : TradingProjection.Kind.values()) {
            var response = TradingReadController.render(new TradingProjection.Unavailable(
                    kind, TradingProjection.UnavailableReason.EVIDENCE_UNAVAILABLE));
            assertEquals(503, response.getStatusCode().value());
            var error = assertInstanceOf(TradingReadController.ApiError.class, response.getBody());
            assertEquals("EVIDENCE_UNAVAILABLE", error.code());
            assertNotNull(error.correlationId());
        }
        assertEquals(503, TradingReadController.render(null).getStatusCode().value());
    }

    @Test void syntheticWireMappingNeverGrantsExecution() {
        var evidence = new TradingProjection.Evidence("a".repeat(64), Instant.EPOCH,
                TradingProjection.EvidenceProvenance.SYNTHETIC_FIXTURE);
        var available = new TradingProjection.Available(TradingProjection.Kind.STRATEGIES,
                List.of(), evidence);
        var response = TradingReadController.render(available);
        assertEquals(200, response.getStatusCode().value());
        var body = assertInstanceOf(TradingReadController.ReadResponse.class, response.getBody());
        assertFalse(body.executionAuthorized());
        assertEquals(evidence, body.evidence());
        assertTrue(body.entries().isEmpty());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test void deliveryDependsOnlyOnReadProjectionPort() {
        var constructor = TradingReadController.class.getConstructors()[0];
        assertArrayEquals(new Class<?>[]{TradingProjectionService.class}, constructor.getParameterTypes());
        assertTrue(java.util.Arrays.stream(TradingReadController.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getName().contains("RiskService")
                        || f.getType().getName().contains("broker")));
    }
}
