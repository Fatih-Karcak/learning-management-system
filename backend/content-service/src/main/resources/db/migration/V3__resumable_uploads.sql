CREATE TABLE video_uploads (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, course_id UUID,
 file_name VARCHAR(200) NOT NULL, content_type VARCHAR(100) NOT NULL,
 total_bytes BIGINT NOT NULL CHECK(total_bytes > 0 AND total_bytes <= 2147483648),
 received_bytes BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','COMPLETED','CANCELLED')),
 expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW(), updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_video_upload_owner ON video_uploads(owner_id,status);
CREATE INDEX idx_video_upload_expiry ON video_uploads(expires_at);
CREATE TABLE video_upload_parts (
 upload_id UUID NOT NULL REFERENCES video_uploads(id) ON DELETE CASCADE,
 part_index INT NOT NULL CHECK(part_index >= 0), size_bytes INT NOT NULL,
 sha256 CHAR(64) NOT NULL, storage_key VARCHAR(500) NOT NULL,
 PRIMARY KEY(upload_id,part_index)
);
