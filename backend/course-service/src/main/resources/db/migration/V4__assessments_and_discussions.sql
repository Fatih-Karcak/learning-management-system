CREATE TABLE lesson_quizzes (
    lesson_id UUID PRIMARY KEY REFERENCES lessons(id) ON DELETE CASCADE,
    definition TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    answers TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_quiz_attempt_student ON quiz_attempts(lesson_id,student_id,created_at DESC);
CREATE TABLE assignment_submissions (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    student_name VARCHAR(300) NOT NULL,
    enrollment_id UUID NOT NULL,
    content TEXT NOT NULL,
    attachment_url VARCHAR(2000),
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    score INT CHECK(score BETWEEN 0 AND 100),
    feedback VARCHAR(10000),
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    graded_at TIMESTAMP,
    UNIQUE(lesson_id,student_id)
);
CREATE TABLE course_questions (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lesson_id UUID REFERENCES lessons(id) ON DELETE SET NULL,
    author_id UUID NOT NULL,
    author_name VARCHAR(300) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    accepted_reply_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE question_replies (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES course_questions(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    author_name VARCHAR(300) NOT NULL,
    instructor BOOLEAN NOT NULL DEFAULT FALSE,
    content VARCHAR(10000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_course_questions ON course_questions(course_id,created_at DESC);
CREATE INDEX idx_question_replies ON question_replies(question_id,created_at);
