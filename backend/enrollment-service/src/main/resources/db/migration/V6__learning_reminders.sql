CREATE TABLE learning_reminder_preferences (
 student_id UUID PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 time_zone VARCHAR(100) NOT NULL DEFAULT 'UTC', local_hour INT NOT NULL DEFAULT 18 CHECK(local_hour BETWEEN 0 AND 23),
 next_run_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_learning_reminders_due ON learning_reminder_preferences(next_run_at) WHERE enabled;
CREATE TABLE learning_reminder_deliveries (
 student_id UUID NOT NULL, local_date DATE NOT NULL, event_id UUID NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT NOW(), PRIMARY KEY(student_id,local_date)
);
