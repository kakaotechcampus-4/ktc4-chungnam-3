CREATE TABLE member (
    id UUID PRIMARY KEY,
    auth_provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_auth_provider_user_id UNIQUE (auth_provider, provider_user_id),
    CONSTRAINT ck_member_auth_provider CHECK (auth_provider IN ('KAKAO'))
);

CREATE TABLE device (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    refresh_token_expires_at TIMESTAMPTZ NOT NULL,
    fcm_token VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_member UNIQUE (member_id),
    CONSTRAINT uk_device_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_device_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);
