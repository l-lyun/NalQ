ALTER TABLE users
    DROP CHECK chk_users_activation_state,
    MODIFY COLUMN email VARCHAR(320) NULL,
    MODIFY COLUMN normalized_email VARCHAR(320) NULL,
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN withdrawal_request_id VARCHAR(36) NULL,
    ADD COLUMN withdrawal_disposal_due_at TIMESTAMP(6) NULL,
    ADD COLUMN withdrawal_disposal_completed_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT uk_users_withdrawal_request_id UNIQUE (withdrawal_request_id);

UPDATE users
SET email_verified_at = COALESCE(email_verified_at, activated_at, suspended_at, updated_at, created_at, CURRENT_TIMESTAMP(6)),
    activated_at = COALESCE(activated_at, email_verified_at, suspended_at, updated_at, created_at, CURRENT_TIMESTAMP(6)),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE status = 'SUSPENDED'
  AND (email_verified_at IS NULL OR activated_at IS NULL);

UPDATE users
SET email = NULL,
    normalized_email = NULL,
    password_hash = NULL,
    nickname = NULL,
    withdrawn_at = COALESCE(withdrawn_at, updated_at, created_at, CURRENT_TIMESTAMP(6)),
    withdrawal_request_id = UUID(),
    withdrawal_disposal_due_at = COALESCE(withdrawn_at, updated_at, created_at, CURRENT_TIMESTAMP(6)) + INTERVAL 30 DAY,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE status = 'WITHDRAWN';

ALTER TABLE users
    ADD CONSTRAINT chk_users_account_state CHECK (
        (
            status = 'PENDING_ACTIVATION'
            AND email IS NOT NULL
            AND normalized_email IS NOT NULL
            AND password_hash IS NOT NULL
            AND email_verified_at IS NULL
            AND activated_at IS NULL
            AND withdrawal_request_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_disposal_due_at IS NULL
        )
        OR (
            status IN ('ACTIVE', 'SUSPENDED')
            AND email IS NOT NULL
            AND normalized_email IS NOT NULL
            AND password_hash IS NOT NULL
            AND email_verified_at IS NOT NULL
            AND activated_at IS NOT NULL
            AND withdrawal_request_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_disposal_due_at IS NULL
        )
        OR (
            status = 'WITHDRAWN'
            AND email IS NULL
            AND normalized_email IS NULL
            AND password_hash IS NULL
            AND nickname IS NULL
            AND withdrawal_request_id IS NOT NULL
            AND withdrawn_at IS NOT NULL
            AND withdrawal_disposal_due_at = withdrawn_at + INTERVAL 30 DAY
        )
    );
