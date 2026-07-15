-- ============================================================
-- Migration: Create role_permissions join table
-- This table implements the many-to-many relationship between
-- roles and permissions as defined in the JPA entities.
-- ============================================================

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    INDEX idx_role_permissions_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Insert default role-permission mappings
-- ============================================================

-- ── ADMIN ─────────────────────────────────────────────────
-- ADMIN: READ, WRITE, DELETE, EXECUTE on ACCOUNT, TRANSACTION, USER_PROFILE, ADMIN_PANEL
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name IN ('ACCOUNT', 'TRANSACTION', 'USER_PROFILE', 'ADMIN_PANEL')
JOIN permissions p ON p.resource_id = res.id AND p.action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE')
WHERE r.name = 'ADMIN';

-- ADMIN: READ on REPORT and AUDIT_LOG
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name IN ('REPORT', 'AUDIT_LOG')
JOIN permissions p ON p.resource_id = res.id AND p.action = 'READ'
WHERE r.name = 'ADMIN';

-- ── BANK_TELLER ───────────────────────────────────────────
-- BANK_TELLER: READ, WRITE on ACCOUNT
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name = 'ACCOUNT'
JOIN permissions p ON p.resource_id = res.id AND p.action IN ('READ', 'WRITE')
WHERE r.name = 'BANK_TELLER';

-- BANK_TELLER: READ, WRITE, EXECUTE on TRANSACTION
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name = 'TRANSACTION'
JOIN permissions p ON p.resource_id = res.id AND p.action IN ('READ', 'WRITE', 'EXECUTE')
WHERE r.name = 'BANK_TELLER';

-- BANK_TELLER: READ on USER_PROFILE and REPORT
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name IN ('USER_PROFILE', 'REPORT')
JOIN permissions p ON p.resource_id = res.id AND p.action = 'READ'
WHERE r.name = 'BANK_TELLER';

-- ── USER ──────────────────────────────────────────────────
-- USER: READ on ACCOUNT
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name = 'ACCOUNT'
JOIN permissions p ON p.resource_id = res.id AND p.action = 'READ'
WHERE r.name = 'USER';

-- USER: READ, EXECUTE on TRANSACTION
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name = 'TRANSACTION'
JOIN permissions p ON p.resource_id = res.id AND p.action IN ('READ', 'EXECUTE')
WHERE r.name = 'USER';

-- USER: READ, WRITE on USER_PROFILE (own profile)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name = 'USER_PROFILE'
JOIN permissions p ON p.resource_id = res.id AND p.action IN ('READ', 'WRITE')
WHERE r.name = 'USER';

-- ── AUDITOR ───────────────────────────────────────────────
-- AUDITOR: READ on ACCOUNT, TRANSACTION, USER_PROFILE, REPORT, AUDIT_LOG
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN resources res ON res.name IN ('ACCOUNT', 'TRANSACTION', 'USER_PROFILE', 'REPORT', 'AUDIT_LOG')
JOIN permissions p ON p.resource_id = res.id AND p.action = 'READ'
WHERE r.name = 'AUDITOR';
