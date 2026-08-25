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
    question_type VARCHAR(32) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    source_excerpt TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_questions_public_id UNIQUE (public_id),
    CONSTRAINT uk_quiz_questions_set_number UNIQUE (quiz_set_id, question_number),
    CONSTRAINT fk_quiz_questions_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_questions_type CHECK (
        question_type IN ('MULTIPLE_CHOICE', 'FILL_IN_THE_BLANK', 'SHORT_ANSWER', 'ESSAY')
    ),
    CONSTRAINT chk_quiz_questions_number CHECK (question_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_question_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_value TEXT NOT NULL,
    normalized_value VARCHAR(1000) NULL,
    answer_role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_question_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_question_answers_role CHECK (answer_role IN ('CORRECT', 'ACCEPTED', 'EXAMPLE')),
    CONSTRAINT chk_quiz_question_answers_value CHECK (CHAR_LENGTH(answer_value) > 0),
    CONSTRAINT chk_quiz_question_answers_normalized CHECK (
        normalized_value IS NULL OR CHAR_LENGTH(normalized_value) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    attempt_type VARCHAR(16) NOT NULL,
    source_attempt_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_attempts_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_attempts_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_source FOREIGN KEY (source_attempt_id) REFERENCES quiz_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_attempts_type CHECK (attempt_type IN ('MAIN', 'REVIEW')),
    CONSTRAINT chk_quiz_attempts_status CHECK (
        status IN ('IN_PROGRESS', 'SELF_ASSESSMENT_REQUIRED', 'COMPLETED')
    ),
    CONSTRAINT chk_quiz_attempts_source CHECK (
        (attempt_type = 'MAIN' AND source_attempt_id IS NULL)
        OR (attempt_type = 'REVIEW' AND source_attempt_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_attempt_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    source_attempt_question_id BIGINT NULL,
    sequence_number INT NOT NULL,
    automatic_grading_result VARCHAR(16) NULL,
    final_grading_result VARCHAR(16) NULL,
    grading_method VARCHAR(24) NULL,
    review_resolved_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_attempt_questions_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT uk_quiz_attempt_questions_attempt_sequence UNIQUE (attempt_id, sequence_number),
    CONSTRAINT fk_quiz_attempt_questions_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempt_questions_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempt_questions_source FOREIGN KEY (source_attempt_question_id) REFERENCES quiz_attempt_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_attempt_questions_sequence CHECK (sequence_number > 0),
    CONSTRAINT chk_quiz_attempt_questions_automatic_result CHECK (
        automatic_grading_result IS NULL OR automatic_grading_result IN ('CORRECT', 'INCORRECT')
    ),
    CONSTRAINT chk_quiz_attempt_questions_final_result CHECK (
        final_grading_result IS NULL OR final_grading_result IN ('CORRECT', 'PARTIAL', 'INCORRECT')
    ),
    CONSTRAINT chk_quiz_attempt_questions_method CHECK (
        grading_method IS NULL OR grading_method IN ('AUTOMATIC', 'USER_OVERRIDE', 'SELF_ASSESSMENT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_submitted_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_question_id BIGINT NOT NULL,
    answer_value TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_submitted_answers_attempt_question FOREIGN KEY (attempt_question_id) REFERENCES quiz_attempt_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_submitted_answers_value CHECK (CHAR_LENGTH(answer_value) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
