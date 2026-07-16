CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE metric_windows (
    test_id             TEXT        NOT NULL,
    ts                  TIMESTAMPTZ NOT NULL,
    requests_in_window  BIGINT      NOT NULL,
    errors_in_window    BIGINT      NOT NULL
);

SELECT create_hypertable('metric_windows', 'ts');

CREATE INDEX idx_metric_windows_test_id ON metric_windows (test_id, ts DESC);
