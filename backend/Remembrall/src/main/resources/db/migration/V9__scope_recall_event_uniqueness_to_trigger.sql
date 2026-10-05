ALTER TABLE recall_execution
    DROP CONSTRAINT uk_recall_execution_trigger_event;

ALTER TABLE recall_execution
    ADD CONSTRAINT uk_recall_execution_trigger_event UNIQUE (trigger_id, trigger_event_id);
