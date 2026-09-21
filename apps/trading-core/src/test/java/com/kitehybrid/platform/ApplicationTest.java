package com.kitehybrid.platform;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.application.BrokerAdapter;
import com.kitehybrid.platform.health.TradingStatusEndpoint;
import com.kitehybrid.platform.order.application.OrderExecutionGateway;
import com.kitehybrid.platform.order.infrastructure.DisabledOrderExecutionGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TradingCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApplicationTest {
    @Autowired ApplicationContext context;
    @Autowired TradingStatusEndpoint tradingStatus;
    @Autowired MockMvc http;
    @Test void contextStartsWithoutDockerAndHasNoExecutableBroker() {
        assertThat(context.getBeansOfType(BrokerAdapter.class)).isEmpty();
        assertThat(context.getBeansOfType(OrderExecutionGateway.class).values())
                .hasSize(1).allMatch(DisabledOrderExecutionGateway.class::isInstance);
        assertThat(tradingStatus.status()).containsEntry("ready", false).containsEntry("emergencyStop", true);
    }
    @Test void applicationCanBeHealthyWhileTradingIsUnavailable() throws Exception {
        http.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        http.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        http.perform(get("/actuator/tradingstatus")).andExpect(status().isNotFound());
        assertThat(tradingStatus.status()).containsEntry("ready", false);
    }
}
