
CREATE TABLE users (
                       id                    BIGINT          NOT NULL AUTO_INCREMENT,
                       first_name             VARCHAR(50)    NOT NULL,
                       middle_name            VARCHAR(50)    ,
                       last_name              VARCHAR(50)    NOT NULL,
                       email                  VARCHAR(100)   NOT NULL,
                       phone                  VARCHAR(100)   NOT NULL ,
                       national_id            VARCHAR(100)   NOT NULL ,
                       identity_file_path     VARCHAR(255)   ,
                       password_hash         VARCHAR(255)    NOT NULL,
                       is_active             BOOLEAN         NOT NULL DEFAULT TRUE,
                       failed_login_attempts INT             NOT NULL DEFAULT 0,
                       locked_until          DATETIME,
                       version               BIGINT          NOT NULL DEFAULT 0,
                       created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),
                       CONSTRAINT uq_users_email    UNIQUE (email),
                       CONSTRAINT  uq_user_phone    UNIQUE (phone),
                       CONSTRAINT  uq_user_national_id UNIQUE(national_id),
                       CONSTRAINT  uq_user_identity_file_path UNIQUE(identity_file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;