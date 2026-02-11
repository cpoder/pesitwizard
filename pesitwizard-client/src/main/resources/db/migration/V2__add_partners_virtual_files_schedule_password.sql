-- V2: Add client partners, client virtual files, and schedule password support
-- Table names prefixed with "client_" to avoid collision with server-side tables
-- when both modules share the same database (e.g. k3d integration tests).

-- Client partners table - stores PeSIT partner credentials for the client
CREATE TABLE IF NOT EXISTS client_partners (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    partner_id VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    password VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_client_partner_partner_id ON client_partners (partner_id);

-- Client virtual files table - stores virtual file definitions for dropdown selection
CREATE TABLE IF NOT EXISTS client_virtual_files (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    direction VARCHAR(10) NOT NULL DEFAULT 'BOTH',
    record_length INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_client_virtual_file_name ON client_virtual_files (name);

-- Add password column to scheduled_transfers
ALTER TABLE scheduled_transfers ADD COLUMN IF NOT EXISTS password VARCHAR(255);
