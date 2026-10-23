ALTER TABLE courses ADD COLUMN review_feedback VARCHAR(2000);
CREATE TABLE course_review_events (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    decision VARCHAR(30) NOT NULL,
    reason VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
