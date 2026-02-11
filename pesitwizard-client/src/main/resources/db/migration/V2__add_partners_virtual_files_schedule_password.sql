-- V2: Add partners, virtual files, and schedule password support

-- Partners table - stores PeSIT partner credentials for the client
CREATE TABLE IF NOT EXISTS partners (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    partner_id VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    password VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_partner_partner_id ON partners (partner_id);

-- Virtual files table - stores virtual file definitions for dropdown selection
CREATE TABLE IF NOT EXISTS virtual_files (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    direction VARCHAR(10) NOT NULL DEFAULT 'BOTH',
    record_length INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_virtual_file_name ON virtual_files (name);

-- Add password column to scheduled_transfers
ALTER TABLE scheduled_transfers ADD COLUMN IF NOT EXISTS password VARCHAR(255);
