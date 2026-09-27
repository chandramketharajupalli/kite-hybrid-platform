\set ON_ERROR_STOP on
-- Match the application's connection user/search_path. Never rank candidate histories by version.
-- Optional flyway_schema must match an explicit application default-schema (or first schemas entry).
-- Optional flyway_table must match the application's Flyway table override.
-- expected_history_schema is an assertion, NEVER a selector or fallback.
\if :{?flyway_schema}
\else
  \set flyway_schema ''
\endif
\if :{?flyway_table}
\else
  \set flyway_table flyway_schema_history
\endif
\if :{?expected_history_schema}
\else
  \set expected_history_schema ''
\endif
-- Run before application startup (Flyway migrates automatically). No data repair.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';
SELECT coalesce(nullif(:'flyway_schema',''), current_schema(), '') AS history_schema \gset
SELECT current_database() AS database, current_user AS connection_user,
       current_setting('search_path') AS connection_search_path, current_schema() AS connection_current_schema,
       :'history_schema' AS authoritative_history_schema, :'flyway_table' AS history_table;
SELECT n.nspname AS history_schema, c.relname AS history_table,
       n.nspname=:'history_schema' AS authoritative
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relname=:'flyway_table' AND n.nspname !~ '^pg_' ORDER BY n.nspname;
SELECT :'history_schema'<>'' AND :'history_schema' !~ '^pg_' AND :'history_schema'<>'information_schema'
  AND (:'expected_history_schema'='' OR :'expected_history_schema'=:'history_schema')
  AND EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname=:'history_schema' AND c.relname=:'flyway_table' AND c.relkind='r') AS history_resolved \gset
\if :history_resolved
\else
  \echo V10_PREFLIGHT=NOT_READY AUTHORITATIVE_HISTORY_UNRESOLVED
  SELECT 1 / 0 AS preflight_stop;
\endif
SELECT version AS current_flyway_version, success
  FROM :"history_schema".:"flyway_table" WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;
SELECT count(*) AS duplicate_non_null_broker_id_groups FROM (
  SELECT broker_order_id FROM trading.orders WHERE broker_order_id IS NOT NULL
  GROUP BY broker_order_id HAVING count(*) > 1
) duplicates;
SELECT conname, contype, convalidated FROM pg_constraint WHERE conrelid='trading.orders'::regclass;
SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='trading' AND tablename='orders';
WITH history AS (SELECT * FROM :"history_schema".:"flyway_table"), evidence AS (
  SELECT
    (SELECT version FROM history WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1) AS version,
    (SELECT count(*) FROM history WHERE version IS NOT NULL) AS version_count,
    NOT EXISTS(SELECT 1 FROM history WHERE version IS NOT NULL GROUP BY version HAVING count(*)>1) AS versions_unique,
    NOT EXISTS(SELECT 1 FROM history WHERE version IS NOT NULL AND version NOT IN ('1','2','3','4','5','6','7','8','9','10'))
      AND NOT EXISTS(SELECT 1 FROM history WHERE version IS NULL AND type<>'SCHEMA') AS versions_expected,
    EXISTS(SELECT 1 FROM history WHERE version='10' AND success AND type='SQL'
      AND script='V10__unique_broker_order_identity.sql' AND description='unique broker order identity') AS v10_recorded,
    NOT EXISTS(SELECT 1 FROM history WHERE NOT success) AS history_clear,
    (SELECT count(DISTINCT version) FROM history
      WHERE success AND version IN ('1','2','3','4','5','6','7','8','9'))=9 AS baseline_complete,
    NOT EXISTS(SELECT 1 FROM trading.orders WHERE broker_order_id IS NOT NULL
      GROUP BY broker_order_id HAVING count(*)>1) AS identities_clear,
    NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='trading.orders'::regclass AND NOT convalidated) AS constraints_clear,
    to_regclass('trading.orders_broker_order_id_unique') IS NULL AS index_name_free,
    EXISTS(SELECT 1 FROM pg_index i WHERE i.indexrelid=to_regclass('trading.orders_broker_order_id_unique')
      AND i.indrelid='trading.orders'::regclass AND i.indisunique AND i.indisvalid AND i.indisready
      AND i.indnkeyatts=1 AND i.indnatts=1 AND i.indexprs IS NULL
      AND pg_get_indexdef(i.indexrelid,1,true)='broker_order_id'
      AND pg_get_expr(i.indpred,i.indrelid)='(broker_order_id IS NOT NULL)') AS index_valid
)
SELECT coalesce(history_clear AND baseline_complete AND versions_unique AND versions_expected AND identities_clear AND constraints_clear
  AND ((version='9' AND version_count=9 AND index_name_free) OR (version='10' AND version_count=10 AND v10_recorded AND index_valid)),false) AS ready
FROM evidence \gset
ROLLBACK;
\if :ready
  \echo V10_PREFLIGHT=READY
\else
  \echo V10_PREFLIGHT=NOT_READY STOP_AND_INVESTIGATE
  -- ON_ERROR_STOP makes this deliberate, non-mutating failure exit psql with code 3.
  SELECT 1 / 0 AS preflight_stop;
\endif
