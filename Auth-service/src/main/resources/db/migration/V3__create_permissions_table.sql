CREATE TABLE permissions (
                             id          BIGINT          NOT NULL AUTO_INCREMENT,
                             resource_id BIGINT          NOT NULL,
                             action      VARCHAR(30)     NOT NULL,
                             version     BIGINT          NOT NULL DEFAULT 0,
                             created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                             PRIMARY KEY (id),
                             CONSTRAINT fk_permissions_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
                             CONSTRAINT uq_permission_resource_action UNIQUE (resource_id, action),
                             INDEX idx_permission_resource_action (resource_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;