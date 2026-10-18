package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitehybrid.platform.trading.domain.TradingFoundationStatus;
import com.kitehybrid.platform.trading.infrastructure.TradingFoundationController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TradingFoundationTest {
    @Test void routeIsAbsentByDefaultAndExplicitFalse() {
        var runner = new WebApplicationContextRunner()
                .withUserConfiguration(TradingFoundationController.class);
        runner.run(context -> assertEquals(0,
                context.getBeansOfType(TradingFoundationController.class).size()));
        runner.withPropertyValues("trading.foundation-api.enabled=false")
                .run(context -> assertEquals(0,
                        context.getBeansOfType(TradingFoundationController.class).size()));
    }

    @Test void metadataOptInCannotGrantExecution() {
        new WebApplicationContextRunner()
                .withUserConfiguration(TradingFoundationController.class)
                .withPropertyValues("trading.foundation-api.enabled=true")
                .run(context -> {
                    var status = context.getBean(TradingFoundationController.class).status().getBody();
                    assertNotNull(status);
                    assertEquals("DISABLED", status.liveOrderPlacement());
                    assertEquals("NOT_IMPLEMENTED", status.paperOrderSubmission());
                    assertEquals("NO_GO", status.operationalReadiness());
                });
    }

    @Test void responseMatchesVersionedMetadataAndDoesNotCache() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TradingFoundationController()).build();
        mvc.perform(get("/api/trading/status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(new ObjectMapper()
                        .writeValueAsString(TradingFoundationStatus.current()), true));
        mvc.perform(get("/api/trading/orders")).andExpect(status().isNotFound());
    }
}
