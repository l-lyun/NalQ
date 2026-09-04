CREATE TABLE notion_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(36) NOT NULL,
    workspace_name VARCHAR(255) NULL,
    access_token_ciphertext TEXT NOT NULL,
    access_token_nonce BINARY(12) NOT NULL,
    refresh_token_ciphertext TEXT NULL,
    refresh_token_nonce BINARY(12) NULL,
    encryption_key_version VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    credential_revision BIGINT NOT NULL,
    connected_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notion_connections_user UNIQUE (user_id),
    CONSTRAINT fk_notion_connections_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_notion_connections_status CHECK (status IN ('CONNECTED', 'REAUTH_REQUIRED')),
    CONSTRAINT chk_notion_connections_refresh_pair CHECK (
        (refresh_token_ciphertext IS NULL AND refresh_token_nonce IS NULL)
        OR (refresh_token_ciphertext IS NOT NULL AND refresh_token_nonce IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
