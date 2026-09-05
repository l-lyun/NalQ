DELETE users
FROM users
LEFT JOIN learning_materials ON learning_materials.user_id = users.id
WHERE users.status = 'PENDING_ACTIVATION'
  AND learning_materials.id IS NULL;

UPDATE users
SET email = CONCAT('legacy-pending-', id, '@invalid.openmd.local'),
    normalized_email = CONCAT('legacy-pending-', id, '@invalid.openmd.local'),
    status = 'WITHDRAWN',
    withdrawn_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE status = 'PENDING_ACTIVATION';

-- A PENDING_ACTIVATION row with learning-material references is invalid legacy data.
-- Keep its FK identity as a withdrawn tombstone while releasing the original email for a fresh sign-up.
