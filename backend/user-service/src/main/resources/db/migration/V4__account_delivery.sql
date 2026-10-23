CREATE TABLE platform_outbox(id UUID PRIMARY KEY,topic VARCHAR(100) NOT NULL,payload TEXT NOT NULL,attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),last_error VARCHAR(200),created_at TIMESTAMP NOT NULL DEFAULT NOW(),delivered_at TIMESTAMP);
CREATE TABLE password_reset_requests(email_hash VARCHAR(64) PRIMARY KEY,last_requested TIMESTAMP NOT NULL);
