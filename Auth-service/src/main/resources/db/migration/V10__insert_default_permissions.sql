-- ============================================================
-- Migration: Insert all unique permission combinations
-- (resource_id + action pairs)
--
-- Note: The many-to-many relationship between roles and
-- permissions is managed separately in V12's role_permissions table
-- ============================================================

-- Insert all unique permission combinations that will be used by roles
-- ADMIN will need: READ, WRITE, DELETE, EXECUTE on ACCOUNT, TRANSACTION, USER_PROFILE, ADMIN_PANEL
--                  READ on REPORT, AUDIT_LOG
-- BANK_TELLER will need: READ, WRITE on ACCOUNT
--                         READ, WRITE, EXECUTE on TRANSACTION
--                         READ on USER_PROFILE, REPORT
-- USER will need: READ on ACCOUNT
--                 READ, EXECUTE on TRANSACTION
--                 READ, WRITE on USER_PROFILE
-- AUDITOR will need: READ on ACCOUNT, TRANSACTION, USER_PROFILE, REPORT, AUDIT_LOG

-- First, let's insert all unique permission combinations
INSERT IGNORE INTO permissions (resource_id, action)
SELECT DISTINCT res.id, a.action
FROM resources res
JOIN (
    SELECT 'READ' AS action UNION ALL
    SELECT 'WRITE' UNION ALL
    SELECT 'DELETE' UNION ALL
    SELECT 'EXECUTE'
) a
WHERE (res.name = 'ACCOUNT' AND a.action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE'))
   OR (res.name = 'TRANSACTION' AND a.action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE'))
   OR (res.name = 'USER_PROFILE' AND a.action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE'))
   OR (res.name = 'ADMIN_PANEL' AND a.action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE'))
   OR (res.name = 'REPORT' AND a.action = 'READ')
   OR (res.name = 'AUDIT_LOG' AND a.action = 'READ');


