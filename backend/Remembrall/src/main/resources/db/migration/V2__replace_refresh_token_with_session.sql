ALTER TABLE device
    RENAME COLUMN refresh_token_hash TO session_token_hash;

ALTER TABLE device
    RENAME COLUMN refresh_token_expires_at TO session_expires_at;

ALTER TABLE device
    RENAME CONSTRAINT uk_device_refresh_token_hash TO uk_device_session_token_hash;

ALTER TABLE device
    ALTER COLUMN session_token_hash DROP NOT NULL,
    ALTER COLUMN session_expires_at DROP NOT NULL;
