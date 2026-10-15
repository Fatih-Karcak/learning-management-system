CREATE TABLE notification_logs (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   recipient_user_id UUID NOT NULL,
                                   recipient_email VARCHAR(255),
                                   channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
                                   subject VARCHAR(500),
                                   event_type VARCHAR(50) NOT NULL,
                                   status VARCHAR(20) NOT NULL,
                                   failure_reason VARCHAR(1000),
                                   created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_recipient ON notification_logs(recipient_user_id);
CREATE INDEX idx_notification_event_type ON notification_logs(event_type);