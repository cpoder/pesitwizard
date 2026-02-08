-- PeSIT Wizard Client - Baseline Schema
-- Generated from JPA entities for Flyway migration

-- Transfer history table
CREATE TABLE IF NOT EXISTS transfer_history (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    server_id VARCHAR(255),
    server_name VARCHAR(255),
    partner_id VARCHAR(255),
    direction VARCHAR(10),
    local_filename VARCHAR(255),
    remote_filename VARCHAR(255),
    file_size BIGINT,
    bytes_transferred BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message VARCHAR(2000),
    diagnostic_code VARCHAR(255),
    checksum VARCHAR(255),
    transfer_config_id VARCHAR(255),
    transfer_config_name VARCHAR(255),
    initiated_by VARCHAR(255),
    correlation_id VARCHAR(255),
    trace_id VARCHAR(255),
    span_id VARCHAR(255),
    sync_points_enabled BOOLEAN DEFAULT FALSE,
    last_sync_point INTEGER,
    bytes_at_last_sync_point BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transfer_history_server ON transfer_history (server_id);
CREATE INDEX IF NOT EXISTS idx_transfer_history_status ON transfer_history (status);
CREATE INDEX IF NOT EXISTS idx_transfer_history_started ON transfer_history (started_at);

-- Transfer configs table
CREATE TABLE IF NOT EXISTS transfer_configs (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    chunk_size INTEGER DEFAULT 32768,
    compression_enabled BOOLEAN DEFAULT FALSE,
    compression_type INTEGER DEFAULT 0,
    crc_enabled BOOLEAN DEFAULT TRUE,
    sync_points_enabled BOOLEAN DEFAULT FALSE,
    sync_point_interval INTEGER DEFAULT 100,
    resync_enabled BOOLEAN DEFAULT FALSE,
    priority INTEGER DEFAULT 5,
    max_retries INTEGER DEFAULT 3,
    retry_delay_ms INTEGER DEFAULT 5000,
    record_format VARCHAR(255) DEFAULT 'V',
    record_length INTEGER DEFAULT 506,
    data_code VARCHAR(255) DEFAULT 'B',
    default_config BOOLEAN DEFAULT FALSE,
    source_connection_id VARCHAR(255),
    source_path VARCHAR(255),
    destination_connection_id VARCHAR(255),
    destination_path VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- PeSIT servers table
CREATE TABLE IF NOT EXISTS pesit_servers (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    server_id VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    tls_enabled BOOLEAN DEFAULT FALSE,
    truststore_data BYTEA,
    truststore_password VARCHAR(255),
    keystore_data BYTEA,
    keystore_password VARCHAR(255),
    hostname_verification BOOLEAN DEFAULT TRUE,
    connection_timeout INTEGER DEFAULT 30000,
    read_timeout INTEGER DEFAULT 60000,
    enabled BOOLEAN DEFAULT TRUE,
    default_server BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Storage connections table
CREATE TABLE IF NOT EXISTS storage_connections (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    connector_type VARCHAR(255) NOT NULL,
    config_json TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    last_tested_at TIMESTAMP,
    last_test_success BOOLEAN,
    last_test_error VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Scheduled transfers table
CREATE TABLE IF NOT EXISTS scheduled_transfers (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    favorite_id VARCHAR(255),
    server_id VARCHAR(255) NOT NULL,
    server_name VARCHAR(255),
    partner_id VARCHAR(255),
    direction VARCHAR(10) NOT NULL,
    source_connection_id VARCHAR(255),
    destination_connection_id VARCHAR(255),
    filename VARCHAR(255),
    local_path VARCHAR(255),
    remote_filename VARCHAR(255),
    virtual_file VARCHAR(255),
    transfer_config_id VARCHAR(255),
    schedule_type VARCHAR(20) NOT NULL DEFAULT 'ONCE',
    cron_expression VARCHAR(255),
    interval_minutes INTEGER,
    daily_time TIME,
    day_of_week INTEGER,
    day_of_month INTEGER,
    calendar_id VARCHAR(255),
    working_days_only BOOLEAN DEFAULT FALSE,
    scheduled_at TIMESTAMP,
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_run_status VARCHAR(10),
    last_run_error VARCHAR(2000),
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduled_enabled ON scheduled_transfers (enabled);
CREATE INDEX IF NOT EXISTS idx_scheduled_next_run ON scheduled_transfers (next_run_at);

-- Favorite transfers table
CREATE TABLE IF NOT EXISTS favorite_transfers (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    server_id VARCHAR(255) NOT NULL,
    server_name VARCHAR(255),
    partner_id VARCHAR(255),
    direction VARCHAR(10) NOT NULL,
    source_connection_id VARCHAR(255),
    destination_connection_id VARCHAR(255),
    filename VARCHAR(255),
    local_path VARCHAR(255),
    remote_filename VARCHAR(255),
    virtual_file VARCHAR(255),
    password VARCHAR(255),
    transfer_config_id VARCHAR(255),
    usage_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_favorite_name ON favorite_transfers (name);
CREATE INDEX IF NOT EXISTS idx_favorite_server ON favorite_transfers (server_id);

-- Business calendars table
CREATE TABLE IF NOT EXISTS business_calendars (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    timezone VARCHAR(255) NOT NULL DEFAULT 'Europe/Paris',
    business_hours_start TIME DEFAULT '08:00',
    business_hours_end TIME DEFAULT '18:00',
    restrict_to_business_hours BOOLEAN DEFAULT FALSE,
    default_calendar BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Business calendar working days (ElementCollection)
CREATE TABLE IF NOT EXISTS calendar_working_days (
    calendar_id VARCHAR(255) NOT NULL REFERENCES business_calendars(id) ON DELETE CASCADE,
    day_of_week INTEGER
);

-- Business calendar holidays (ElementCollection)
CREATE TABLE IF NOT EXISTS calendar_holidays (
    calendar_id VARCHAR(255) NOT NULL REFERENCES business_calendars(id) ON DELETE CASCADE,
    holiday_date DATE
);
