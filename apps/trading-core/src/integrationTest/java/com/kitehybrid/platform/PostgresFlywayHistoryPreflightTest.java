package com.kitehybrid.platform;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.operator.application.OperationalReadiness;
import com.kitehybrid.platform.operator.infrastructure.PostgresOperationalReadiness;
import com.kitehybrid.platform.order.application.RuntimeExecutionArming;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import static org.junit.jupiter.api.Assertions.*;

/** Every mutation in this fixture targets a disposable container, never the local deployment. */
@Testcontainers
@Isolated
class PostgresFlywayHistoryPreflightTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withUsername("trading").withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static TimeZone previousZone;
    @BeforeAll static void prepare() {
        previousZone=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("../../scripts/v10-preflight.sql")
                .toAbsolutePath().normalize()), "/tmp/v10-preflight.sql");
    }
    @AfterAll static void restoreZone() { TimeZone.setDefault(previousZone); }

    private record Database(String name, DriverManagerDataSource source, JdbcTemplate jdbc) {}
    private Database empty() {
        String name="history_"+UUID.randomUUID().toString().replace("-", "");
        var admin=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        admin.execute("CREATE DATABASE "+name);
        var source=new DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/"+name),
                postgres.getUsername(),postgres.getPassword());
        return new Database(name,source,new JdbcTemplate(source));
    }
    private Flyway flyway(Database db, String target) {
        return Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").target(target).load();
    }
    private Database discovered() {
        var db=empty();
        assertEquals("public",db.jdbc().queryForObject("SELECT current_schema()",String.class));
        assertEquals(1,flyway(db,"1").migrate().migrationsExecuted);
        // V1 creates the schema matching the role; a fresh connection now resolves $user to trading.
        assertEquals("trading",db.jdbc().queryForObject("SELECT current_schema()",String.class));
        assertEquals(9,flyway(db,"9").migrate().migrationsExecuted);
        assertEquals(1,db.jdbc().queryForObject("SELECT count(*) FROM public.flyway_schema_history",Integer.class));
        assertEquals(9,db.jdbc().queryForObject("SELECT count(*) FROM trading.flyway_schema_history",Integer.class));
        return db;
    }
    private org.testcontainers.containers.Container.ExecResult preflight(Database db, String path, String... variables) throws Exception {
        var args=new ArrayList<String>(List.of("env","PGOPTIONS=-c search_path="+path,"psql","-X","-U",postgres.getUsername(),
                "-d",db.name(),"-v","ON_ERROR_STOP=1"));
        for (var variable:variables) { args.add("-v"); args.add(variable); }
        args.addAll(List.of("-f","/tmp/v10-preflight.sql"));
        return postgres.execInContainer(args.toArray(String[]::new));
    }
    private void ready(Database db) throws Exception {
        var result=preflight(db,"\"$user\",public","expected_history_schema=trading");
        assertEquals(0,result.getExitCode(),result.getStdout()+result.getStderr());
        assertTrue(result.getStdout().contains("V10_PREFLIGHT=READY"));
        assertFalse(result.getStdout().contains("V10_PREFLIGHT=NOT_READY"));
    }
    private void denied(Database db) throws Exception {
        var before=db.jdbc().queryForList("SELECT * FROM trading.flyway_schema_history ORDER BY installed_rank");
        var result=preflight(db,"\"$user\",public");
        assertEquals(3,result.getExitCode(),result.getStdout()+result.getStderr());
        assertTrue(result.getStdout().contains("V10_PREFLIGHT=NOT_READY"));
        assertEquals(before,db.jdbc().queryForList("SELECT * FROM trading.flyway_schema_history ORDER BY installed_rank"));
        assertFalse(new PostgresOperationalReadiness(db.jdbc(),()->true).inspect().databaseReady());
    }

    @Test void discoveredDualHistoryUsesTradingAndProductionStartupUpgradesOnlyTrading() throws Exception {
        var db=discovered();
        var publicBefore=db.jdbc().queryForList("SELECT * FROM public.flyway_schema_history");
        var tradingBefore=db.jdbc().queryForList("SELECT * FROM trading.flyway_schema_history ORDER BY installed_rank");
        ready(db);
        assertEquals(publicBefore,db.jdbc().queryForList("SELECT * FROM public.flyway_schema_history"));
        assertEquals(tradingBefore,db.jdbc().queryForList("SELECT * FROM trading.flyway_schema_history ORDER BY installed_rank"));
        assertEquals("9",flyway(db,"9").info().current().getVersion().toString());
        assertFalse(new PostgresOperationalReadiness(db.jdbc(),()->true).inspect().databaseReady()); // V9 cannot execute.
        for (int restart=0;restart<2;restart++) {
            try (var context=new SpringApplicationBuilder(TradingCoreApplication.class).web(WebApplicationType.NONE).run(
                    "--spring.profiles.active=production", "--spring.datasource.url="+db.source().getUrl(),
                    "--spring.datasource.username="+postgres.getUsername(), "--spring.datasource.password="+postgres.getPassword(),
                    "--kite.rest-enabled=false", "--kite.order-execution.enabled=false", "--kite.operator-control.enabled=false",
                    "--kite.live-test.enabled=false", "--kite.market-data.enabled=false", "--kite.trading-read.enabled=false")) {
                var applicationFlyway=context.getBean(Flyway.class);
                assertNull(applicationFlyway.getConfiguration().getDefaultSchema());
                assertEquals(0,applicationFlyway.getConfiguration().getSchemas().length);
                assertEquals("10",applicationFlyway.info().current().getVersion().toString());
                applicationFlyway.validate();
                assertTrue(context.getBean(OperationalReadiness.class).inspect().databaseReady());
                assertFalse(context.getBean(RuntimeExecutionArming.class).armed(Instant.now()));
            }
        }
        ready(db);
        assertEquals(publicBefore,db.jdbc().queryForList("SELECT * FROM public.flyway_schema_history"));
        assertEquals(10,db.jdbc().queryForObject("SELECT count(*) FROM trading.flyway_schema_history",Integer.class));
    }

    @Test void inverseHistoryNeverFallsBackToHigherPublicVersion() throws Exception {
        var db=empty();
        assertEquals(9,flyway(db,"9").migrate().migrationsExecuted); // One initial connection: public owns V1..V9.
        db.jdbc().execute("CREATE TABLE trading.flyway_schema_history (LIKE public.flyway_schema_history INCLUDING ALL)");
        db.jdbc().execute("INSERT INTO trading.flyway_schema_history SELECT * FROM public.flyway_schema_history WHERE version='1'");
        denied(db);
    }

    @ParameterizedTest @ValueSource(strings={"--spring.flyway.default-schema=trading", "--spring.flyway.schemas=trading,public"})
    void explicitExistingFlywaySettingsAreHonoredOnCleanProductionInstall(String schemaSetting) throws Exception {
        var db=empty();
        try (var context=new SpringApplicationBuilder(TradingCoreApplication.class).web(WebApplicationType.NONE).run(
                "--spring.profiles.active=production", "--spring.datasource.url="+db.source().getUrl(),
                "--spring.datasource.username="+postgres.getUsername(), "--spring.datasource.password="+postgres.getPassword(),
                schemaSetting, "--spring.flyway.table=custom_history",
                "--kite.rest-enabled=false", "--kite.order-execution.enabled=false", "--kite.operator-control.enabled=false",
                "--kite.live-test.enabled=false", "--kite.market-data.enabled=false", "--kite.trading-read.enabled=false")) {
            context.getBean(Flyway.class).validate();
            assertTrue(context.getBean(OperationalReadiness.class).inspect().databaseReady());
            assertFalse(context.getBean(RuntimeExecutionArming.class).armed(Instant.now()));
        }
        var result=preflight(db,"public","flyway_schema=trading","flyway_table=custom_history","expected_history_schema=trading");
        assertEquals(0,result.getExitCode(),result.getStdout()+result.getStderr());
        // Absent overrides must not guess the custom table just because it has V10.
        assertEquals(3,preflight(db,"\"$user\",public").getExitCode());
    }

    @Test void twoCompleteHistoriesStillRequireTheExpectedApplicationSchema() throws Exception {
        var db=discovered();
        db.jdbc().execute("INSERT INTO public.flyway_schema_history SELECT * FROM trading.flyway_schema_history WHERE version<>'1'");
        ready(db); // No ambiguity once connection/default-schema selection is known.
        assertEquals(3,preflight(db,"public","expected_history_schema=trading").getExitCode());
        assertEquals(0,preflight(db,"public","expected_history_schema=public").getExitCode());
        assertEquals(9,db.jdbc().queryForObject("SELECT count(*) FROM public.flyway_schema_history",Integer.class));
        assertEquals(9,db.jdbc().queryForObject("SELECT count(*) FROM trading.flyway_schema_history",Integer.class));
    }

    @Test void missingAuthoritativeHistoryNeverFallsThroughSearchPath() throws Exception {
        var db=empty();
        assertEquals(10,flyway(db,"10").migrate().migrationsExecuted);
        assertEquals("trading",db.jdbc().queryForObject("SELECT current_schema()",String.class));
        assertNotNull(db.jdbc().queryForObject("SELECT to_regclass('flyway_schema_history')",String.class)); // Would fall through!
        assertEquals(3,preflight(db,"\"$user\",public").getExitCode());
        assertFalse(new PostgresOperationalReadiness(db.jdbc(),()->true).inspect().databaseReady());
    }

    @Test void searchPathAndExplicitOverrideMustMatchApplicationAndExpectation() throws Exception {
        var db=discovered();
        assertEquals(3,preflight(db,"public").getExitCode()); // public V1, never choose trading V9 by rank.
        assertEquals(3,preflight(db,"public","expected_history_schema=trading").getExitCode());
        assertEquals(3,preflight(db,"\"$user\",public","flyway_schema=public").getExitCode());
        assertEquals(3,preflight(db,"\"$user\",public","expected_history_schema=public").getExitCode());
        assertEquals(3,preflight(db,"missing_schema").getExitCode());
        assertEquals(3,preflight(db,"public","flyway_schema=missing_schema").getExitCode());
        assertEquals(0,preflight(db,"public","flyway_schema=trading","expected_history_schema=trading").getExitCode());
        assertEquals(1,flyway(db,"10").migrate().migrationsExecuted);
        var publicSource=new DriverManagerDataSource(db.source().getUrl()+"&currentSchema=public",postgres.getUsername(),postgres.getPassword());
        var publicJdbc=new JdbcTemplate(publicSource);
        assertFalse(new PostgresOperationalReadiness(publicJdbc,()->true).inspect().databaseReady());
        assertTrue(new PostgresOperationalReadiness(publicJdbc,()->true,"trading","flyway_schema_history").inspect().databaseReady());
    }

    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9})
    void everyMissingBaselineVersionDenies(int version) throws Exception {
        var db=discovered();
        db.jdbc().update("DELETE FROM trading.flyway_schema_history WHERE version=?",Integer.toString(version));
        denied(db);
    }

    @ParameterizedTest @ValueSource(strings={"failed","duplicate","unexpected","outOfOrder","empty"})
    void corruptV9HistoryDenies(String kind) throws Exception {
        var db=discovered();
        switch (kind) {
            case "failed" -> db.jdbc().execute("UPDATE trading.flyway_schema_history SET success=false WHERE version='5'");
            case "duplicate" -> copyLastVersion(db,"9");
            case "unexpected" -> copyLastVersion(db,"11");
            case "outOfOrder" -> db.jdbc().execute("UPDATE trading.flyway_schema_history SET installed_rank=99 WHERE version='8'");
            case "empty" -> db.jdbc().execute("DELETE FROM trading.flyway_schema_history");
            default -> fail();
        }
        denied(db);
    }
    private void copyLastVersion(Database db,String version) {
        db.jdbc().update("""
                INSERT INTO trading.flyway_schema_history
                SELECT 99,?,description,type,script,checksum,installed_by,installed_on,execution_time,success
                FROM trading.flyway_schema_history WHERE version='9'
                """,version);
    }

    @ParameterizedTest @ValueSource(strings={"script","type","missingBaseline","duplicate","failed","unexpected"})
    void v10RequiresExactSuccessfulCompleteHistory(String kind) throws Exception {
        var db=discovered(); flyway(db,"10").migrate(); ready(db);
        switch (kind) {
            case "script" -> db.jdbc().execute("UPDATE trading.flyway_schema_history SET script='unexpected.sql' WHERE version='10'");
            case "type" -> db.jdbc().execute("UPDATE trading.flyway_schema_history SET type='BASELINE' WHERE version='10'");
            case "missingBaseline" -> db.jdbc().execute("DELETE FROM trading.flyway_schema_history WHERE version='1'");
            case "duplicate" -> copyLastVersion(db,"9");
            case "failed" -> db.jdbc().execute("UPDATE trading.flyway_schema_history SET success=false WHERE version='10'");
            case "unexpected" -> copyLastVersion(db,"11");
            default -> fail();
        }
        denied(db);
    }

    @ParameterizedTest @ValueSource(strings={"missing","nonUnique","column","predicate","included","expression"})
    void v10MalformedIndexDenies(String kind) throws Exception {
        var db=discovered(); flyway(db,"10").migrate();
        db.jdbc().execute("DROP INDEX trading.orders_broker_order_id_unique");
        switch (kind) {
            case "missing" -> { }
            case "nonUnique" -> db.jdbc().execute("CREATE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id) WHERE broker_order_id IS NOT NULL");
            case "column" -> db.jdbc().execute("CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(order_id) WHERE broker_order_id IS NOT NULL");
            case "predicate" -> db.jdbc().execute("CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id) WHERE broker_order_id IS NULL");
            case "included" -> db.jdbc().execute("CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id) INCLUDE(order_id) WHERE broker_order_id IS NOT NULL");
            case "expression" -> db.jdbc().execute("CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(lower(broker_order_id)) WHERE broker_order_id IS NOT NULL");
            default -> fail();
        }
        denied(db);
    }

    @Test void v10InvalidUnreadyIndexDeniesEvenAfterDuplicateTestRowsAreCorrected() throws Exception {
        var db=discovered(); flyway(db,"10").migrate();
        db.jdbc().execute("DROP INDEX trading.orders_broker_order_id_unique");
        for (String key:List.of("one","two")) db.jdbc().update("""
                INSERT INTO trading.orders(order_id,idempotency_key,instrument_id,side,quantity,order_type,
                product,validity,disclosed_quantity,variety,state,broker_order_id,created_at,updated_at,version)
                VALUES(gen_random_uuid(),?,gen_random_uuid(),'BUY',1,'MARKET','DELIVERY','DAY',0,'REGULAR',
                'SUBMITTED','duplicate',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)
                """,key);
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.jdbc().execute(
                "CREATE UNIQUE INDEX CONCURRENTLY orders_broker_order_id_unique ON trading.orders(broker_order_id) WHERE broker_order_id IS NOT NULL"));
        db.jdbc().execute("UPDATE trading.orders SET broker_order_id='different' WHERE idempotency_key='two'");
        assertFalse(db.jdbc().queryForObject("SELECT indisvalid FROM pg_index WHERE indexrelid='trading.orders_broker_order_id_unique'::regclass",Boolean.class));
        assertFalse(db.jdbc().queryForObject("SELECT indisready FROM pg_index WHERE indexrelid='trading.orders_broker_order_id_unique'::regclass",Boolean.class));
        denied(db);
    }
}
