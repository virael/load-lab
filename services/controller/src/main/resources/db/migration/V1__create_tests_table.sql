CREATE TABLE tests (
    id                TEXT PRIMARY KEY,
    target_url        TEXT        NOT NULL,
    virtual_users     INT         NOT NULL,
    duration_seconds  INT         NOT NULL,
    status            TEXT        NOT NULL,
    total_requests    BIGINT      NOT NULL DEFAULT 0,
    avg_latency_ms    DOUBLE PRECISION NOT NULL DEFAULT 0,
    errors            BIGINT      NOT NULL DEFAULT 0,
    p50_ms            BIGINT      NOT NULL DEFAULT 0,
    p95_ms            BIGINT      NOT NULL DEFAULT 0,
    p99_ms            BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
