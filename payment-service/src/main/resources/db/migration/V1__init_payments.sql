CREATE TABLE payments (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          student_id UUID NOT NULL,
                          course_id UUID NOT NULL,
                          course_title VARCHAR(200),
                          course_thumbnail_url VARCHAR(500),
                          instructor_name VARCHAR(200),
                          stripe_payment_intent_id VARCHAR(255) UNIQUE,
                          stripe_charge_id VARCHAR(255) UNIQUE,
                          stripe_customer_id VARCHAR(255),
                          amount DECIMAL(10,2) NOT NULL,
                          currency VARCHAR(3) NOT NULL DEFAULT 'try',
                          status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                          fraud_check_passed BOOLEAN,
                          fraud_reason VARCHAR(500),
                          failure_reason VARCHAR(500),
                          receipt_url VARCHAR(500),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          completed_at TIMESTAMP
);

CREATE TABLE refunds (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         payment_id UUID NOT NULL REFERENCES payments(id),
                         stripe_refund_id VARCHAR(255),
                         amount DECIMAL(10,2) NOT NULL,
                         reason TEXT,
                         status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                         created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                         completed_at TIMESTAMP
);

CREATE INDEX idx_payments_student ON payments(student_id);
CREATE INDEX idx_payments_course ON payments(course_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_stripe_intent ON payments(stripe_payment_intent_id);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);