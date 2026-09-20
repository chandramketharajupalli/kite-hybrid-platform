package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KiteTradingReadConfigurationTest {
    private final KiteRestTransport transport = mock(KiteRestTransport.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(KiteTradingReadConfiguration.class)
            .withBean(KiteRestTransport.class, () -> transport)
            .withBean(KiteSession.class, () -> new KiteSession(new KiteProperties("", "", "", false)))
            .withBean(InstrumentRegistry.class, InMemoryInstrumentRegistry::new)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test void absentOrFalseOptInCreatesNoReadPortsOrRequests() {
        context.run(application -> {
            assertThat(application).doesNotHaveBean(BrokerOrdersProvider.class);
            assertThat(application).doesNotHaveBean(BrokerTradesProvider.class);
            assertThat(application).doesNotHaveBean(BrokerPositionsProvider.class);
            assertThat(application).doesNotHaveBean(BrokerHoldingsProvider.class);
            assertThat(application).doesNotHaveBean(BrokerMarginsProvider.class);
        });
        context.withPropertyValues("kite.trading-read.enabled=false").run(application ->
                assertThat(application).doesNotHaveBean(KiteTradingReadAdapter.class));
        verifyNoInteractions(transport);
    }

    @Test void optInWiresFivePortsToOneAdapterWithoutStartingAnyRead() {
        context.withPropertyValues("kite.trading-read.enabled=true").run(application -> {
            assertThat(application).hasSingleBean(KiteTradingReadAdapter.class);
            var adapter = application.getBean(KiteTradingReadAdapter.class);
            assertThat(application.getBean(BrokerOrdersProvider.class)).isSameAs(adapter);
            assertThat(application.getBean(BrokerTradesProvider.class)).isSameAs(adapter);
            assertThat(application.getBean(BrokerPositionsProvider.class)).isSameAs(adapter);
            assertThat(application.getBean(BrokerHoldingsProvider.class)).isSameAs(adapter);
            assertThat(application.getBean(BrokerMarginsProvider.class)).isSameAs(adapter);
            verifyNoInteractions(transport);
            assertThatThrownBy(adapter::orders).isInstanceOfSatisfying(BrokerReadException.class,
                    failure -> assertThat(failure.category()).isEqualTo(BrokerReadException.Category.AUTHENTICATION));
            verifyNoInteractions(transport);
        });
    }
}
