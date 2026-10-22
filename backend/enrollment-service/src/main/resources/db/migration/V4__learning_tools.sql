ALTER TABLE lesson_progress ADD COLUMN position_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD COLUMN credited_millis BIGINT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD COLUMN watched_ranges TEXT;
ALTER TABLE lesson_progress ADD COLUMN last_playback_at TIMESTAMP;
ALTER TABLE lesson_progress ADD COLUMN last_playback_position DOUBLE PRECISION;
ALTER TABLE lesson_progress ADD COLUMN playback_active BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE lesson_progress SET position_seconds=COALESCE(watched_seconds,0);
CREATE TABLE lesson_notes (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL,
    position_seconds INT NOT NULL DEFAULT 0 CHECK(position_seconds>=0),
    content VARCHAR(10000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_lesson_notes_owner ON lesson_notes(enrollment_id,lesson_id,position_seconds);
CREATE TABLE enrollment_admin_audit (
    id UUID PRIMARY KEY, actor_id UUID NOT NULL, enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    action VARCHAR(30) NOT NULL, reason VARCHAR(1000) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
