-- PeSIT Wizard Server - Baseline Schema
-- Generated from JPA entities for Flyway migration

-- Partners table
CREATE TABLE IF NOT EXISTS partners (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    description VARCHAR(255),
    password VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    access_type VARCHAR(10) DEFAULT 'BOTH',
    max_connections INTEGER DEFAULT 10,
    allowed_files VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Virtual files table
CREATE TABLE IF NOT EXISTS virtual_files (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    description VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    direction VARCHAR(10) DEFAULT 'BOTH',
    receive_directory VARCHAR(255),
    send_directory VARCHAR(255),
    receive_filename_pattern VARCHAR(255) DEFAULT '${virtualFile}_${timestamp}',
    overwrite BOOLEAN DEFAULT FALSE,
    max_file_size BIGINT DEFAULT 0,
    file_type INTEGER DEFAULT 0,
    record_length INTEGER DEFAULT 1024,
    record_format INTEGER DEFAULT 128,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Transfer records table
CREATE TABLE IF NOT EXISTS transfer_records (
    id BIGSERIAL PRIMARY KEY,
    transfer_id VARCHAR(36) NOT NULL UNIQUE,
    session_id VARCHAR(36) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64),
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    partner_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    local_path VARCHAR(1024),
    file_size BIGINT DEFAULT 0,
    bytes_transferred BIGINT DEFAULT 0,
    progress_percent INTEGER DEFAULT 0,
    last_sync_point BIGINT DEFAULT 0,
    sync_point_count INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    remote_address VARCHAR(64),
    protocol_version INTEGER DEFAULT 2,
    access_type INTEGER DEFAULT 0,
    error_code VARCHAR(10),
    error_message VARCHAR(1024),
    checksum VARCHAR(64),
    checksum_algorithm VARCHAR(20) DEFAULT 'SHA-256',
    resumable BOOLEAN DEFAULT TRUE,
    parent_transfer_id VARCHAR(36),
    metadata TEXT,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_transfer_status ON transfer_records (status);
CREATE INDEX IF NOT EXISTS idx_transfer_partner ON transfer_records (partner_id);
CREATE INDEX IF NOT EXISTS idx_transfer_filename ON transfer_records (filename);
CREATE INDEX IF NOT EXISTS idx_transfer_started ON transfer_records (started_at);
CREATE INDEX IF NOT EXISTS idx_transfer_server ON transfer_records (server_id);
CREATE INDEX IF NOT EXISTS idx_transfer_session ON transfer_records (session_id);

-- Audit events table
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    category VARCHAR(30) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    username VARCHAR(100),
    auth_method VARCHAR(30),
    client_ip VARCHAR(64),
    session_id VARCHAR(64),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    action VARCHAR(50),
    server_id VARCHAR(64),
    partner_id VARCHAR(64),
    transfer_id VARCHAR(64),
    filename VARCHAR(255),
    bytes_transferred BIGINT,
    duration_ms BIGINT,
    error_code VARCHAR(20),
    error_message VARCHAR(1000),
    details TEXT,
    user_agent VARCHAR(500),
    request_uri VARCHAR(500),
    http_method VARCHAR(10),
    http_status INTEGER
);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_type ON audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_category ON audit_events (category);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_events (username);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_events (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_outcome ON audit_events (outcome);
CREATE INDEX IF NOT EXISTS idx_audit_session ON audit_events (session_id);

-- File checksums table
CREATE TABLE IF NOT EXISTS file_checksums (
    id BIGSERIAL PRIMARY KEY,
    checksum_hash VARCHAR(128) NOT NULL,
    algorithm VARCHAR(10) NOT NULL DEFAULT 'SHA_256',
    filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    transfer_id VARCHAR(255),
    partner_id VARCHAR(255),
    server_id VARCHAR(255),
    direction VARCHAR(10),
    local_path VARCHAR(1024),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    computed_at TIMESTAMP,
    verified_at TIMESTAMP,
    duplicate_count INTEGER DEFAULT 0,
    first_seen_at TIMESTAMP,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_checksum_hash ON file_checksums (checksum_hash);
CREATE INDEX IF NOT EXISTS idx_checksum_filename ON file_checksums (filename);
CREATE INDEX IF NOT EXISTS idx_checksum_transfer ON file_checksums (transfer_id);
CREATE INDEX IF NOT EXISTS idx_checksum_partner ON file_checksums (partner_id);

-- PeSIT server configuration table
CREATE TABLE IF NOT EXISTS pesit_server_config (
    id BIGSERIAL PRIMARY KEY,
    server_id VARCHAR(255) NOT NULL UNIQUE,
    port INTEGER NOT NULL UNIQUE,
    bind_address VARCHAR(255) DEFAULT '0.0.0.0',
    protocol_version INTEGER DEFAULT 2,
    max_connections INTEGER DEFAULT 100,
    connection_timeout INTEGER DEFAULT 30000,
    read_timeout INTEGER DEFAULT 60000,
    receive_directory VARCHAR(255) DEFAULT '/data/received',
    send_directory VARCHAR(255) DEFAULT '/data/send',
    max_entity_size INTEGER DEFAULT 4096,
    sync_points_enabled BOOLEAN DEFAULT TRUE,
    sync_interval_kb INTEGER DEFAULT 32,
    resync_enabled BOOLEAN DEFAULT TRUE,
    auto_start BOOLEAN DEFAULT FALSE,
    status VARCHAR(10) DEFAULT 'STOPPED',
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    last_started_at TIMESTAMP,
    last_stopped_at TIMESTAMP
);

-- Server ownership table (for cluster mode)
CREATE TABLE IF NOT EXISTS server_ownership (
    server_id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_node VARCHAR(128) NOT NULL,
    acquired_at TIMESTAMP NOT NULL
);

-- Secrets table
CREATE TABLE IF NOT EXISTS secrets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    secret_type VARCHAR(30) NOT NULL DEFAULT 'GENERIC',
    encrypted_value TEXT NOT NULL,
    iv VARCHAR(32) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    partner_id VARCHAR(64),
    server_id VARCHAR(64),
    version BIGINT,
    active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    last_rotated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_secret_name ON secrets (name);
CREATE INDEX IF NOT EXISTS idx_secret_type ON secrets (secret_type);
CREATE INDEX IF NOT EXISTS idx_secret_scope ON secrets (scope);

-- API keys table
CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    key_prefix VARCHAR(8) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    allowed_ips VARCHAR(1000),
    rate_limit INTEGER,
    partner_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100),
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_apikey_key ON api_keys (key_hash);
CREATE INDEX IF NOT EXISTS idx_apikey_name ON api_keys (name);
CREATE INDEX IF NOT EXISTS idx_apikey_active ON api_keys (active);

-- API key roles (ElementCollection)
CREATE TABLE IF NOT EXISTS api_key_roles (
    api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    role TEXT
);

-- Certificate stores table
CREATE TABLE IF NOT EXISTS certificate_stores (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    store_type VARCHAR(20) NOT NULL,
    format VARCHAR(10) NOT NULL DEFAULT 'PKCS12',
    store_data BYTEA NOT NULL,
    store_password VARCHAR(500),
    key_password VARCHAR(500),
    key_alias VARCHAR(100),
    subject_dn VARCHAR(500),
    issuer_dn VARCHAR(500),
    serial_number VARCHAR(100),
    valid_from TIMESTAMP,
    expires_at TIMESTAMP,
    fingerprint VARCHAR(64),
    active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    purpose VARCHAR(20) DEFAULT 'SERVER',
    partner_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT,
    UNIQUE (name, store_type)
);

CREATE INDEX IF NOT EXISTS idx_cert_name ON certificate_stores (name);
CREATE INDEX IF NOT EXISTS idx_cert_type ON certificate_stores (store_type);
CREATE INDEX IF NOT EXISTS idx_cert_active ON certificate_stores (active);
CREATE INDEX IF NOT EXISTS idx_cert_expiry ON certificate_stores (expires_at);
