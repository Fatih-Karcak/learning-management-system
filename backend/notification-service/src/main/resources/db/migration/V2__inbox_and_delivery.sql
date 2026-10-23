CREATE TABLE notification_inbox(id UUID PRIMARY KEY,user_id UUID NOT NULL,subject VARCHAR(300) NOT NULL,message TEXT NOT NULL,path VARCHAR(1000),kind VARCHAR(50) NOT NULL,read_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE INDEX idx_inbox_user ON notification_inbox(user_id,created_at DESC);
CREATE TABLE notification_deliveries(id UUID PRIMARY KEY,recipient_id UUID NOT NULL,payload TEXT NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),last_error VARCHAR(100),created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE INDEX idx_notification_delivery ON notification_deliveries(status,next_attempt_at);
