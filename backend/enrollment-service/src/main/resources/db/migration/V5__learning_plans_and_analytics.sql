CREATE TABLE study_daily(student_id UUID NOT NULL,day DATE NOT NULL,watched_millis BIGINT NOT NULL DEFAULT 0,PRIMARY KEY(student_id,day));
CREATE FUNCTION track_study_time() RETURNS TRIGGER AS $$
DECLARE delta BIGINT; student UUID;
BEGIN
 delta:=GREATEST(0,NEW.credited_millis-COALESCE(OLD.credited_millis,0));
 IF delta>0 THEN
  SELECT student_id INTO student FROM enrollments WHERE id=NEW.enrollment_id;
  INSERT INTO study_daily(student_id,day,watched_millis) VALUES(student,CURRENT_DATE,delta)
  ON CONFLICT(student_id,day) DO UPDATE SET watched_millis=study_daily.watched_millis+EXCLUDED.watched_millis;
 END IF;
 RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER study_time_after_progress AFTER INSERT OR UPDATE ON lesson_progress FOR EACH ROW EXECUTE FUNCTION track_study_time();
CREATE TABLE lesson_bookmarks(id UUID PRIMARY KEY,enrollment_id UUID NOT NULL REFERENCES enrollments(id),lesson_id UUID NOT NULL,title VARCHAR(200) NOT NULL,position_seconds INT NOT NULL DEFAULT 0 CHECK(position_seconds>=0),created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE INDEX idx_lesson_bookmarks ON lesson_bookmarks(enrollment_id,lesson_id);
CREATE TABLE learning_tasks(id UUID PRIMARY KEY,student_id UUID NOT NULL,title VARCHAR(200) NOT NULL,due_date DATE NOT NULL,enrollment_id UUID REFERENCES enrollments(id),done BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMP NOT NULL DEFAULT NOW());
CREATE INDEX idx_learning_tasks ON learning_tasks(student_id,due_date);
