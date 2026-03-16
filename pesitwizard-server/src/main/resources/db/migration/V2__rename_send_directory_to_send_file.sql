-- Rename send_directory to send_file in virtual_files table
-- Virtual files map to specific physical files, not directories
ALTER TABLE virtual_files RENAME COLUMN send_directory TO send_file;
