CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at TIMESTAMP(6) NULL,
    status VARCHAR(32) NOT NULL,
    activated_at TIMESTAMP(6) NULL,
    suspended_at TIMESTAMP(6) NULL,
    withdrawn_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_users_activation_state CHECK (
        (status = 'PENDING_ACTIVATION' AND email_verified_at IS NULL AND activated_at IS NULL)
        OR (status = 'ACTIVE' AND email_verified_at IS NOT NULL AND activated_at IS NOT NULL)
        OR status IN ('SUSPENDED', 'WITHDRAWN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
