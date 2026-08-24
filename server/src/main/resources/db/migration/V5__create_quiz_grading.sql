CREATE TABLE quiz_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    learning_material_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_sets_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_sets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_sets_material FOREIGN KEY (learning_material_id) REFERENCES learning_materials (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_sets_status CHECK (status IN ('GENERATING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    question_number INT NOT NULL,
    type VARCHAR(32) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    representative_answer TEXT NOT NULL,
    explanation TEXT NOT NULL,
    source_excerpt TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_questions_public_id UNIQUE (public_id),
    CONSTRAINT uk_quiz_questions_set_number UNIQUE (quiz_set_id, question_number),
    CONSTRAINT fk_quiz_questions_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_questions_type CHECK (type IN ('MULTIPLE_CHOICE', 'FILL_IN_THE_BLANK', 'SHORT_ANSWER', 'ESSAY')),
    CONSTRAINT chk_quiz_questions_number CHECK (question_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_short_answer_accepted_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer TEXT NOT NULL,
    normalized_answer VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_short_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_short_answers_normalized CHECK (CHAR_LENGTH(normalized_answer) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    automatic_correct_count INT NOT NULL,
    automatic_graded_count INT NOT NULL,
    summary_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_attempts_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_attempts_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_attempts_status CHECK (status IN ('SELF_ASSESSMENT_REQUIRED', 'COMPLETED')),
    CONSTRAINT chk_quiz_attempts_counts CHECK (
        automatic_correct_count >= 0 AND automatic_graded_count >= automatic_correct_count AND summary_revision >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_question_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    submitted_answer TEXT NULL,
    automatic_outcome VARCHAR(16) NOT NULL,
    user_override_outcome VARCHAR(16) NULL,
    grading_revision BIGINT NOT NULL DEFAULT 0,
    corrected_at TIMESTAMP(6) NULL,
    review_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_question_results_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_quiz_question_results_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_question_results_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_question_results_automatic CHECK (automatic_outcome IN ('CORRECT', 'INCORRECT')),
    CONSTRAINT chk_quiz_question_results_override CHECK (user_override_outcome IS NULL OR user_override_outcome IN ('CORRECT', 'INCORRECT')),
    CONSTRAINT chk_quiz_question_results_revision CHECK (grading_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_attempt_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    attempt_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_attempt_submissions_key UNIQUE (user_id, quiz_set_id, idempotency_key_hash),
    CONSTRAINT fk_quiz_attempt_submissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempt_submissions_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempt_submissions_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_short_answer_grading_idempotencies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    grading_revision BIGINT NOT NULL,
    summary_revision BIGINT NOT NULL,
    correct_question_count INT NOT NULL,
    graded_question_count INT NOT NULL,
    review_question_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_short_answer_grading_key UNIQUE (user_id, attempt_id, question_id, idempotency_key_hash),
    CONSTRAINT fk_quiz_short_answer_grading_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_short_answer_grading_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_short_answer_grading_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_short_answer_grading_outcome CHECK (outcome IN ('CORRECT', 'INCORRECT')),
    CONSTRAINT chk_quiz_short_answer_grading_counts CHECK (
        grading_revision >= 0 AND summary_revision >= 0 AND correct_question_count >= 0
        AND graded_question_count >= correct_question_count AND review_question_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_review_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    source_attempt_id BIGINT NOT NULL,
    source_summary_revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_review_sessions_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_review_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_review_sessions_attempt FOREIGN KEY (source_attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_review_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT chk_quiz_review_sessions_revision CHECK (source_summary_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_review_session_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_review_session_question UNIQUE (review_session_id, question_id),
    CONSTRAINT uk_quiz_review_session_sequence UNIQUE (review_session_id, sequence_number),
    CONSTRAINT fk_quiz_review_session_questions_session FOREIGN KEY (review_session_id) REFERENCES quiz_review_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_review_session_questions_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_review_session_questions_status CHECK (status IN ('PENDING', 'RESOLVED', 'UNRESOLVED')),
    CONSTRAINT chk_quiz_review_session_questions_sequence CHECK (sequence_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
