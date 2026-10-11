CREATE TABLE member_consent (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    terms_version VARCHAR(50) NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_consent_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT ck_member_consent_type
        CHECK (consent_type IN ('PUBLIC_CANDIDATE_CONTRIBUTION', 'LOCATION_BASED_SERVICE'))
);

CREATE UNIQUE INDEX uk_member_consent_active
    ON member_consent (member_id, consent_type)
    WHERE withdrawn_at IS NULL;
