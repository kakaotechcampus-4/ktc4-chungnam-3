CREATE INDEX idx_notification_member_sent_at
    ON notification (member_id, sent_at)
    WHERE status = 'SENT';
