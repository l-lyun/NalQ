ALTER TABLE push_devices DROP CHECK chk_push_devices_shape;

ALTER TABLE push_devices ADD CONSTRAINT chk_push_devices_shape CHECK (
    (
        status = 'ACTIVE'
        AND user_id IS NOT NULL
        AND session_id IS NOT NULL
        AND binding_id IS NOT NULL
        AND platform IN ('IOS', 'ANDROID')
        AND (
            provider = 'EXPO'
            OR (provider = 'FCM' AND platform = 'IOS')
        )
        AND push_token IS NOT NULL
        AND push_token_digest IS NOT NULL
        AND inactive_at IS NULL
    )
    OR (
        status IN ('DISABLED', 'REVOKED')
        AND session_id IS NULL
        AND binding_id IS NULL
        AND push_token IS NULL
        AND push_token_digest IS NULL
        AND inactive_at IS NOT NULL
    )
);
