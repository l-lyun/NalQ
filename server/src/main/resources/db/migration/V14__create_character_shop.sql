CREATE TABLE character_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coin_balance INT NOT NULL DEFAULT 0,
    character_item_id VARCHAR(32) NOT NULL DEFAULT 'character-dragon',
    room_item_id VARCHAR(32) NOT NULL DEFAULT 'room-day',
    hat_item_id VARCHAR(32) NOT NULL DEFAULT 'hat-none',
    top_item_id VARCHAR(32) NOT NULL DEFAULT 'top-none',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_character_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_character_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_character_profiles_balance CHECK (coin_balance >= 0),
    CONSTRAINT chk_character_profiles_character CHECK (
        character_item_id IN ('character-dragon', 'character-cat', 'character-bear')
    ),
    CONSTRAINT chk_character_profiles_room CHECK (room_item_id IN ('room-day', 'room-night')),
    CONSTRAINT chk_character_profiles_hat CHECK (hat_item_id IN ('hat-none', 'hat-beret', 'hat-beanie')),
    CONSTRAINT chk_character_profiles_top CHECK (top_item_id IN ('top-none', 'top-sweater'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE character_owned_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    item_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_character_owned_items_user_item UNIQUE (user_id, item_id),
    CONSTRAINT fk_character_owned_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_character_owned_items_paid CHECK (
        item_id IN ('character-cat', 'character-bear', 'room-night', 'hat-beret', 'hat-beanie', 'top-sweater')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_coin_rewards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    attempt_public_id VARCHAR(36) NOT NULL,
    amount INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_coin_rewards_user_set UNIQUE (user_id, quiz_set_id),
    CONSTRAINT uk_quiz_coin_rewards_attempt UNIQUE (attempt_public_id),
    CONSTRAINT fk_quiz_coin_rewards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_coin_rewards_amount CHECK (amount > 0),
    INDEX idx_quiz_coin_rewards_user_created (user_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
