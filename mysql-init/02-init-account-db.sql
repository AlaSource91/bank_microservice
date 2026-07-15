-- ============================================================
-- MySQL initialization script for Account Service
-- Executed automatically by the MySQL Docker image on first start
-- (any *.sql file placed in /docker-entrypoint-initdb.d/)
-- ============================================================

-- Create the account_db database if it does not exist yet.
CREATE DATABASE IF NOT EXISTS account_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Grant full privileges on account_db to the shared application user.
GRANT ALL PRIVILEGES ON account_db.* TO 'bank_user'@'%';

FLUSH PRIVILEGES;

