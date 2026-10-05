package com.kitehybrid.platform.historical.infrastructure;

import com.kitehybrid.platform.historical.application.HistoricalBarRepository;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import static com.kitehybrid.platform.historical.domain.HistoricalDataException.Reason.*;

/** No broker I/O; uniqueness and conflict comparison are inside a short READ COMMITTED transaction. */
public final class PostgresHistoricalBarRepository implements HistoricalBarRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public PostgresHistoricalBarRepository(JdbcTemplate jdbc) {
        this.jdbc=Objects.requireNonNull(jdbc);
        tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Override public Saved append(Chunk c) {
        try { return tx.execute(status -> appendTransaction(c)); }
        catch(HistoricalDataException safe) { throw safe; }
        catch(RuntimeException unavailable) { throw new HistoricalDataException(STORAGE_UNAVAILABLE); }
    }
    private Saved appendTransaction(Chunk c) {
        var id=UUID.randomUUID(); var hash=HistoricalFingerprint.bars(c.bars());
        jdbc.update("""
            INSERT INTO trading.historical_ingestion_chunks(chunk_id,run_id,instrument_id,bar_interval,
                requested_from,requested_to,chunk_from,chunk_to,source,source_version,adjustment_policy,
                observed_at,ingested_at,closed_bar_cutoff,reference_fingerprint,calendar_fingerprint,
                content_hash,bar_count,inserted_count) VALUES (?,?,?,?,?,?,?,?,?,?,'PROVIDER_UNSPECIFIED',?,?,?,?,?,?,?,0)
            """,id,c.runId(),c.window().instrumentId().value(),c.window().interval().name(),
                Timestamp.from(c.request().from()),Timestamp.from(c.request().to()),Timestamp.from(c.window().from()),
                Timestamp.from(c.window().to()),c.source(),c.sourceVersion(),Timestamp.from(c.observedAt()),
                Timestamp.from(c.ingestedAt()),Timestamp.from(c.closedBarCutoff()),c.referenceFingerprint(),
                c.calendarFingerprint(),hash,c.bars().size());
        int inserted=0;
        for(var b:c.bars()) {
            int n=jdbc.update("""
                INSERT INTO trading.historical_bars(instrument_id,bar_interval,start_time,open,high,low,close,
                    volume,open_interest,first_chunk_id,first_ingested_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (instrument_id,bar_interval,start_time) DO NOTHING
                """, b.instrumentId().value(),b.interval().name(),Timestamp.from(b.startTime()),b.open(),b.high(),
                    b.low(),b.close(),b.volume(),b.openInterest().orElse(null),id,Timestamp.from(c.ingestedAt()));
            if(n==0) {
                var prior=jdbc.queryForObject("SELECT * FROM trading.historical_bars WHERE instrument_id=? AND bar_interval=? AND start_time=?",
                        (rs,row)->bar(rs),b.instrumentId().value(),b.interval().name(),Timestamp.from(b.startTime()));
                if(!b.equals(prior)) throw new HistoricalDataException(CONFLICT);
            }
            inserted+=n;
        }
        jdbc.update("UPDATE trading.historical_ingestion_chunks SET inserted_count=? WHERE chunk_id=?",inserted,id);
        return new Saved(id,inserted,c.bars().size()-inserted,hash);
    }
    @Override public Dataset query(Query q) {
        var bars=new ArrayList<HistoricalBar>(); var chunks=new TreeSet<UUID>();
        try {
            jdbc.query("""
                SELECT * FROM trading.historical_bars WHERE instrument_id=? AND bar_interval=?
                    AND start_time>=? AND start_time<? AND start_time + INTERVAL '1 minute'<=?
                    AND first_ingested_at<=? ORDER BY start_time ASC
                """,rs->{bars.add(bar(rs)); chunks.add(rs.getObject("first_chunk_id",UUID.class));},
                    q.window().instrumentId().value(),q.window().interval().name(),Timestamp.from(q.window().from()),
                    Timestamp.from(q.window().to()),Timestamp.from(q.decisionCutoff()),Timestamp.from(q.datasetCutoff()));
            return new Dataset(q,bars,List.copyOf(chunks),HistoricalFingerprint.bars(bars));
        } catch(DataAccessException unavailable) { throw new HistoricalDataException(STORAGE_UNAVAILABLE); }
    }
    private HistoricalBar bar(ResultSet rs) throws SQLException {
        return new HistoricalBar(new InstrumentId(rs.getObject("instrument_id",UUID.class)),rs.getTimestamp("start_time").toInstant(),
                BarInterval.valueOf(rs.getString("bar_interval")),rs.getBigDecimal("open"),rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),rs.getBigDecimal("close"),rs.getLong("volume"),Optional.ofNullable(rs.getBigDecimal("open_interest")));
    }
    @Override public Optional<Evidence> evidence(UUID chunkId) {
        try {
            return jdbc.query("SELECT * FROM trading.historical_ingestion_chunks WHERE chunk_id=?",(rs,row)->{
                var instrument=new InstrumentId(rs.getObject("instrument_id",UUID.class));
                var interval=BarInterval.valueOf(rs.getString("bar_interval"));
                return new Evidence(chunkId,rs.getObject("run_id",UUID.class),
                        new HistoricalWindow(instrument,interval,rs.getTimestamp("requested_from").toInstant(),rs.getTimestamp("requested_to").toInstant()),
                        new HistoricalWindow(instrument,interval,rs.getTimestamp("chunk_from").toInstant(),rs.getTimestamp("chunk_to").toInstant()),
                        rs.getString("source"),rs.getString("source_version"),rs.getTimestamp("observed_at").toInstant(),
                        rs.getTimestamp("ingested_at").toInstant(),rs.getTimestamp("closed_bar_cutoff").toInstant(),
                        rs.getString("reference_fingerprint"),rs.getString("calendar_fingerprint"),rs.getString("content_hash"),
                        rs.getInt("bar_count"),rs.getInt("inserted_count"));
            },Objects.requireNonNull(chunkId)).stream().findFirst();
        } catch(DataAccessException unavailable) { throw new HistoricalDataException(STORAGE_UNAVAILABLE); }
    }
}
