CREATE TABLE user_sessions (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),refresh_hash VARCHAR(64) UNIQUE NOT NULL,
 expires_at TIMESTAMP NOT NULL,revoked BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMP NOT NULL DEFAULT NOW(),last_seen_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_user_sessions ON user_sessions(user_id,revoked);
CREATE TABLE used_refresh_tokens(token_hash VARCHAR(64) PRIMARY KEY,session_id UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE);
CREATE TABLE user_mfa(user_id UUID PRIMARY KEY REFERENCES users(id),secret TEXT NOT NULL,enabled BOOLEAN NOT NULL DEFAULT FALSE,last_step BIGINT NOT NULL DEFAULT -1,failed_attempts INT NOT NULL DEFAULT 0,locked_until TIMESTAMP);
CREATE TABLE account_preferences(user_id UUID PRIMARY KEY REFERENCES users(id),learning_emails BOOLEAN NOT NULL DEFAULT TRUE,announcements BOOLEAN NOT NULL DEFAULT TRUE,weekly_target_minutes INT NOT NULL DEFAULT 60 CHECK(weekly_target_minutes BETWEEN 10 AND 3000));
CREATE TABLE support_tickets(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),subject VARCHAR(200) NOT NULL,category VARCHAR(30) NOT NULL,content VARCHAR(10000) NOT NULL,course_id UUID,status VARCHAR(30) NOT NULL DEFAULT 'OPEN',created_at TIMESTAMP NOT NULL DEFAULT NOW(),updated_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE TABLE support_replies(id UUID PRIMARY KEY,ticket_id UUID NOT NULL REFERENCES support_tickets(id),author_id UUID NOT NULL,staff BOOLEAN NOT NULL,content VARCHAR(10000) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE TABLE privacy_requests(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),kind VARCHAR(30) NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'OPEN',reason VARCHAR(2000),created_at TIMESTAMP NOT NULL DEFAULT NOW(),resolved_at TIMESTAMP);
ALTER TABLE users ADD COLUMN verification_expires_at TIMESTAMP;
UPDATE users SET verification_expires_at=NOW()+INTERVAL '24 hours' WHERE email_verification_token IS NOT NULL;
