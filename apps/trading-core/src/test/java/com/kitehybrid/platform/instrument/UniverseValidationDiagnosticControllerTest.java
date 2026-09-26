package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.infrastructure.UniverseValidationDiagnosticController;
import com.kitehybrid.platform.instrument.infrastructure.UniverseValidationDiagnosticController.UnresolvedEntry;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UniverseValidationDiagnosticControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    @Test void returnsOnlyValidationCountsAndUnresolvedExchangeSymbols(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("universe.csv");
        Files.writeString(file, "symbol,exchange,enabled\nABC,NSE,TRUE\nMISSING,NSE,TRUE\n");
        var instrument = InstrumentFixtures.cash("1", "ABC");
        InstrumentRegistry registry = mock(InstrumentRegistry.class);
        when(registry.snapshot()).thenReturn(com.kitehybrid.platform.instrument.domain.InstrumentSnapshot.validated(List.of(instrument), 1, NOW));
        when(registry.findByExchangeAndSymbol("NSE", "ABC")).thenReturn(Optional.of(instrument));
        when(registry.findByExchangeAndSymbol("NSE", "MISSING")).thenReturn(Optional.empty());
        MockMvc http = MockMvcBuilders.standaloneSetup(new UniverseValidationDiagnosticController(registry, file.toString())).build();

        http.perform(local(get("/api/development/universe/validate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputRows").value(2))
                .andExpect(jsonPath("$.unique").value(2))
                .andExpect(jsonPath("$.duplicatesRemoved").value(0))
                .andExpect(jsonPath("$.enabled").value(2))
                .andExpect(jsonPath("$.disabled").value(0))
                .andExpect(jsonPath("$.resolved").value(1))
                .andExpect(jsonPath("$.unresolved").value(1))
                .andExpect(jsonPath("$.instrument_token").doesNotExist());
        http.perform(local(get("/api/development/universe/unresolved")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exchange").value("NSE"))
                .andExpect(jsonPath("$[0].symbol").value("MISSING"))
                .andExpect(jsonPath("$[0].brokerInstrumentId").doesNotExist());
        verify(registry, times(2)).snapshot();
        verify(registry, times(2)).findByExchangeAndSymbol("NSE", "ABC");
        verify(registry, times(2)).findByExchangeAndSymbol("NSE", "MISSING");
        verifyNoMoreInteractions(registry);
    }

    @Test void rejectsNonLoopbackBeforeReadingRegistry(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("universe.csv");
        Files.writeString(file, "symbol,exchange,enabled\nABC,NSE,TRUE\n");
        var registry = mock(InstrumentRegistry.class);
        MockMvc http = MockMvcBuilders.standaloneSetup(new UniverseValidationDiagnosticController(registry, file.toString())).build();
        http.perform(get("/api/development/universe/validate").with(request -> {
            request.setRemoteAddr("10.0.0.1"); request.setServerName("localhost"); return request;
        })).andExpect(status().isForbidden());
        verifyNoInteractions(registry);
    }

    @Test void lookupReturnsOnlyBoundedPublicReferenceFields(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("universe.csv");
        Files.writeString(file, "symbol,exchange,enabled\nABC,NSE,TRUE\n");
        var instrument = InstrumentFixtures.cash("1", "ABC");
        var registry = mock(InstrumentRegistry.class);
        when(registry.snapshot()).thenReturn(com.kitehybrid.platform.instrument.domain.InstrumentSnapshot.validated(
                List.of(instrument), 1, NOW));
        MockMvc http = MockMvcBuilders.standaloneSetup(new UniverseValidationDiagnosticController(registry, file.toString())).build();

        http.perform(local(get("/api/development/universe/lookup").param("fragment", "ab")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exchange").value("NSE"))
                .andExpect(jsonPath("$[0].symbol").value("ABC"))
                .andExpect(jsonPath("$[0].segment").value("CASH"))
                .andExpect(jsonPath("$[0].instrument_token").doesNotExist())
                .andExpect(jsonPath("$[0].brokerId").doesNotExist())
                .andExpect(jsonPath("$[0].brokerInstrumentId").doesNotExist());
        verify(registry).snapshot();
        verifyNoMoreInteractions(registry);
    }

    @Test void lookupRejectsUnboundedFragments(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("universe.csv");
        Files.writeString(file, "symbol,exchange,enabled\nABC,NSE,TRUE\n");
        var registry = mock(InstrumentRegistry.class);
        MockMvc http = MockMvcBuilders.standaloneSetup(new UniverseValidationDiagnosticController(registry, file.toString())).build();

        http.perform(local(get("/api/development/universe/lookup").param("fragment", "A".repeat(33))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FRAGMENT"));
        verifyNoInteractions(registry);
    }

    @Test void controllerIsDevelopmentOnlyExplicitlyDisabledByDefault() throws Exception {
        var type = UniverseValidationDiagnosticController.class;
        var profile = type.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"development & !production"}, profile.value());
        var guard = type.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertNotNull(guard);
        assertEquals("universe.diagnostic", guard.prefix());
        assertArrayEquals(new String[]{"enabled"}, guard.name());
        assertEquals("true", guard.havingValue());
        assertFalse(guard.matchIfMissing());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder local(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.with(builder -> { builder.setRemoteAddr("127.0.0.1"); builder.setServerName("localhost"); return builder; });
    }
}
