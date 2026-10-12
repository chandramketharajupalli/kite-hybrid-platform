-- Isolated historical opt-in location only; no operational tables are touched.
ALTER TABLE trading.historical_ingestion_chunks DROP CONSTRAINT historical_ingestion_chunks_bar_interval_check;
ALTER TABLE trading.historical_ingestion_chunks ADD CHECK (bar_interval IN ('MINUTE','FIVE_MINUTE'));
ALTER TABLE trading.historical_bars DROP CONSTRAINT historical_bars_bar_interval_check;
ALTER TABLE trading.historical_bars ADD CHECK (bar_interval IN ('MINUTE','FIVE_MINUTE'));
ALTER TABLE trading.historical_bars ADD CHECK (
    bar_interval <> 'FIVE_MINUTE' OR EXTRACT(EPOCH FROM start_time)::NUMERIC % 300 = 0
);
ALTER TABLE trading.historical_ingestion_chunks ADD COLUMN source_instrument_token VARCHAR(128);
ALTER TABLE trading.historical_ingestion_chunks ADD COLUMN reference_as_of TIMESTAMPTZ;
ALTER TABLE trading.historical_ingestion_chunks ADD CHECK (
    (source_instrument_token IS NULL) = (reference_as_of IS NULL)
);
