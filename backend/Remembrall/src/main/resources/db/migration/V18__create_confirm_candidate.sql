-- 되묻기(NEEDS_CONFIRMATION) 후보를 영속화한다 - 지금까지는 응답 한 번 나가고 사라져서
-- GET /api/personal-saves/{id}가 다시 보여줄 방법이 없었다. confirm_request는 질문+후보 리스트를
-- 그대로 JSON 직렬화한 것(save.dto.ConfirmRequestDto) - 내부 값으로 쿼리할 필요가 없어 jsonb 대신
-- text로 둔다.
CREATE TABLE confirm_candidate (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    candidate_id VARCHAR(50) NOT NULL,
    description TEXT,
    confirm_request TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMPTZ,
    CONSTRAINT uk_confirm_candidate_content_candidate UNIQUE (content_id, candidate_id),
    CONSTRAINT ck_confirm_candidate_status CHECK (status IN ('PENDING', 'ANSWERED'))
);
