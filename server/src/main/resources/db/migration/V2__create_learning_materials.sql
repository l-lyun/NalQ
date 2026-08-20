CREATE TABLE learning_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    content_edit_status VARCHAR(32) NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_materials_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_learning_materials_user_idempotency
        UNIQUE (user_id, idempotency_key_hash),
    CONSTRAINT chk_learning_materials_source_type
        CHECK (source_type IN ('PASTE', 'NOTION')),
    CONSTRAINT chk_learning_materials_content_edit_status
        CHECK (content_edit_status = 'EDITABLE'),
    CONSTRAINT chk_learning_materials_title_length
        CHECK (CHAR_LENGTH(title) BETWEEN 1 AND 255),
    CONSTRAINT chk_learning_materials_content_length
        CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 20000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
