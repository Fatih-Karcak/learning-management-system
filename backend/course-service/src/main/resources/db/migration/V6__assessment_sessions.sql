CREATE TABLE quiz_sessions(id UUID PRIMARY KEY,lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,student_id UUID NOT NULL,enrollment_id UUID NOT NULL,definition TEXT NOT NULL,expires_at TIMESTAMP NOT NULL,submitted BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE INDEX idx_quiz_sessions_student ON quiz_sessions(lesson_id,student_id);
ALTER TABLE quiz_attempts ADD COLUMN session_id UUID UNIQUE REFERENCES quiz_sessions(id);
ALTER TABLE quiz_attempts ADD COLUMN results TEXT NOT NULL DEFAULT '[]';
CREATE TABLE assignment_revisions(id UUID PRIMARY KEY,submission_id UUID NOT NULL REFERENCES assignment_submissions(id) ON DELETE CASCADE,content TEXT NOT NULL,attachment_url VARCHAR(2000),status VARCHAR(30) NOT NULL,score INT,feedback TEXT,created_at TIMESTAMP NOT NULL DEFAULT NOW());
