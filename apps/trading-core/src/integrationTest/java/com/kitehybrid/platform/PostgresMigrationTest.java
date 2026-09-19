package com.kitehybrid.platform;

import java.sql.DriverManager;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class PostgresMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static TimeZone originalTimeZone;

    @BeforeAll static void useUtcForJdbcStartup() {
        // pgJDBC sends the JVM default as a separate startup parameter; URL options cannot override it.
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll static void restoreTimeZone() {
        if (originalTimeZone != null) {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test void baselineMigratesAndCanBeValidatedOnRestart() throws Exception {
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()).locations("classpath:db/migration").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
             var query = connection.prepareStatement("SELECT schema_name, current_setting('TimeZone') AS session_timezone FROM information_schema.schemata WHERE schema_name = 'trading'");
             var rows = query.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("UTC", rows.getString("session_timezone"));
        }
    }
}
