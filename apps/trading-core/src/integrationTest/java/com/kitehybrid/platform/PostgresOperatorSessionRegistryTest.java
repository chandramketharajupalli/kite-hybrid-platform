package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.infrastructure.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fresh external disposable database only; never discovers host databases or starts containers. */
class PostgresOperatorSessionRegistryTest {
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    private static DriverManagerDataSource source;
    private static JdbcTemplate sql;
    private static Flyway flyway;
    @BeforeAll static void migrateDisposableFixture() {
        if(!"true".equals(System.getProperty("evidence.integration.authorized")))throw new IllegalStateException("Authorization required");
        String url=System.getProperty("evidence.integration.url","");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/evidence_fixture_[a-z0-9]+"))
            throw new IllegalStateException("Explicit disposable database required");
        source=new DriverManagerDataSource(url,"evidence_fixture_admin","");sql=new JdbcTemplate(source);sql.setQueryTimeout(5);
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')",Integer.class));
        flyway=Flyway.configure().dataSource(source).defaultSchema("public").schemas("public")
            .locations("classpath:db/migration","classpath:db/historical","classpath:db/evidence","classpath:db/operator")
            .cleanDisabled(true).load();assertEquals(14,flyway.migrate().migrationsExecuted);
        System.out.println("PHASE_M_MIGRATION_14_PUBLIC_HISTORY_PASS");
    }
    private static String digest(){return OperatorSessionLifecycle.hashHandle(UUID.randomUUID().toString().replace("-","")+"x".repeat(16));}
    private static Session session(){return new Session(digest(),new Event("fixture-issuer-"+UUID.randomUUID(),"fixture-subject",
        "fixture-audience",UUID.randomUUID(),NOW,Trust.VERIFIED_OPERATOR_ASSERTION),NOW.plusSeconds(1800),NOW,
        Duration.ofSeconds(600),Set.of(RISK_READ),Set.of("fixture-account"),"v1",false,1);}
    private static PostgresOperatorSessionRegistry registry(){return new PostgresOperatorSessionRegistry(source,CLOCK);}
    @Test void migrationUsesExplicitPublicHistory(){flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals("14",flyway.info().current().getVersion().toString());
        assertEquals(14,sql.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success",Integer.class));
        assertNull(sql.queryForObject("SELECT to_regclass('trading.flyway_schema_history')",String.class));}
    @Test void sessionRoundTripPreservesOriginalGrant(){var s=session();assertTrue(registry().establish(s));
        var observed=registry().find(s.digest()).orElseThrow();assertEquals(s,observed.session());assertEquals(NOW,observed.observedAt());
        assertThrows(UnsupportedOperationException.class,()->observed.session().accounts().add("other"));
        assertFalse(registry().establish(s));assertEquals(s,registry().find(s.digest()).orElseThrow().session());}
    @Test void isolatedConstraintsRejectInvalidRecords() throws Exception {
        var s=session();assertTrue(registry().establish(s));
        String[] columns={"digest","issuer","subject","audience","authentication_event","authenticated_at","absolute_expires_at",
            "last_activity_at","inactivity_seconds","permissions","accounts","policy_version","revoked","revision"};
        // Each copied fixture is valid and revoked; only the labelled column differs from that valid fixture.
        Map<String,String> cases=new LinkedHashMap<>();cases.put("audience","NULL");cases.put("digest","'bad'");
        cases.put("absolute_expires_at","authenticated_at");cases.put("inactivity_seconds","0");
        cases.put("permissions","ARRAY['NOT_A_PERMISSION']::text[]");cases.put("accounts","ARRAY[]::text[]");
        cases.put("revision","0");cases.put("subject","'nonexistent-owner'");
        try(Connection c=source.getConnection()){c.setAutoCommit(false);
            for(var entry:cases.entrySet()){
                var save=c.setSavepoint();List<String> values=new ArrayList<>();
                for(String column:columns)values.add(column.equals(entry.getKey())?entry.getValue():
                    column.equals("digest")?"'"+digest()+"'":column.equals("revoked")?"true":column);
                String insert="INSERT INTO trading.operator_sessions ("+String.join(",",columns)+") SELECT "+String.join(",",values)
                    +" FROM trading.operator_sessions WHERE digest=?";
                SQLException error=assertThrows(SQLException.class,()->{try(var p=c.prepareStatement(insert)){
                    p.setQueryTimeout(5);p.setString(1,s.digest());p.executeUpdate();}},entry.getKey());
                assertEquals(entry.getKey().equals("audience")?"23502":entry.getKey().equals("subject")?"23503":"23514",error.getSQLState());
                // PostgreSQL's constraint diagnostic is fixture-only and contains no real identity data.
                String constraint=error instanceof org.postgresql.util.PSQLException pg && pg.getServerErrorMessage()!=null
                    ?pg.getServerErrorMessage().getConstraint():null;
                System.out.println("PHASE_M_CONSTRAINT field="+entry.getKey()+" sqlstate="+error.getSQLState()+" constraint="+constraint);
                c.rollback(save);try(var p=c.prepareStatement("SELECT 1");var result=p.executeQuery()){assertTrue(result.next());assertEquals(1,result.getInt(1));}
            }
            for(String mutation:List.of("UPDATE trading.operator_sessions SET absolute_expires_at=absolute_expires_at+interval '1 second',revision=revision+1 WHERE digest=?",
                    "DELETE FROM trading.operator_sessions WHERE digest=?","TRUNCATE trading.operator_sessions")){
                var save=c.setSavepoint();SQLException error=assertThrows(SQLException.class,()->{try(var p=c.prepareStatement(mutation)){
                    p.setQueryTimeout(5);if(mutation.contains("?"))p.setString(1,s.digest());p.executeUpdate();}});
                assertEquals("23514",error.getSQLState());System.out.println("PHASE_M_RETENTION_GUARD sqlstate="+error.getSQLState());c.rollback(save);
            }
            c.rollback();
        }
        assertEquals(s,registry().find(s.digest()).orElseThrow().session());
    }
    @Test void rotationIsAtomicAndOldBindingCannotReplay() throws Exception {
        var s=session();assertTrue(registry().establish(s));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        String first=digest(),second=digest();
        try{var a=pool.submit(()->{start.await();return registry().rotate(s.digest(),1,first,NOW);});
            var b=pool.submit(()->{start.await();return registry().rotate(s.digest(),1,second,NOW);});start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
        assertTrue(registry().find(s.digest()).orElseThrow().session().revoked());assertFalse(registry().touch(s.digest(),1,NOW));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions WHERE issuer=? AND NOT revoked",Integer.class,s.event().issuer()));
        var active=registry().find(first).or(()->registry().find(second)).orElseThrow().session();
        assertEquals(s.event(),active.event());assertEquals(s.absoluteExpiresAt(),active.absoluteExpiresAt());
    }
    @Test void concurrentEstablishmentLeavesOneActiveSession() throws Exception {
        var a=session();var b=new Session(digest(),new Event(a.event().issuer(),a.event().subject(),a.event().audience(),UUID.randomUUID(),NOW,
            Trust.VERIFIED_OPERATOR_ASSERTION),a.absoluteExpiresAt(),NOW,a.inactivity(),a.permissions(),a.accounts(),"v1",false,1);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var first=pool.submit(()->{start.await();return registry().establish(a);});var second=pool.submit(()->{start.await();return registry().establish(b);});
            start.countDown();assertTrue(first.get(15,TimeUnit.SECONDS));assertTrue(second.get(15,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions WHERE issuer=? AND NOT revoked",Integer.class,a.event().issuer()));
        assertNotEquals(registry().find(a.digest()).orElseThrow().session().revoked(),registry().find(b.digest()).orElseThrow().session().revoked());
    }
    @Test void revocationPersistsAcrossAdapterInstances(){var s=session();assertTrue(registry().establish(s));assertTrue(registry().revoke(s.digest(),NOW));
        assertTrue(new PostgresOperatorSessionRegistry(source,CLOCK).find(s.digest()).orElseThrow().session().revoked());
        assertTrue(registry().revoke(s.digest(),NOW));assertFalse(registry().rotate(s.digest(),1,digest(),NOW));}
    @Test void activityCannotReviveExpiredOrExtendAbsoluteExpiry(){var s=session();assertTrue(registry().establish(s));
        var later=new PostgresOperatorSessionRegistry(source,Clock.fixed(NOW.plusSeconds(600),ZoneOffset.UTC));
        assertFalse(later.touch(s.digest(),1,NOW.plusSeconds(600)));assertFalse(later.rotate(s.digest(),1,digest(),NOW.plusSeconds(600)));
        var before=new PostgresOperatorSessionRegistry(source,Clock.fixed(NOW.plusSeconds(599),ZoneOffset.UTC));
        assertTrue(before.touch(s.digest(),1,NOW.plusSeconds(599)));var renewed=registry().find(s.digest()).orElseThrow().session();
        assertEquals(s.absoluteExpiresAt(),renewed.absoluteExpiresAt());assertEquals(s.event(),renewed.event());
        assertEquals(s.permissions(),renewed.permissions());assertEquals(s.accounts(),renewed.accounts());}
    @Test void registryFailureAndMalformedRowsFailClosed() throws Exception {
        var unavailable=mock(javax.sql.DataSource.class);when(unavailable.getConnection()).thenThrow(new SQLException("private fixture detail"));
        var broken=new PostgresOperatorSessionRegistry(unavailable,CLOCK);
        assertEquals("Operator registry unavailable",assertThrows(IllegalStateException.class,()->broken.find(digest())).getMessage());
        var s=session();assertTrue(registry().establish(s));String bad=digest();
        // Deliberately malformed text array allowed by SQL's coarse cardinality check; mapper must reject it.
        sql.update("""
            INSERT INTO trading.operator_sessions SELECT ?,issuer,subject,audience,authentication_event,authenticated_at,
            absolute_expires_at,last_activity_at,inactivity_seconds,permissions,ARRAY['']::text[],policy_version,true,revision
            FROM trading.operator_sessions WHERE digest=?
            """,bad,s.digest());
        assertThrows(IllegalStateException.class,()->registry().find(bad));
        assertEquals(s,registry().find(s.digest()).orElseThrow().session());
        System.out.println("PHASE_M_MALFORMED_ROW_FAIL_CLOSED_PASS");
    }
}
