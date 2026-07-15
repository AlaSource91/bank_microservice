INSERT INTO users (first_name,middle_name,last_name ,email,phone,  national_id,identity_file_path, password_hash, is_active)
VALUES (
           'admin',
           'Alaeldin',
        'Suliman',
           'admin@bank.com',
        '+971588530119',
        '099282878766366',
        '099282878766366.jpg',
           '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgfl4i8VToBf7yDQDRHfGe',
           TRUE
       )
    ON DUPLICATE KEY UPDATE
        password_hash = VALUES(password_hash),
        email = VALUES(email),
        is_active = VALUES(is_active);

-- Assign ADMIN role to the admin user
INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT u.id, r.id, 'flyway-seed'
FROM users u
         JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@bank.com'
    ON DUPLICATE KEY UPDATE assigned_by = VALUES(assigned_by);