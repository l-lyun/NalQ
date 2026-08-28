ALTER TABLE quiz_sets
    ADD COLUMN quiz_title VARCHAR(255) NULL AFTER learning_material_id;

UPDATE quiz_sets quiz_set
JOIN learning_materials material ON material.id = quiz_set.learning_material_id
SET quiz_set.quiz_title = CONCAT(LEFT(material.title, 252), ' 퀴즈');

ALTER TABLE quiz_sets
    MODIFY COLUMN quiz_title VARCHAR(255) NOT NULL,
    ADD CONSTRAINT chk_quiz_sets_quiz_title
        CHECK (CHAR_LENGTH(quiz_title) BETWEEN 1 AND 255);

CREATE TABLE home_visits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_home_visits_user_date UNIQUE (user_id, visit_date),
    CONSTRAINT fk_home_visits_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
