package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class PostgresOrderRepositoryTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private JdbcTemplate jdbc;
    private PostgresOrderRepository repository;
    private static TimeZone originalTimeZone;

    @BeforeAll static void useUtcForJdbcStartup() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    @AfterAll static void restoreTimeZone() {
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }

    @BeforeEach void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource); repository = new PostgresOrderRepository(jdbc);
    }

    @Test
    void concurrentSameKeyHasOneDurableWinner() throws Exception {
        var first = record("same-key");
        var second = record("same-key");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Future<OrderRepositoryResult> a = pool.submit(() -> claim(start, first));
            Future<OrderRepositoryResult> b = pool.submit(() -> claim(start, second));
            start.countDown();
            var results = java.util.List.of(a.get(), b.get());
            assertEquals(1, results.stream().filter(r -> r == OrderRepositoryResult.CREATED).count());
            assertEquals(1, results.stream().filter(r -> r == OrderRepositoryResult.EXISTING).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.orders", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.order_idempotency", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void recordSurvivesRepositoryRecreationAndOptimisticVersioning() {
        var original = record("restart-key");
        assertEquals(OrderRepositoryResult.CREATED, claimNow(original));
        var reloaded = new PostgresOrderRepository(jdbc).find(original.id()).orElseThrow();
        assertEquals(original.id(), reloaded.id()); assertEquals(OrderState.VALIDATED, reloaded.state());
        var next = reloaded.withBrokerOrderId("broker-1", reloaded.updatedAt().plusSeconds(1));
        assertTrue(repository.attachBrokerOrderId(reloaded, next));
        assertFalse(repository.attachBrokerOrderId(reloaded, next));
        assertEquals(Optional.of("broker-1"), new PostgresOrderRepository(jdbc).find(original.id()).orElseThrow().brokerOrderId());
    }

    private OrderRepositoryResult claim(CountDownLatch start, OrderRecord record) throws Exception {
        start.await(); return claimNow(record);
    }
    private OrderRepositoryResult claimNow(OrderRecord record) {
        return repository.createIfAbsent(record, "a".repeat(64)) == com.kitehybrid.platform.order.application.OrderRepository.IdempotencyClaim.CREATED
                ? OrderRepositoryResult.CREATED : OrderRepositoryResult.EXISTING;
    }
    private static OrderRecord record(String key) {
        var command = new PlaceOrder(key, new InstrumentId(UUID.randomUUID()), OrderSide.BUY, 1, OrderType.MARKET,
                OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var now = Instant.parse("2026-09-21T05:00:00Z");
        return new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.VALIDATED, Optional.empty(),
                Optional.empty(), now, now, 1);
    }
    private enum OrderRepositoryResult { CREATED, EXISTING }
}
