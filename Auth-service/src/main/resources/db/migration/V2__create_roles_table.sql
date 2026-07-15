CREATE TABLE roles (
                       id          BIGINT          NOT NULL AUTO_INCREMENT,
                       name        VARCHAR(50)     NOT NULL,
                       description VARCHAR(255),
                       version     BIGINT          NOT NULL DEFAULT 0,
                       created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       PRIMARY KEY (id),
                       CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;