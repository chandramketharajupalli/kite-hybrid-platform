package com.kitehybrid.platform;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.health.KiteStatusEndpoint;
import com.kitehybrid.platform.health.MarketDataStatusEndpoint;
import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TradingCoreApplication.class, properties = {
        "kite.rest-enabled=false", "kite.api-key=", "kite.access-token="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KiteApplicationSafetyTest {
    @Autowired MockMvc http;
    @Autowired KiteStatusEndpoint kiteStatus;
    @Autowired MarketDataGateway marketData;
    @Autowired MarketDataStatusEndpoint marketDataStatus;

    @Test void absentKiteIsNotAnApplicationHealthFailureAndNoDiagnosticHttpApiIsExposed() throws Exception {
        http.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        http.perform(get("/actuator/kitestatus")).andExpect(status().isNotFound());
        http.perform(get("/actuator/marketdatastatus")).andExpect(status().isNotFound());
        http.perform(post("/api/development/market-data/start").param("exchange", "NSE").param("symbol", "INFY"))
                .andExpect(status().isNotFound());
        http.perform(get("/api/development/market-data/status")).andExpect(status().isNotFound());
        http.perform(get("/actuator/env")).andExpect(status().isNotFound());
        http.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
        assertThat(kiteStatus.status()).containsEntry("sessionState", "DISABLED")
                .containsEntry("instrumentCount", 0).containsEntry("tradingReady", false);
        assertThat(marketData.state()).isEqualTo(MarketDataGateway.State.STOPPED);
        assertThat(marketDataStatus.status().status()).isEqualTo(MarketDataHealth.Status.STOPPED);
        assertThat(marketDataStatus.status().reason()).isEqualTo(MarketDataHealth.Reason.DISABLED);
        assertThat(marketDataStatus.status().framesReceived()).isZero();
    }
}
