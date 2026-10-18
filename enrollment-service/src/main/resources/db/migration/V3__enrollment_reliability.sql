ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS payment_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS
    ux_enrollments_payment_id
    ON enrollments(payment_id);

CREATE TABLE IF NOT EXISTS processed_refunds (
                                                 payment_id UUID PRIMARY KEY,
                                                 student_id UUID NOT NULL,
                                                 course_id UUID NOT NULL,
                                                 refund_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS enrollment_outbox (
                                                 id UUID PRIMARY KEY,
                                                 topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP NULL
    );

CREATE INDEX IF NOT EXISTS idx_enrollment_outbox_unsent
    ON enrollment_outbox(created_at)
    WHERE sent_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS
    ux_certificates_enrollment_id
    ON certificates(enrollment_id);