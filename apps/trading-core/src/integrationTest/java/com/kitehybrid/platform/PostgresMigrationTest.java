package com.kitehybrid.platform;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Test void baselineMigratesAndCanBeValidatedOnRestart() throws Exception {
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()).locations("classpath:db/migration").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
             var query = connection.prepareStatement("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'trading'");
             var rows = query.executeQuery()) {
            assertTrue(rows.next());
        }
    }
}
