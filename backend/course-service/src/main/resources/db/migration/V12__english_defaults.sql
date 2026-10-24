-- Translate only the original, unchanged seed definitions. Preserve authored categories.
WITH translations(old_name, old_description, new_name, new_description) AS (
 VALUES
 ('Yazılım Geliştirme', 'Programlama ve yazılım mühendisliği', 'Software Development', 'Programming and software engineering'),
 ('Veri Bilimi', 'Makine öğrenmesi ve veri analizi', 'Data Science', 'Machine learning and data analysis'),
 ('Tasarım', 'UI/UX ve grafik tasarım', 'Design', 'UI/UX and graphic design'),
 ('İş Dünyası', 'Girişimcilik ve yönetim', 'Business', 'Entrepreneurship and management'),
 ('Kişisel Gelişim', 'Liderlik ve iletişim becerileri', 'Personal Development', 'Leadership and communication skills')
)
UPDATE categories c SET name=t.new_name, description=t.new_description
FROM translations t
WHERE c.name=t.old_name AND c.description=t.old_description
 AND NOT EXISTS (SELECT 1 FROM categories existing WHERE existing.name=t.new_name);

-- Existing course language values describe authored content and are preserved.
ALTER TABLE courses ALTER COLUMN language SET DEFAULT 'English';
