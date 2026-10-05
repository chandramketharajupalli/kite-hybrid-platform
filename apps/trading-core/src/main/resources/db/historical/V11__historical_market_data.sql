-- Explicit isolated research database only: opt in to classpath:db/historical.
-- Not in the application's default migration location; V10 execution preflight stays unchanged.
CREATE TABLE trading.historical_ingestion_chunks (
    chunk_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval = 'MINUTE'),
    requested_from TIMESTAMPTZ NOT NULL,
    requested_to TIMESTAMPTZ NOT NULL,
    chunk_from TIMESTAMPTZ NOT NULL,
    chunk_to TIMESTAMPTZ NOT NULL,
    source VARCHAR(80) NOT NULL,
    source_version VARCHAR(80) NOT NULL,
    adjustment_policy VARCHAR(32) NOT NULL CHECK (adjustment_policy = 'PROVIDER_UNSPECIFIED'),
    observed_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    closed_bar_cutoff TIMESTAMPTZ NOT NULL,
    reference_fingerprint CHAR(64) NOT NULL,
    calendar_fingerprint CHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    bar_count INTEGER NOT NULL CHECK (bar_count BETWEEN 0 AND 1440),
    inserted_count INTEGER NOT NULL CHECK (inserted_count BETWEEN 0 AND bar_count),
    CHECK (requested_from <= chunk_from AND chunk_from < chunk_to AND chunk_to <= requested_to),
    CHECK (requested_to - requested_from <= INTERVAL '31 days'),
    CHECK (chunk_to - chunk_from <= INTERVAL '1 day'),
    CHECK (closed_bar_cutoff <= observed_at AND observed_at <= ingested_at)
);
CREATE TABLE trading.historical_bars (
    instrument_id UUID NOT NULL,
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval = 'MINUTE'),
    start_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(28,10) NOT NULL CHECK (open > 0),
    high NUMERIC(28,10) NOT NULL CHECK (high > 0),
    low NUMERIC(28,10) NOT NULL CHECK (low > 0),
    close NUMERIC(28,10) NOT NULL CHECK (close > 0),
    volume BIGINT NOT NULL CHECK (volume >= 0),
    open_interest NUMERIC(28,10) CHECK (open_interest >= 0),
    first_chunk_id UUID NOT NULL REFERENCES trading.historical_ingestion_chunks(chunk_id),
    first_ingested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instrument_id, bar_interval, start_time),
    CHECK (high >= open AND high >= close AND high >= low AND low <= open AND low <= close),
    CHECK (EXTRACT(EPOCH FROM start_time)::NUMERIC % 60 = 0)
);
-- Primary key already supports instrument/interval half-open ordered range scans.
