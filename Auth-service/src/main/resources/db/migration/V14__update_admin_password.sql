-- Update admin password to SecurePass123!
-- BCrypt hash with cost factor 12
UPDATE users
SET password_hash = '$2a$12$mZN8VqNKLZwJLnXNJrLXHuqSHmfPYYP3tgCe6lqoL8N5wqBdH7Ady'
WHERE email = 'admin@bank.com';

-- Verify the update
SELECT id, first_name, last_name, email, is_active,
       SUBSTRING(password_hash, 1, 20) as password_hash_prefix
FROM users
WHERE email = 'admin@bank.com';

