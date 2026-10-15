CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,
                       bio TEXT,
                       avatar_url VARCHAR(500),
                       phone_number VARCHAR(20),
                       role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
                       status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
                       email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                       email_verification_token VARCHAR(255),
                       password_reset_token VARCHAR(255),
                       password_reset_token_expiry TIMESTAMP,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);