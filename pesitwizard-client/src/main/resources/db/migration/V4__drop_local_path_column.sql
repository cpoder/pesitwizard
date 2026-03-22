-- Remove deprecated local_path column from favorite_transfers and scheduled_transfers
ALTER TABLE favorite_transfers DROP COLUMN IF EXISTS local_path;
ALTER TABLE scheduled_transfers DROP COLUMN IF EXISTS local_path;
