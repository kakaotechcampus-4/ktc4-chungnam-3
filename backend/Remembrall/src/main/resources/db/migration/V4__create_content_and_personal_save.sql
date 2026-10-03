CREATE TABLE content (
    id UUID PRIMARY KEY,
    video_id VARCHAR(32) NOT NULL,
    title VARCHAR(500),
    summary TEXT,
    category VARCHAR(50),
    source_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    analysis_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    analysis_version VARCHAR(30),
    last_analysis_error_code VARCHAR(50),
    metadata_fetched_at TIMESTAMPTZ,
    analysis_started_at TIMESTAMPTZ,
    analyzed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_video_id UNIQUE (video_id),
    CONSTRAINT ck_content_source_status CHECK (source_status IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT ck_content_analysis_status CHECK (analysis_status IN ('PENDING', 'ANALYZING', 'COMPLETED', 'PARTIAL_SUCCESS', 'FAILED'))
);

CREATE TABLE personal_save (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    content_id UUID NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_personal_save_member_content UNIQUE (member_id, content_id),
    CONSTRAINT fk_personal_save_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_save_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
);
