-- Downsampling: a per-minute rollup, useful for browsing many tests' history at a
-- coarser grain than the raw, per-second windows in metric_windows.
CREATE MATERIALIZED VIEW metric_windows_1min
WITH (timescaledb.continuous) AS
SELECT
    test_id,
    time_bucket('1 minute', ts) AS bucket,
    sum(requests_in_window) AS requests,
    sum(errors_in_window)   AS errors
FROM metric_windows
GROUP BY test_id, bucket
WITH NO DATA;

-- start_offset (2 days) is deliberately far shorter than the 7-day retention below:
-- it gives the refresh job a safe margin to materialise data BEFORE retention
-- deletes it. A NULL start_offset would mirror the raw table exactly, so dropped
-- raw rows would drop their rollup too — the opposite of downsampling.
SELECT add_continuous_aggregate_policy('metric_windows_1min',
    start_offset      => INTERVAL '2 days',
    end_offset         => INTERVAL '1 minute',
    schedule_interval  => INTERVAL '1 minute');

-- Retention: drop raw, per-second windows older than 7 days. The per-minute rollup
-- above is NOT affected — it lives in its own internal hypertable and keeps the
-- long-term history. That survival is the whole point of downsampling.
SELECT add_retention_policy('metric_windows', INTERVAL '7 days');
