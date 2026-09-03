UPDATE quiz_sets target
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS generation_rank
    FROM quiz_sets
    WHERE status = 'GENERATING'
) ranked ON ranked.id = target.id
SET target.status = 'FAILED',
    target.failure_code = 'GENERATION_FAILED',
    target.updated_at = CURRENT_TIMESTAMP(6)
WHERE ranked.generation_rank > 1;

ALTER TABLE quiz_sets
    ADD COLUMN generation_model VARCHAR(100) NULL AFTER failure_code,
    ADD COLUMN prompt_version VARCHAR(100) NULL AFTER generation_model,
    ADD COLUMN generation_started_at TIMESTAMP(6) NULL AFTER prompt_version,
    ADD COLUMN active_generation_user_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'GENERATING' THEN user_id ELSE NULL END
        ) STORED AFTER generation_started_at,
    ADD CONSTRAINT uk_quiz_sets_active_generation_user UNIQUE (active_generation_user_id);
