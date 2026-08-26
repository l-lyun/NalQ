CREATE TABLE notion_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    workspace_name VARCHAR(255) NULL,
    workspace_icon_url VARCHAR(2048) NULL,
    bot_id VARCHAR(64) NOT NULL,
    access_token_ciphertext VARBINARY(4096) NOT NULL,
    refresh_token_ciphertext VARBINARY(4096) NOT NULL,
    encryption_key_version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notion_connections_user UNIQUE (user_id),
    CONSTRAINT fk_notion_connections_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_notion_connections_status
        CHECK (status IN ('CONNECTED', 'REAUTH_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
