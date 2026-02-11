-- Remove unused connect_express_mode column from pesit_servers
ALTER TABLE pesit_servers DROP COLUMN IF EXISTS connect_express_mode;
