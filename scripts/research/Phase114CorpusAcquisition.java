package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.*;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.nio.file.*;

/** Explicit one-shot review harness. No application startup, HTTP server, execution beans or migrations. */
public final class Phase114CorpusAcquisition {
    static final RuntimeTradingHalt HALT=new RuntimeTradingHalt(()->true);
    static final Map<String,Integer> REQUESTS=new TreeMap<>();
    static final Set<String> GETS=Set.of("/user/profile","/instruments");
    static void output(String stage,Map<String,?> fields) throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("stage",stage);row.put("observedAt",Instant.now().toString());row.putAll(fields);
        System.out.println(new ObjectMapper().writeValueAsString(row));
    }
    static void halted() {
        var s=HALT.status();if(s.runtimeState()!=RuntimeTradingHalt.State.HALTED || !s.startupHalted() || !s.effectiveHalted()) throw new IllegalStateException();
    }
    static void runtimeGuard() {
        for(String key:List.of("KITE_ORDER_EXECUTION_ENABLED","KITE_OPERATOR_CONTROL_ENABLED","KITE_LIVE_TEST_ENABLED","ENABLE_LIVE_TRADING","KITE_MARKET_DATA_ENABLED"))
            if(!"false".equals(System.getenv(key))) throw new IllegalStateException();
        if(!"true".equals(System.getenv("EMERGENCY_STOP")) || !"127.0.0.1".equals(System.getenv("SERVER_ADDRESS"))
                || !"UTC".equals(System.getProperty("user.timezone"))) throw new IllegalStateException();
        halted();
    }
    public static void main(String[] args) throws Exception {
        boolean success=false;
        try { runtimeGuard(); before=databaseEvidence("DB_BEFORE"); success=run(); }
        catch(BrokerReadException e) { output("STOP",Map.of("reason",e.category().name(),"historicalReadiness","NOT_READY")); }
        catch(com.kitehybrid.platform.historical.domain.HistoricalDataException e) { output("STOP",Map.of("reason",e.reason().name(),"historicalReadiness","NOT_READY")); }
        catch(Exception e) { output("STOP",Map.of("reason","REVIEW_UNAVAILABLE","historicalReadiness","NOT_READY")); }
        finally {
            if(before!=null) {
                try {
                    var after=databaseEvidence("DB_AFTER");
                    var unchanged=before.equals(after);
                    output("PRESERVATION",Map.of("tableCountsUnchanged",before.counts.equals(after.counts),
                            "tokenStoreUnchanged",before.tokenDigest.equals(after.tokenDigest),"verified",unchanged));
                    if(!unchanged) throw new IllegalStateException();
                } catch(Exception invalid) { success=false;output("PRESERVATION_FAILURE",Map.of("status","NOT_READY")); }
            }
            halted();output("FINAL",Map.of("runtimeHalt","HALTED","emergencyStop",true,"brokerRequests",REQUESTS,
                "orderMutationRequests",0,"tokenWrites",0,"developmentDatabaseWrites",0,"executionInvocations",0,"subscriptions",0)); }
        if(!success) System.exit(1);
    }
    static boolean run() throws Exception {
        output("RUNTIME",Map.of("runtimeHalt","HALTED","emergencyStop",true,"executionEnabled",false,"operatorEnabled",false,"liveTestEnabled",false,"serverStarted",false));
        var clock=Clock.systemUTC();var env=System.getenv();
        Optional<KiteAccessToken> stored;
        var database=new Properties();database.setProperty("user",env.get("DB_USER"));database.setProperty("password",env.get("DB_PASSWORD"));
        database.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=5000");
        try(var connection=DriverManager.getConnection(env.get("DB_URL"),database)) {
            connection.setReadOnly(true);connection.setAutoCommit(false);
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));jdbc.setQueryTimeout(5);
            if(!"on".equals(jdbc.queryForObject("SHOW transaction_read_only",String.class))) throw new IllegalStateException();
            var store=new PostgresKiteAccessTokenStore(jdbc,KiteAuthenticationAdapter.digest(env.get("KITE_API_KEY")),env.get("KITE_TOKEN_ENCRYPTION_KEY"));
            stored=store.loadCurrent();connection.rollback();
        }
        if(stored.isEmpty() || !stored.orElseThrow().isUsableAt(clock.instant())) {
            output("AUTHENTICATION",Map.of("authenticated",false,"initializationReady",false,"code","KITE_AUTH_REQUIRED","historicalReadiness","NOT_READY"));return false;
        }
        var readOnlyStore=new KiteAccessTokenStore() {
            public Optional<KiteAccessToken> loadCurrent(){return stored;}
            public void save(KiteAccessToken ignored){throw new UnsupportedOperationException();}
            public void clear(){throw new UnsupportedOperationException();}
        };
        var noExchange=new KiteAuthenticationGateway() {
            public String loginUrl(String ignored){throw new UnsupportedOperationException();}
            public KiteAccessToken exchange(String ignored){throw new UnsupportedOperationException();}
        };
        var session=new KiteSession(new KiteProperties(env.get("KITE_API_KEY"),"","",true),clock);
        var client=KiteRestTransport.productionClient().mutate().requestInterceptor((request,body,next)->{
            halted();var uri=request.getURI();var method=request.getMethod().name();
            if(!"https".equals(uri.getScheme()) || !"api.kite.trade".equals(uri.getHost()) || uri.getUserInfo()!=null
                    || uri.getFragment()!=null || uri.getPort()!=-1
                    || !method.equals("GET") || !allowed(uri))
                throw new IllegalStateException("READ_ROUTE_DENIED");
            var key=method+" "+(uri.getPath().startsWith("/instruments/historical/")?"/instruments/historical/[resolved]/minute":uri.getPath());if(REQUESTS.merge(key,1,Integer::sum)>(historicalPath!=null && uri.getPath().equals(historicalPath)?122:1)) throw new IllegalStateException("READ_BUDGET_EXCEEDED");
            return next.execute(request,body);
        }).build();
        var transport=new KiteRestTransport(client,session);var registry=new InMemoryInstrumentRegistry();
        var auth=new KiteAuthenticationUseCase(noExchange,session,readOnlyStore,
                new ValidateBrokerProfileUseCase(new KiteProfileAdapter(transport,session)),
                new RefreshInstrumentRegistryUseCase(new KiteInstrumentMasterAdapter(transport),registry,clock),clock);
        var status=auth.restore();
        output("AUTHENTICATION",Map.of("authenticated",status.authenticated(),"tokenAvailable",status.tokenAvailable(),
                "initializationReady",status.initializationReady(),"executionIdentityAvailable",session.executionIdentity().isPresent(),"code",status.code()));
        if(!status.authenticated() || !status.tokenAvailable() || !status.initializationReady() || session.executionIdentity().isEmpty()) return false;
        halted();var sbin=registry.findByExchangeAndSymbol("NSE","SBIN").orElseThrow();
        var count=registry.snapshot().byId().values().stream().filter(i->i.exchange().equals("NSE") && i.tradingSymbol().equals("SBIN")).count();
        var rows=Files.readAllLines(Path.of("universe.csv")).stream().skip(1).map(s->s.split(",",-1))
                .filter(a->a.length==3 && a[0].strip().equalsIgnoreCase("SBIN") && a[1].strip().equalsIgnoreCase("NSE")).toList();
        var enabled=rows.size()==1 && rows.getFirst()[2].strip().equalsIgnoreCase("true");
        output("REFERENCE",Map.of("matches",count,"universeRows",rows.size(),"enabled",enabled,"instrumentId",sbin.id().value().toString(),
                "exchange",sbin.exchange(),"symbol",sbin.tradingSymbol(),"segment",sbin.segment(),"lotSize",sbin.lotSize(),"tickSize",sbin.tickSize(),"brokerTokenPresent",sbin.brokerId()!=null));
        if(count!=1 || !enabled || sbin.type()!=com.kitehybrid.platform.instrument.domain.InstrumentType.CASH || sbin.lotSize()<1) return false;
        if(!sbin.segment().equals("CASH")) throw new IllegalStateException();
        historicalPath="/instruments/historical/"+sbin.brokerId().value()+"/minute";
        halted();
        require(sbin.id().value().toString().equals("050f94dd-e639-364f-97a6-595594de6543"));
        acquire(transport,registry,sbin,clock);
        return true;
    }
    static String historicalPath;
    static final Set<String> QUERIES=new HashSet<>();
    static boolean allowed(java.net.URI uri) {
        if(GETS.contains(uri.getPath())) return uri.getQuery()==null;
        return historicalPath!=null && historicalPath.equals(uri.getPath())
                && QUERIES.remove(uri.getQuery());
    }
    static void require(boolean condition) { if(!condition) throw new IllegalStateException(); }
    static void acquire(KiteRestTransport transport,InMemoryInstrumentRegistry registry,
                        com.kitehybrid.platform.instrument.domain.Instrument sbin,Clock clock) throws Exception {
        var json=new ObjectMapper();
        var node=json.readTree(Files.readString(Path.of("research/phase-11.4/calendar.json")));
        var dates=new TreeMap<LocalDate,com.kitehybrid.platform.historical.domain.TradingCalendar.Day>();
        for(var row:node.path("days")) {
            var windows=new ArrayList<com.kitehybrid.platform.historical.domain.TradingCalendar.Session>();
            for(var s:row.path("sessions")) windows.add(new com.kitehybrid.platform.historical.domain.TradingCalendar.Session(
                LocalTime.parse(s.path("open").asText()),LocalTime.parse(s.path("close").asText())));
            require(dates.put(LocalDate.parse(row.path("date").asText()),new com.kitehybrid.platform.historical.domain.TradingCalendar.Day(
                com.kitehybrid.platform.historical.domain.TradingCalendar.Status.valueOf(row.path("status").asText()),windows))==null);
        }
        var calendar=new com.kitehybrid.platform.historical.domain.TradingCalendar(node.path("version").asText(),node.path("source").asText(),dates);
        var plan=new com.kitehybrid.platform.historical.application.HistoricalCorpusPlan(sbin.id(),LocalDate.of(2026,2,2),LocalDate.of(2026,8,1),calendar,122);
        var output=Path.of("data/phase114-sbin");Files.createDirectories(output);
        var planRows=new ArrayList<Map<String,Object>>();
        for(var chunk:plan.chunks()) {
            String query="from="+chunk.date()+" 09:15:00&to="+chunk.date()+" 15:30:00&continuous=0&oi=0";
            require(chunk.expectedBars()==375); // Reviewed calendar assertion, not a domain constant.
            QUERIES.add(query);
            planRows.add(Map.of("sequence",chunk.sequence(),"date",chunk.date().toString(),
                "fromInclusive",chunk.window().from().toString(),"toExclusive",chunk.window().to().toString(),
                "providerQuery",query,"expectedBars",chunk.expectedBars()));
        }
        var planText=json.writeValueAsString(Map.of("planFingerprint",plan.fingerprint(),"calendarFingerprint",calendar.fingerprint(),"chunks",planRows));
        writePinned(output.resolve("chunk-plan.json"),planText);
        output("PLAN",Map.of("requests",plan.chunks().size(),"calendarFingerprint",calendar.fingerprint(),"planFingerprint",plan.fingerprint()));
        String researchUrl="jdbc:postgresql://127.0.0.1:55414/phase114_research";
        require(!researchUrl.equals(System.getenv("DB_URL")));
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(researchUrl,"research","");
        var jdbc=new JdbcTemplate(ds);jdbc.setQueryTimeout(10);
        require("phase114_research".equals(jdbc.queryForObject("SELECT current_database()",String.class)));
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/historical").load();
        flyway.migrate();flyway.validate();require("11".equals(flyway.info().current().getVersion().toString()));
        var repo=new com.kitehybrid.platform.historical.infrastructure.PostgresHistoricalBarRepository(jdbc);
        var adapter=new KiteHistoricalAdapter(transport,registry,clock,true,KiteHistoricalAdapter.HistoricalPacing::await);
        com.kitehybrid.platform.historical.application.HistoricalMarketDataProvider checked=window->{
            halted();var batch=adapter.fetch(window);
            var quality=com.kitehybrid.platform.historical.domain.HistoricalQuality.analyze(window,window.to(),batch.bars(),calendar);
            try { output("SESSION_QUALITY",Map.of("date",window.from().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString(),
                "expected",quality.expectedBars(),"received",batch.bars().size(),"gaps",quality.missingBars(),"unexpected",quality.unexpectedBars())); }
            catch(Exception e) { throw new IllegalStateException("EVIDENCE_WRITE_FAILURE"); }
            require(quality.complete() && quality.expectedBars()==batch.bars().size());
            require(new HashSet<>(batch.bars().stream().map(b->b.startTime()).toList()).size()==batch.bars().size());
            return batch;
        };
        var service=new com.kitehybrid.platform.historical.application.HistoricalDataIngestionService(registry,checked,repo,clock);
        var acquisition=new com.kitehybrid.platform.historical.application.HistoricalCorpusAcquisition(service,repo,clock);
        var started=clock.instant();var result=acquisition.acquire(plan);
        var exporter=new com.kitehybrid.platform.historical.infrastructure.HistoricalResearchExporter(repo);
        var manifests=new ArrayList<Map<String,Object>>();
        for(var session:result.sessions()) {
            var target=output.resolve(session.chunk().date()+".json");
            String exported=exporter.export(session.dataset(),session.chunk().calendar(),sbin);
            if(Files.exists(target)) {
                var old=json.readTree(Files.readString(target));var fresh=json.readTree(exported);
                require(old.path("content_fingerprint").equals(fresh.path("content_fingerprint")) && old.path("bars").equals(fresh.path("bars"))
                    && old.path("calendar").equals(fresh.path("calendar")) && old.path("instrument_id").equals(fresh.path("instrument_id")));
            } else Files.writeString(target,exported,StandardOpenOption.CREATE_NEW);
            String pinned=Files.readString(target);
            manifests.add(Map.of("file",target.getFileName().toString(),"contentFingerprint",session.dataset().contentHash(),
                "artifactFingerprint",com.kitehybrid.platform.historical.domain.HistoricalFingerprint.sha256(pinned),"bars",session.dataset().bars().size()));
        }
        var beforeRows=jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Long.class);
        var replay=acquisition.acquire(plan);
        require(replay.providerCalls()==0 && replay.inserted()==0 && result.contentFingerprint().equals(replay.contentFingerprint()));
        require(beforeRows.equals(jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Long.class)));
        for(String table:List.of("orders","risk_decisions","execution_authorizations","kite_access_tokens","reconciliation_decisions"))
            require(jdbc.queryForObject("SELECT count(*) FROM trading."+table,Long.class)==0);
        var manifest=Map.of("schema","HistoricalCorpusManifest.v1","planFingerprint",plan.fingerprint(),
            "calendarFingerprint",calendar.fingerprint(),"contentFingerprint",result.contentFingerprint(),"sessions",manifests,
            "quality","COMPLETE_CANONICAL_SESSIONS","source","KITE","adjustmentPolicy","UNSPECIFIED_NO_LOCAL_ADJUSTMENTS");
        writePinned(output.resolve("manifest.json"),json.writeValueAsString(manifest));
        output("CORPUS",Map.of("sessions",result.sessions().size(),"bars",beforeRows,"providerCalls",result.providerCalls(),
            "replayInserted",replay.inserted(),"replayCalls",replay.providerCalls(),"contentFingerprint",result.contentFingerprint(),
            "acquisitionStartedAt",started.toString(),"acquisitionCompletedAt",clock.instant().toString(),"researchTradingRows",0));
    }
    static void writePinned(Path path,String value) throws Exception {
        if(Files.exists(path)) require(new ObjectMapper().readTree(Files.readString(path)).equals(new ObjectMapper().readTree(value)));
        else Files.writeString(path,value,StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Object> barEvidence(com.kitehybrid.platform.historical.domain.HistoricalBar b) {
        return Map.of("start",b.startTime().toString(),"open",b.open(),"high",b.high(),"low",b.low(),"close",b.close(),"volume",b.volume());
    }
    private record DatabaseEvidence(Map<String,Long> counts,String tokenDigest) {
        @Override public String toString() { return "DatabaseEvidence[withheld]"; }
    }
    private static DatabaseEvidence before;
    static DatabaseEvidence databaseEvidence(String stage) throws Exception {
        halted();var env=System.getenv();var properties=new Properties();
        properties.setProperty("user",env.get("DB_USER"));properties.setProperty("password",env.get("DB_PASSWORD"));
        properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=5000");
        try(var c=DriverManager.getConnection(env.get("DB_URL"),properties)) {
            c.setReadOnly(true);c.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(c,true));jdbc.setQueryTimeout(5);
            if(!"on".equals(jdbc.queryForObject("SHOW transaction_read_only",String.class))) throw new IllegalStateException();
            var counts=new TreeMap<String,Long>();
            for(var table:List.of("orders","order_idempotency","risk_decisions","reconciliation_decisions",
                    "reconciliation_trades","strategy_evaluations","execution_authorizations","kite_login_attempts","kite_access_tokens"))
                counts.put(table,jdbc.queryForObject("SELECT count(*) FROM trading."+table,Long.class));
            // Equality fingerprint stays solely in memory; no ciphertext, nonce, store ID or digest is emitted.
            String fingerprint=jdbc.queryForObject("SELECT md5(COALESCE(string_agg(row_to_json(t)::text, '' ORDER BY store_id), '')) FROM trading.kite_access_tokens t",String.class);
            var tokenRows=jdbc.query("SELECT issued_at,expires_at FROM trading.kite_access_tokens WHERE store_id=?",
                    (row,index)->Map.of("issuedAt",row.getTimestamp(1).toInstant().toString(),
                            "expiresAt",row.getTimestamp(2).toInstant().toString(),
                            "valid",!Instant.now().isBefore(row.getTimestamp(1).toInstant()) && Instant.now().isBefore(row.getTimestamp(2).toInstant())),
                    KiteAuthenticationAdapter.digest(env.get("KITE_API_KEY")));
            c.rollback();
            output(stage,Map.of("transactionReadOnly",true,"tableCounts",counts,"selectedTokenRowCount",tokenRows.size(),"tokenMetadata",tokenRows));
            return new DatabaseEvidence(Map.copyOf(counts),fingerprint);
        }
    }
}

