CREATE TABLE enrollments (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             student_id UUID NOT NULL,
                             course_id UUID NOT NULL,
                             course_title VARCHAR(200),
                             course_thumbnail_url VARCHAR(500),
                             instructor_name VARCHAR(200),
                             price_paid DECIMAL(10,2) NOT NULL DEFAULT 0,
                             status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                             total_lessons INT DEFAULT 0,
                             completed_lessons INT DEFAULT 0,
                             progress_percentage DOUBLE PRECISION DEFAULT 0,
                             enrolled_at TIMESTAMP NOT NULL DEFAULT NOW(),
                             last_accessed_at TIMESTAMP,
                             completed_at TIMESTAMP,
                             UNIQUE(student_id, course_id)
);

CREATE TABLE lesson_progress (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
                                 lesson_id UUID NOT NULL,
                                 section_id UUID NOT NULL,
                                 status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
                                 watched_seconds INT DEFAULT 0,
                                 total_seconds INT DEFAULT 0,
                                 started_at TIMESTAMP,
                                 completed_at TIMESTAMP,
                                 last_watched_at TIMESTAMP,
                                 UNIQUE(enrollment_id, lesson_id)
);

CREATE TABLE certificates (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              verification_code VARCHAR(100) NOT NULL UNIQUE,
                              enrollment_id UUID NOT NULL UNIQUE REFERENCES enrollments(id),
                              student_id UUID NOT NULL,
                              student_name VARCHAR(200),
                              course_id UUID NOT NULL,
                              course_title VARCHAR(200),
                              instructor_name VARCHAR(200),
                              pdf_url VARCHAR(500),
                              issued_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
CREATE INDEX idx_enrollments_status ON enrollments(status);
CREATE INDEX idx_lesson_progress_enrollment ON lesson_progress(enrollment_id);
CREATE INDEX idx_certificates_verification ON certificates(verification_code);