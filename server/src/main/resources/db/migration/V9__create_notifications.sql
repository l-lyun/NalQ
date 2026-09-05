CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    payload_version INT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    quiz_set_id VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    failure_code VARCHAR(32) NULL,
    action_type VARCHAR(32) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_public_id UNIQUE (public_id),
    CONSTRAINT uk_notifications_quiz_set_id UNIQUE (quiz_set_id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_payload_version CHECK (payload_version = 1),
    CONSTRAINT chk_notifications_shape CHECK (
        (
            notification_type = 'QUIZ_GENERATION_READY'
            AND failure_code IS NULL
            AND action_type = 'FOCUS_QUIZ_IN_LIST'
        )
        OR (
            notification_type = 'QUIZ_GENERATION_FAILED'
            AND failure_code IN ('SOURCE_INSUFFICIENT', 'GENERATION_FAILED')
            AND action_type = 'RECONFIGURE_QUIZ'
        )
    ),
    INDEX idx_notifications_user_created (user_id, created_at DESC, public_id DESC),
    INDEX idx_notifications_user_unread_created (user_id, read_at, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
