-- Create events table for raw event storage
CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36)  NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    user_id         VARCHAR(100),
    session_id      VARCHAR(100),
    payload         JSONB,
    source          VARCHAR(100),
    region          VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE
);

-- Create dead letter queue table
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36),
    raw_payload     TEXT,
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_events_type       ON events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_user_id    ON events(user_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_region     ON events(region);
CREATE INDEX IF NOT EXISTS idx_events_source     ON events(source);

-- Create aggregated_metrics table for pre-computed stats
CREATE TABLE IF NOT EXISTS aggregated_metrics (
    id              BIGSERIAL PRIMARY KEY,
    metric_name     VARCHAR(200) NOT NULL,
    metric_value    BIGINT       NOT NULL,
    window_start    TIMESTAMP WITH TIME ZONE,
    window_end      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(metric_name, window_start)
);

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO analytics;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO analytics;
