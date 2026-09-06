CREATE TABLE push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    installation_id VARCHAR(36) NOT NULL,
    installation_key_digest CHAR(64) NOT NULL,
    user_id BIGINT NULL,
    session_id VARCHAR(128) NULL,
    binding_id VARCHAR(36) NULL,
    revision BIGINT NOT NULL,
    token_version BIGINT NOT NULL,
    platform VARCHAR(16) NULL,
    provider VARCHAR(16) NULL,
    push_token VARCHAR(512) NULL,
    push_token_digest CHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    inactive_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_devices_installation UNIQUE (installation_id),
    CONSTRAINT uk_push_devices_active_token UNIQUE (provider, push_token_digest),
    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_push_devices_revision CHECK (revision >= 1 AND token_version >= 1),
    CONSTRAINT chk_push_devices_shape CHECK (
        (
            status = 'ACTIVE'
            AND user_id IS NOT NULL
            AND session_id IS NOT NULL
            AND binding_id IS NOT NULL
            AND platform IN ('IOS', 'ANDROID')
            AND provider = 'EXPO'
            AND push_token IS NOT NULL
            AND push_token_digest IS NOT NULL
            AND inactive_at IS NULL
        )
        OR (
            status IN ('DISABLED', 'REVOKED')
            AND session_id IS NULL
            AND binding_id IS NULL
            AND push_token IS NULL
            AND push_token_digest IS NULL
            AND inactive_at IS NOT NULL
        )
    ),
    INDEX idx_push_devices_user_status (user_id, status, id),
    INDEX idx_push_devices_session_status (session_id, status, id),
    INDEX idx_push_devices_inactive (status, inactive_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE push_device_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    installation_id VARCHAR(36) NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    subject_user_id BIGINT NULL,
    request_digest CHAR(64) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    result_revision BIGINT NULL,
    result_binding_id VARCHAR(36) NULL,
    result_status VARCHAR(16) NULL,
    result_user_id BIGINT NULL,
    result_revoked BOOLEAN NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_device_operations_intent UNIQUE (installation_id, operation_id),
    CONSTRAINT chk_push_device_operations_shape CHECK (
        (
            operation_type = 'REGISTER'
            AND subject_user_id IS NOT NULL
            AND result_revision IS NOT NULL
            AND result_status IN ('ACTIVE', 'DISABLED', 'REVOKED')
            AND result_user_id IS NOT NULL
            AND result_revoked IS NULL
        )
        OR (
            operation_type = 'REVOKE'
            AND result_revision IS NULL
            AND result_binding_id IS NULL
            AND result_status IS NULL
            AND result_user_id IS NULL
            AND result_revoked IS NOT NULL
        )
    ),
    INDEX idx_push_device_operations_expiry (expires_at, id),
    INDEX idx_push_device_operations_user (subject_user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
