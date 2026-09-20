package com.kitehybrid.platform.health;

import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Optional local operational endpoint; kept separate from process/database readiness. */
@Component
@Endpoint(id = "marketdatastatus")
public final class MarketDataStatusEndpoint {
    private final MarketDataGateway gateway;
    public MarketDataStatusEndpoint(MarketDataGateway gateway) { this.gateway = gateway; }
    @ReadOperation public MarketDataHealth status() { return gateway.health(); }
}
