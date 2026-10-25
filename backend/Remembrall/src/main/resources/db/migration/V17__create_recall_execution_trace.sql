CREATE TABLE recall_execution_trace (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES recall_execution(id) ON DELETE CASCADE,
    seq INTEGER NOT NULL CHECK (seq > 0),
    step VARCHAR(30) NOT NULL,
    name VARCHAR(200),
    started_at TIMESTAMPTZ NOT NULL,
    duration_ms DOUBLE PRECISION NOT NULL CHECK (duration_ms >= 0 AND duration_ms < 'Infinity'::double precision),
    success BOOLEAN NOT NULL,
    error_message VARCHAR(4000),
    input_tokens INTEGER CHECK (input_tokens >= 0),
    output_tokens INTEGER CHECK (output_tokens >= 0),
    detail JSONB NOT NULL,
    CONSTRAINT uk_recall_execution_trace_seq UNIQUE (execution_id, seq),
    CONSTRAINT ck_recall_execution_trace_step CHECK (step IN (
        'CANDIDATE_PREPARATION', 'MODEL_CALL', 'TOOL_CALL', 'VALIDATION',
        'NOTIFICATION_CREATE', 'NOTIFICATION_SEND'
    ))
);
