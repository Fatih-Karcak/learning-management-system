CREATE TABLE course_learning_rules (
 course_id UUID PRIMARY KEY REFERENCES courses(id), revision BIGINT NOT NULL DEFAULT 1,
 sequential BOOLEAN NOT NULL DEFAULT FALSE, release_days INT NOT NULL DEFAULT 0 CHECK(release_days BETWEEN 0 AND 365),
 effective_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE course_prerequisites (
 course_id UUID NOT NULL REFERENCES courses(id), prerequisite_id UUID NOT NULL REFERENCES courses(id),
 PRIMARY KEY(course_id,prerequisite_id), CHECK(course_id<>prerequisite_id)
);
CREATE TABLE learning_paths (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(4000) NOT NULL,
 published BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT NOW(), updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE learning_path_steps (
 path_id UUID NOT NULL REFERENCES learning_paths(id) ON DELETE CASCADE,
 course_id UUID NOT NULL REFERENCES courses(id), position INT NOT NULL,
 PRIMARY KEY(path_id,course_id), UNIQUE(path_id,position)
);
