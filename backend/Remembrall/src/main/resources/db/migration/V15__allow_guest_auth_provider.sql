ALTER TABLE member DROP CONSTRAINT ck_member_auth_provider;

ALTER TABLE member ADD CONSTRAINT ck_member_auth_provider
    CHECK (auth_provider IN ('KAKAO', 'GUEST'));
