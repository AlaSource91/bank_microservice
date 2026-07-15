-- ============================================================
-- MySQL initialization script
-- Executed automatically by the MySQL Docker image on first start
-- (any *.sql file placed in /docker-entrypoint-initdb.d/)
-- ============================================================

-- Create auth_db if it doesn't exist
CREATE DATABASE IF NOT EXISTS auth_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Create account_db if it doesn't exist
CREATE DATABASE IF NOT EXISTS account_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Grant privileges on all databases to bank_user
GRANT ALL PRIVILEGES ON auth_db.* TO 'bank_user'@'%';
GRANT ALL PRIVILEGES ON account_db.* TO 'bank_user'@'%';
GRANT ALL PRIVILEGES ON bank_simulator_db.* TO 'bank_user'@'%';

FLUSH PRIVILEGES;

