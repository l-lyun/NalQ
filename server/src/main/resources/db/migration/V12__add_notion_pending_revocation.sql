ALTER TABLE notion_connections
    ADD COLUMN pending_revocation_workspace_id VARCHAR(36) NULL AFTER refresh_token_nonce,
    ADD COLUMN pending_revocation_token_ciphertext TEXT NULL AFTER pending_revocation_workspace_id,
    ADD COLUMN pending_revocation_token_nonce BINARY(12) NULL AFTER pending_revocation_token_ciphertext,
    ADD COLUMN pending_revocation_key_version VARCHAR(32) NULL AFTER pending_revocation_token_nonce,
    ADD COLUMN pending_revocation_created_at TIMESTAMP(6) NULL AFTER pending_revocation_key_version,
    ADD CONSTRAINT chk_notion_connections_pending_revocation CHECK (
        (pending_revocation_workspace_id IS NULL
            AND pending_revocation_token_ciphertext IS NULL
            AND pending_revocation_token_nonce IS NULL
            AND pending_revocation_key_version IS NULL
            AND pending_revocation_created_at IS NULL)
        OR (pending_revocation_workspace_id IS NOT NULL
            AND pending_revocation_token_ciphertext IS NOT NULL
            AND pending_revocation_token_nonce IS NOT NULL
            AND pending_revocation_key_version IS NOT NULL
            AND pending_revocation_created_at IS NOT NULL)
    );
