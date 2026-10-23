CREATE TABLE admin_audit (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(60) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_admin_audit_created ON admin_audit(created_at DESC);
