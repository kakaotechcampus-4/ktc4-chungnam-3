ALTER TABLE recall_execution
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0);

ALTER TABLE recall_execution_trace
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1 CHECK (attempt > 0);
