CREATE TABLE course_workspaces (
 course_id UUID PRIMARY KEY REFERENCES courses(id) ON DELETE CASCADE,
 revision BIGINT NOT NULL DEFAULT 1,
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 document TEXT NOT NULL,
 feedback TEXT,
 updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
 submitted_at TIMESTAMP
);
CREATE TABLE course_versions (
 id UUID PRIMARY KEY,
 course_id UUID NOT NULL REFERENCES courses(id),
 version_number INT NOT NULL,
 document TEXT NOT NULL,
 actor_id UUID NOT NULL,
 published_at TIMESTAMP NOT NULL DEFAULT NOW(),
 UNIQUE(course_id,version_number)
);
CREATE TABLE lesson_materials (
 lesson_id UUID PRIMARY KEY REFERENCES lessons(id) ON DELETE CASCADE,
 document TEXT NOT NULL DEFAULT '[]'
);
