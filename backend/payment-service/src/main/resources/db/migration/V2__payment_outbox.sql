CREATE TABLE payment_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP NULL,
    UNIQUE(topic, event_key)
);
CREATE INDEX idx_payment_outbox_unsent ON payment_outbox(created_at) WHERE sent_at IS NULL;
