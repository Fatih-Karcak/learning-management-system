CREATE TABLE assignment_policies (
 lesson_id UUID PRIMARY KEY REFERENCES lessons(id) ON DELETE CASCADE,
 document TEXT NOT NULL, revision BIGINT NOT NULL DEFAULT 1, updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE assignment_submissions ADD COLUMN late BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assignment_submissions ADD COLUMN rubric_scores TEXT NOT NULL DEFAULT '{}';
ALTER TABLE assignment_submissions ADD COLUMN rubric_snapshot TEXT NOT NULL DEFAULT '[]';
ALTER TABLE assignment_revisions ADD COLUMN rubric_scores TEXT NOT NULL DEFAULT '{}';
ALTER TABLE assignment_revisions ADD COLUMN rubric_snapshot TEXT NOT NULL DEFAULT '[]';
