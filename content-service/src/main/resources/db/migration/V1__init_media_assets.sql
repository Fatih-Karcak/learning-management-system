CREATE TABLE media_assets (
                              id UUID PRIMARY KEY,
                              owner_id UUID NOT NULL,
                              course_id UUID,
                              lesson_id UUID,
                              type VARCHAR(20) NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                              original_file_name VARCHAR(500) NOT NULL,
                              content_type VARCHAR(100),
                              size_bytes BIGINT,
                              raw_storage_key VARCHAR(1000) NOT NULL,
                              hls_master_key VARCHAR(1000),
                              thumbnail_key VARCHAR(1000),
                              duration_seconds INT,
                              failure_reason VARCHAR(1000),
                              created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_owner  ON media_assets(owner_id);
CREATE INDEX idx_media_course ON media_assets(course_id);
CREATE INDEX idx_media_lesson ON media_assets(lesson_id);