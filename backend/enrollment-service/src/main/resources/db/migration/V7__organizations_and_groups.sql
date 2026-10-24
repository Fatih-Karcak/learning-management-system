CREATE TABLE training_organizations (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, name VARCHAR(200) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_training_org_owner ON training_organizations(owner_id);
CREATE TABLE training_groups (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES training_organizations(id),
 name VARCHAR(200) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE training_group_members (
 group_id UUID NOT NULL REFERENCES training_groups(id), student_id UUID NOT NULL,
 email VARCHAR(254) NOT NULL, display_name VARCHAR(200) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'INVITED' CHECK(status IN('INVITED','ACTIVE','DECLINED')),
 created_at TIMESTAMP NOT NULL DEFAULT NOW(), replied_at TIMESTAMP,
 PRIMARY KEY(group_id,student_id)
);
CREATE INDEX idx_training_member_user ON training_group_members(student_id,status);
CREATE TABLE training_group_courses (
 group_id UUID NOT NULL REFERENCES training_groups(id), course_id UUID NOT NULL,
 assigned_by UUID NOT NULL, due_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT NOW(),
 PRIMARY KEY(group_id,course_id)
);
