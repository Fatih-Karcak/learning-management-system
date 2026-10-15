CREATE TABLE categories (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(100) NOT NULL UNIQUE,
                            description TEXT,
                            icon_url VARCHAR(500),
                            parent_id BIGINT REFERENCES categories(id)
);

CREATE TABLE courses (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         title VARCHAR(200) NOT NULL,
                         description TEXT,
                         short_description VARCHAR(500),
                         thumbnail_url VARCHAR(500),
                         preview_video_url VARCHAR(500),
                         instructor_id UUID NOT NULL,
                         instructor_name VARCHAR(200),
                         category_id BIGINT REFERENCES categories(id),
                         status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                         level VARCHAR(30) DEFAULT 'ALL_LEVELS',
                         price DECIMAL(10,2) NOT NULL DEFAULT 0,
                         discount_price DECIMAL(10,2),
                         language VARCHAR(50) DEFAULT 'Türkçe',
                         duration_minutes INT,
                         total_enrollments INT DEFAULT 0,
                         average_rating DOUBLE PRECISION DEFAULT 0,
                         total_reviews INT DEFAULT 0,
                         created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                         updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                         published_at TIMESTAMP
);

CREATE TABLE course_requirements (
                                     course_id UUID REFERENCES courses(id) ON DELETE CASCADE,
                                     requirement TEXT
);

CREATE TABLE course_outcomes (
                                 course_id UUID REFERENCES courses(id) ON DELETE CASCADE,
                                 outcome TEXT
);

CREATE TABLE sections (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          title VARCHAR(200) NOT NULL,
                          description TEXT,
                          order_index INT NOT NULL DEFAULT 0,
                          course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE
);

CREATE TABLE lessons (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         title VARCHAR(200) NOT NULL,
                         content TEXT,
                         video_url VARCHAR(500),
                         duration_seconds INT,
                         type VARCHAR(30) NOT NULL,
                         order_index INT NOT NULL DEFAULT 0,
                         preview_allowed BOOLEAN DEFAULT FALSE,
                         section_id UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE
);

CREATE TABLE reviews (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                         student_id UUID NOT NULL,
                         student_name VARCHAR(200),
                         student_avatar_url VARCHAR(500),
                         rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                         comment TEXT,
                         created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                         UNIQUE(course_id, student_id)
);

-- Seed categories
INSERT INTO categories (name, description) VALUES
                                               ('Yazılım Geliştirme', 'Programlama ve yazılım mühendisliği'),
                                               ('Veri Bilimi', 'Makine öğrenmesi ve veri analizi'),
                                               ('Tasarım', 'UI/UX ve grafik tasarım'),
                                               ('İş Dünyası', 'Girişimcilik ve yönetim'),
                                               ('Kişisel Gelişim', 'Liderlik ve iletişim becerileri');

CREATE INDEX idx_courses_instructor ON courses(instructor_id);
CREATE INDEX idx_courses_status ON courses(status);
CREATE INDEX idx_courses_category ON courses(category_id);
CREATE INDEX idx_sections_course ON sections(course_id);
CREATE INDEX idx_lessons_section ON lessons(section_id);
CREATE INDEX idx_reviews_course ON reviews(course_id);