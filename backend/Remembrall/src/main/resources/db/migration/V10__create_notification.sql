CREATE TABLE notification (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    member_id UUID NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_message_id TEXT,
    failure_code VARCHAR(50),
    sent_at TIMESTAMPTZ,
    opened_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_execution
        FOREIGN KEY (execution_id) REFERENCES recall_execution(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT uk_notification_execution UNIQUE (execution_id),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_sent_at CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_notification_failure_code CHECK ((status = 'FAILED') = (failure_code IS NOT NULL))
);
