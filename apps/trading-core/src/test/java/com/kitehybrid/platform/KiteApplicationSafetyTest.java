package com.kitehybrid.platform;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.health.KiteStatusEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TradingCoreApplication.class, properties = {
        "kite.rest-enabled=false", "kite.api-key=", "kite.access-token="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KiteApplicationSafetyTest {
    @Autowired MockMvc http;
    @Autowired KiteStatusEndpoint kiteStatus;

    @Test void absentKiteIsNotAnApplicationHealthFailureAndNoDiagnosticHttpApiIsExposed() throws Exception {
        http.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        http.perform(get("/actuator/kitestatus")).andExpect(status().isNotFound());
        http.perform(get("/actuator/env")).andExpect(status().isNotFound());
        http.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
        assertThat(kiteStatus.status()).containsEntry("sessionState", "DISABLED")
                .containsEntry("instrumentCount", 0).containsEntry("tradingReady", false);
    }
}
