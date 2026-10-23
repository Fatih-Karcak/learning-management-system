UPDATE courses SET total_enrollments = COALESCE(total_enrollments, 0),
                   average_rating = COALESCE(average_rating, 0),
                   total_reviews = COALESCE(total_reviews, 0);
ALTER TABLE courses ALTER COLUMN total_enrollments SET NOT NULL;
ALTER TABLE courses ALTER COLUMN average_rating SET NOT NULL;
ALTER TABLE courses ALTER COLUMN total_reviews SET NOT NULL;
CREATE TABLE processed_course_enrollments (
    enrollment_id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE
);
