\set ON_ERROR_STOP on
-- This script assumes this repository's public Flyway history and trading schema.
-- Run before application startup (Flyway migrates automatically). No data repair.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';
SELECT version AS current_flyway_version, success
  FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT count(*) AS duplicate_non_null_broker_id_groups FROM (
  SELECT broker_order_id FROM trading.orders WHERE broker_order_id IS NOT NULL
  GROUP BY broker_order_id HAVING count(*) > 1
) duplicates;
SELECT conname, contype, convalidated FROM pg_constraint WHERE conrelid='trading.orders'::regclass;
SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='trading' AND tablename='orders';
WITH evidence AS (
  SELECT
    (SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1) AS version,
    NOT EXISTS(SELECT 1 FROM public.flyway_schema_history WHERE NOT success) AS history_clear,
    (SELECT count(DISTINCT version) FROM public.flyway_schema_history
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
SELECT coalesce(history_clear AND baseline_complete AND identities_clear AND constraints_clear
  AND ((version='9' AND index_name_free) OR (version='10' AND index_valid)),false) AS ready
FROM evidence \gset
ROLLBACK;
\if :ready
  \echo V10_PREFLIGHT=READY
\else
  \echo V10_PREFLIGHT=NOT_READY STOP_AND_INVESTIGATE
  -- ON_ERROR_STOP makes this deliberate, non-mutating failure exit psql with code 3.
  SELECT 1 / 0 AS preflight_stop;
\endif
