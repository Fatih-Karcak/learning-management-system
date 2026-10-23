CREATE TABLE login_throttles(email_hash VARCHAR(64) PRIMARY KEY,failures INT NOT NULL DEFAULT 0,window_started TIMESTAMP NOT NULL DEFAULT NOW(),blocked_until TIMESTAMP);
