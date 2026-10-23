CREATE TABLE course_collaborators (
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('INVITED', 'ACCEPTED')),
    invited_by UUID NOT NULL,
    invited_at TIMESTAMP NOT NULL DEFAULT now(),
    accepted_at TIMESTAMP,
    PRIMARY KEY (course_id, user_id)
);
CREATE INDEX course_collaborators_user ON course_collaborators(user_id, status);
CREATE TABLE course_templates (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    document TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX course_templates_owner ON course_templates(owner_id, created_at);
