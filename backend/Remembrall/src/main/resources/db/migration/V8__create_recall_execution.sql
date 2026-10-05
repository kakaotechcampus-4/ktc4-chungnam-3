CREATE TABLE recall_execution (
    id UUID PRIMARY KEY,
    trigger_id UUID NOT NULL,
    trigger_event_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result VARCHAR(20),
    proposal_type VARCHAR(30),
    proposal_payload JSONB,
    decision_summary TEXT,
    failure_code VARCHAR(50),
    agent_version VARCHAR(50) NOT NULL,
    event_occurred_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recall_execution_trigger
        FOREIGN KEY (trigger_id) REFERENCES trigger(id) ON DELETE CASCADE,
    CONSTRAINT uk_recall_execution_trigger_event UNIQUE (trigger_event_id),
    CONSTRAINT ck_recall_execution_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT')),
    CONSTRAINT ck_recall_execution_result
        CHECK (result IS NULL OR result IN ('PROPOSE', 'NO_ACTION')),
    CONSTRAINT ck_recall_execution_proposal_type
        CHECK (proposal_type IS NULL OR proposal_type IN ('PLACE', 'COURSE', 'CONTENT'))
);
