CREATE TABLE quiz_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    learning_material_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(32) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_sets_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_sets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_sets_material FOREIGN KEY (learning_material_id) REFERENCES learning_materials (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_sets_status CHECK (status IN ('GENERATING', 'READY', 'FAILED')),
    CONSTRAINT chk_quiz_sets_failure CHECK (
        (
            status = 'FAILED'
            AND failure_code IS NOT NULL
            AND failure_code IN ('SOURCE_INSUFFICIENT', 'GENERATION_FAILED')
        )
        OR (status IN ('GENERATING', 'READY') AND failure_code IS NULL)
    )
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

CREATE TABLE quiz_question_choices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    question_id BIGINT NOT NULL,
    choice_value TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_question_choices_public_id UNIQUE (public_id),
    CONSTRAINT fk_quiz_question_choices_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_question_choices_value CHECK (CHAR_LENGTH(choice_value) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_short_answer_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_value TEXT NOT NULL,
    normalized_value VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_short_answer_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_short_answer_answers_value CHECK (CHAR_LENGTH(answer_value) > 0),
    CONSTRAINT chk_quiz_short_answer_answers_normalized CHECK (CHAR_LENGTH(normalized_value) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_essay_answer_guides (
    question_id BIGINT NOT NULL,
    model_answer TEXT NOT NULL,
    key_points JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (question_id),
    CONSTRAINT fk_quiz_essay_answer_guides_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_essay_answer_guides_model_answer CHECK (CHAR_LENGTH(model_answer) > 0),
    CONSTRAINT chk_quiz_essay_answer_guides_key_points CHECK (
        JSON_TYPE(key_points) = 'ARRAY' AND JSON_LENGTH(key_points) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_fill_in_the_blanks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    question_id BIGINT NOT NULL,
    blank_number INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_fill_in_the_blanks_public_id UNIQUE (public_id),
    CONSTRAINT uk_quiz_fill_in_the_blanks_question_number UNIQUE (question_id, blank_number),
    CONSTRAINT fk_quiz_fill_in_the_blanks_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_fill_in_the_blanks_number CHECK (blank_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_fill_in_the_blank_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blank_id BIGINT NOT NULL,
    answer_value TEXT NOT NULL,
    normalized_value VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_fill_in_the_blank_answers_blank FOREIGN KEY (blank_id) REFERENCES quiz_fill_in_the_blanks (id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_fill_in_the_blank_answers_value CHECK (CHAR_LENGTH(answer_value) > 0),
    CONSTRAINT chk_quiz_fill_in_the_blank_answers_normalized CHECK (CHAR_LENGTH(normalized_value) > 0)
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
    selected_choice_id BIGINT NULL,
    blank_id BIGINT NULL,
    answer_value TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_submitted_answers_attempt_blank UNIQUE (attempt_question_id, blank_id),
    CONSTRAINT fk_quiz_submitted_answers_attempt_question FOREIGN KEY (attempt_question_id) REFERENCES quiz_attempt_questions (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_submitted_answers_choice FOREIGN KEY (selected_choice_id) REFERENCES quiz_question_choices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_submitted_answers_blank FOREIGN KEY (blank_id) REFERENCES quiz_fill_in_the_blanks (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_submitted_answers_shape CHECK (
        (selected_choice_id IS NOT NULL AND blank_id IS NULL AND answer_value IS NULL)
        OR (
            selected_choice_id IS NULL
            AND answer_value IS NOT NULL
            AND CHAR_LENGTH(answer_value) > 0
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
