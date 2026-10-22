import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Copy,
  FileVideo,
  Image,
  Plus,
  Send,
  Trash2,
  Upload,
} from 'lucide-react';
import { api, body, lessonTypes, levels, money, safeUrl } from '../api';
import type { Category, Course, Lesson, Media, Page, Section } from '../types';
import VideoPlayer from '../VideoPlayer';
import {
  QuizEditor,
  AssignmentGrading,
  CourseStudents,
  CourseDiscussion,
} from './LearningFeatures';
import {
  Badge,
  Cover,
  Empty,
  ErrorBox,
  Field,
  Loading,
  Modal,
  PageTitle,
  Pagination,
  Submit,
  Success,
} from '../ui';

export function Instructor() {
  const [page, setPage] = useState(0);
  const courses = useQuery({
    queryKey: ['own-courses', page],
    queryFn: () => api<Page<Course>>(`/courses/mine?page=${page}&size=9&sort=createdAt,desc`),
  });
  return (
    <>
      <PageTitle
        eyebrow="BİLDİKLERİN DEĞERLİ"
        title="Eğitmen stüdyosu"
        description="Bilgini paylaş, birinin yolculuğuna ilham ol."
      >
        <Link className="button primary" to="/instructor/new">
          <Plus size={18} /> Yeni kurs oluştur
        </Link>
      </PageTitle>
      {courses.isPending ? (
        <Loading />
      ) : courses.error ? (
        <ErrorBox error={courses.error} retry={() => void courses.refetch()} />
      ) : courses.data?.content.length ? (
        <>
          <div className="course-grid">
            {courses.data.content.map((c) => (
              <article className="course-card" key={c.id}>
                <Cover course={c} />
                <div className="course-card-body">
                  <Badge value={c.status} />
                  <h3>{c.title}</h3>
                  <div className="card-bottom">
                    <span>{c.totalEnrollments || 0} öğrenci</span>
                    <strong>{money(c.discountPrice ?? c.price)}</strong>
                  </div>
                  <Link className="button secondary" to={`/instructor/${c.id}`}>
                    Kursu düzenle <ArrowRight size={16} />
                  </Link>
                </div>
              </article>
            ))}
          </div>
          <Pagination page={page} pages={courses.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="İlk kursunun hikâyesi burada başlasın."
          text="Taslağını oluştur, derslerini ekle ve hazır olduğunda yayınla."
        >
          <Link className="button primary" to="/instructor/new">
            <Plus size={17} /> Kurs oluştur
          </Link>
        </Empty>
      )}
    </>
  );
}
function CourseForm({ course, onSaved }: { course?: Course; onSaved: (c: Course) => void }) {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
  });
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const values = Object.fromEntries(f);
    const price = Number(f.get('price'));
    const discountPrice =
      f.get('discountPrice') === '' ? undefined : Number(f.get('discountPrice'));
    if (discountPrice !== undefined && discountPrice > price) {
      setError(new Error('İndirimli fiyat normal fiyattan yüksek olamaz.'));
      return;
    }
    setBusy(true);
    setError(null);
    const data = {
      ...values,
      price,
      discountPrice,
      categoryId: course ? undefined : Number(f.get('categoryId')),
      requirements: String(f.get('requirements') || '')
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean),
      outcomes: String(f.get('outcomes') || '')
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean),
    };
    try {
      const result = await api<Course>(course ? `/courses/${course.id}` : '/courses', {
        method: course ? 'PUT' : 'POST',
        body: body(data),
      });
      onSaved(result);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="panel editor-form" onSubmit={submit}>
      <h2>Kurs bilgileri</h2>
      <Field label="Kurs başlığı">
        <input
          name="title"
          minLength={10}
          maxLength={200}
          required
          defaultValue={course?.title}
          placeholder="Öğrencilerin ne öğrenecek?"
        />
      </Field>
      <Field label="Kısa açıklama" hint="Katalogda görünen açıklama. En fazla 500 karakter.">
        <textarea
          name="shortDescription"
          rows={2}
          required
          maxLength={500}
          defaultValue={course?.shortDescription}
        />
      </Field>
      <Field label="Kurs açıklaması" hint="Başlıklar ve listeler için Markdown kullanabilirsin.">
        <textarea name="description" rows={6} required defaultValue={course?.description} />
      </Field>
      <div className="form-grid">
        <Field label="Fiyat (₺)">
          <input
            name="price"
            type="number"
            min="0"
            step="0.01"
            required
            defaultValue={course?.price ?? 0}
          />
        </Field>
        <Field
          label="İndirimli fiyat (₺)"
          hint={
            course?.discountPrice != null
              ? 'İndirimi kaldırmak için normal fiyatı gir.'
              : 'İsteğe bağlı.'
          }
        >
          <input
            name="discountPrice"
            type="number"
            min="0"
            step="0.01"
            defaultValue={course?.discountPrice ?? ''}
          />
        </Field>
        {!course && (
          <Field label="Kategori">
            <select name="categoryId" required defaultValue="">
              <option value="" disabled>
                Kategori seç
              </option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
        )}
        <Field label="Seviye">
          <select name="level" defaultValue={course?.level || 'ALL_LEVELS'}>
            {Object.entries(levels).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        {!course && (
          <Field label="Eğitim dili">
            <input name="language" defaultValue="Türkçe" required />
          </Field>
        )}
      </div>
      <Field label="Kapak görseli adresi">
        <input
          name="thumbnailUrl"
          type="url"
          defaultValue={course?.thumbnailUrl}
          placeholder="https://…"
        />
      </Field>
      {!course && (
        <Field label="Tanıtım videosu adresi">
          <input name="previewVideoUrl" type="url" placeholder="https://…" />
        </Field>
      )}
      <div className="form-grid">
        <Field label="Ön koşullar" hint="Her satıra bir madde yaz.">
          <textarea name="requirements" rows={4} defaultValue={course?.requirements?.join('\n')} />
        </Field>
        <Field label="Kazanımlar" hint="Her satıra bir kazanım yaz.">
          <textarea name="outcomes" rows={4} defaultValue={course?.outcomes?.join('\n')} />
        </Field>
      </div>
      {categories.error && (
        <ErrorBox error={categories.error} retry={() => void categories.refetch()} />
      )}{' '}
      {!!error && <ErrorBox error={error} />}
      <Submit busy={busy}>{course ? 'Değişiklikleri kaydet' : 'Taslağı oluştur'}</Submit>
    </form>
  );
}
export function Editor() {
  const { courseId } = useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [saved, setSaved] = useState(false);
  const [addSection, setAddSection] = useState(false);
  const [lessonSection, setLessonSection] = useState<Section>();
  const [videoLesson, setVideoLesson] = useState<Lesson>();
  const [assessment, setAssessment] = useState<Lesson>();
  const [tool, setTool] = useState<'students' | 'discussion'>();
  const course = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api<Course>(`/courses/${courseId}`),
    enabled: !!courseId,
  });
  const reload = () => {
    void client.invalidateQueries({ queryKey: ['course', courseId] });
    void client.invalidateQueries({ queryKey: ['own-courses'] });
  };
  const publish = useMutation({
    mutationFn: () => api(`/courses/${courseId}/publish`, { method: 'POST' }),
    onSuccess: reload,
  });
  const archive = useMutation({
    mutationFn: () => api(`/courses/${courseId}/archive`, { method: 'POST' }),
    onSuccess: reload,
  });
  const remove = useMutation({
    mutationFn: ({ kind, id }: { kind: string; id: string }) =>
      api(`/${kind}/${id}`, { method: 'DELETE' }),
    onSuccess: reload,
  });
  if (courseId && course.isPending) return <Loading />;
  if (course.error) return <ErrorBox error={course.error} retry={() => void course.refetch()} />;
  const c = course.data;
  function onSaved(result: Course) {
    setSaved(true);
    client.setQueryData(['course', result.id], result);
    void client.invalidateQueries({ queryKey: ['own-courses'] });
    if (!courseId) navigate(`/instructor/${result.id}`, { replace: true });
  }
  return (
    <>
      <Link className="back-link" to="/instructor">
        <ArrowLeft size={16} /> Kurslarım
      </Link>
      <PageTitle
        eyebrow="EĞİTMEN STÜDYOSU"
        title={c ? 'Birlikte öğrenmeye değer.' : 'Bilgini bir kursa dönüştür.'}
        description={c?.title || 'Önce kurs bilgilerini ekle, ardından derslerini hazırla.'}
      >
        {c && (
          <div className="form-actions">
            <Link className="button secondary" to={`/courses/${c.id}`}>
              Ön izle
            </Link>
            {!['PUBLISHED', 'PENDING_REVIEW'].includes(c.status) && (
              <button
                className="button primary"
                disabled={publish.isPending || !c.sections?.some((s) => s.lessons?.length)}
                onClick={() => {
                  if (window.confirm('Kursunuz yönetici incelemesine gönderilsin mi?'))
                    publish.mutate();
                }}
              >
                <Send size={16} /> İncelemeye gönder
              </button>
            )}
            {c.status !== 'ARCHIVED' && (
              <button
                className="text-button danger"
                disabled={archive.isPending}
                onClick={() => {
                  if (
                    window.confirm(
                      'Kurs yeni kayıtlara kapatılsın mı? Mevcut öğrenciler erişmeye devam eder.',
                    )
                  )
                    archive.mutate();
                }}
              >
                Kursu arşivle
              </button>
            )}
          </div>
        )}
      </PageTitle>
      {saved && <Success>Kurs bilgileri kaydedildi.</Success>}
      {publish.error && <ErrorBox error={publish.error} />}
      {archive.error && <ErrorBox error={archive.error} />}
      {c && (
        <div className="notice">
          <Badge value={c.status} />
          {c.status === 'PENDING_REVIEW' && <p>Kurs yönetici onayı bekliyor.</p>}
          {c.reviewFeedback && <p>Yönetici geri bildirimi: {c.reviewFeedback}</p>}
        </div>
      )}
      <CourseForm key={c?.id || 'new'} course={c} onSaved={onSaved} />
      {c && (
        <section className="panel curriculum-editor">
          <div className="section-heading">
            <div>
              <h2>Kurs müfredatı</h2>
              <p>Bölümlerini ve derslerini adım adım oluştur.</p>
            </div>
            <button className="button secondary" onClick={() => setAddSection(true)}>
              <Plus size={17} /> Bölüm ekle
            </button>
          </div>
          {remove.error && <ErrorBox error={remove.error} />}{' '}
          {c.sections?.length ? (
            c.sections.map((s, i) => (
              <div className="editor-section" key={s.id}>
                <div className="editor-section-title">
                  <h3>
                    {i + 1}. {s.title}
                  </h3>
                  <div>
                    <button className="text-button" onClick={() => setLessonSection(s)}>
                      <Plus size={15} /> Ders ekle
                    </button>
                    <button
                      className="icon-button danger"
                      aria-label={`${s.title} bölümünü sil`}
                      disabled={remove.isPending}
                      onClick={() => {
                        if (window.confirm('Bölüm ve dersleri silinsin mi?'))
                          remove.mutate({ kind: 'sections', id: s.id });
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
                {s.lessons?.map((l) => (
                  <div className="lesson-row" key={l.id}>
                    <BookOpen size={16} />
                    <span>{l.title}</span>
                    <small>
                      {lessonTypes[l.type]}
                      {l.previewAllowed ? ' · Ön izleme' : ''}
                    </small>
                    {['QUIZ', 'ASSIGNMENT'].includes(l.type) && (
                      <button className="text-button" onClick={() => setAssessment(l)}>
                        {l.type === 'QUIZ' ? 'Testi düzenle' : 'Ödevleri değerlendir'}
                      </button>
                    )}
                    {['VIDEO', 'ARTICLE'].includes(l.type) && (
                      <button
                        className="text-button"
                        aria-label={`${l.title}: video ekle veya değiştir`}
                        onClick={() => setVideoLesson(l)}
                      >
                        <FileVideo size={16} /> {l.videoUrl ? 'Videoyu değiştir' : 'Video ekle'}
                      </button>
                    )}
                    <button
                      className="icon-button danger"
                      aria-label={`${l.title} dersini sil`}
                      disabled={remove.isPending}
                      onClick={() => {
                        if (window.confirm('Ders silinsin mi?'))
                          remove.mutate({ kind: 'lessons', id: l.id });
                      }}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            ))
          ) : (
            <Empty
              title="İlk bölümünü ekle"
              text="Kursu yayınlayabilmek için en az bir ders eklemelisin."
            />
          )}
          <Link className="inline-link" to={`/media?course=${c.id}`}>
            <Upload size={16} /> Kurs için medya yükle
          </Link>
        </section>
      )}
      {c && (
        <>
          <div className="form-actions">
            <button
              className="button secondary"
              onClick={() => setTool(tool === 'students' ? undefined : 'students')}
            >
              Öğrenciler ve ilerleme
            </button>
            <button
              className="button secondary"
              onClick={() => setTool(tool === 'discussion' ? undefined : 'discussion')}
            >
              Kurs soru-cevap
            </button>
          </div>
          {tool === 'students' && <CourseStudents courseId={c.id} />}{' '}
          {tool === 'discussion' && (
            <CourseDiscussion courseId={c.id} instructorId={c.instructorId} />
          )}
        </>
      )}
      {assessment && c && (
        <Modal
          title={`${assessment.title} — ${assessment.type === 'QUIZ' ? 'Test hazırlama' : 'Ödev değerlendirme'}`}
          onClose={() => setAssessment(undefined)}
        >
          {assessment.type === 'QUIZ' ? (
            <QuizEditor courseId={c.id} lessonId={assessment.id} />
          ) : (
            <AssignmentGrading courseId={c.id} lessonId={assessment.id} />
          )}
        </Modal>
      )}
      {videoLesson && c && (
        <Modal
          title={`${videoLesson.title} — Ders videosu`}
          onClose={() => setVideoLesson(undefined)}
        >
          <LessonVideoForm
            courseId={c.id}
            lesson={videoLesson}
            onDone={() => {
              setVideoLesson(undefined);
              reload();
            }}
          />
        </Modal>
      )}
      {addSection && (
        <Modal title="Yeni bölüm" onClose={() => setAddSection(false)}>
          <CurriculumForm
            path="sections"
            courseId={courseId!}
            onDone={() => {
              setAddSection(false);
              reload();
            }}
          />
        </Modal>
      )}
      {lessonSection && (
        <Modal
          title={`${lessonSection.title} — Yeni ders`}
          onClose={() => setLessonSection(undefined)}
        >
          <CurriculumForm
            path="lessons"
            courseId={courseId!}
            sectionId={lessonSection.id}
            onDone={() => {
              setLessonSection(undefined);
              reload();
            }}
          />
        </Modal>
      )}
    </>
  );
}
function VideoFields({
  courseId,
  initialUrl = '',
  initialDuration = 0,
}: {
  courseId: string;
  initialUrl?: string;
  initialDuration?: number;
}) {
  const [page, setPage] = useState(0);
  const [url, setUrl] = useState(initialUrl);
  const [duration, setDuration] = useState(initialDuration);
  const media = useQuery({
    queryKey: ['video-picker', page],
    queryFn: () => api<Page<Media>>(`/media/my?size=12&page=${page}&sort=createdAt,desc`),
    refetchInterval: (q) =>
      q.state.data?.content.some((m) => m.status === 'PROCESSING') ? 3000 : false,
  });
  const videos =
    media.data?.content.filter(
      (m) => m.type === 'VIDEO' && m.status === 'READY' && (!m.courseId || m.courseId === courseId),
    ) || [];
  return (
    <>
      <Field
        label="Medya kitaplığından video seç"
        hint="Hazır videolar listelenir. Seçilen video bu kursun öğrencilerine açılır."
      >
        <select
          value={videos.some((m) => m.streamUrl === url) ? url : ''}
          onChange={(e) => {
            const video = videos.find((m) => m.streamUrl === e.target.value);
            if (video?.streamUrl) {
              setUrl(video.streamUrl);
              setDuration(video.durationSeconds || 0);
            }
          }}
        >
          <option value="">Video seçin</option>
          {videos.map((m) => (
            <option key={m.id} value={m.streamUrl}>
              {m.originalFileName}
            </option>
          ))}
        </select>
      </Field>
      {media.isPending && <Loading />}
      {media.error && <ErrorBox error={media.error} retry={() => void media.refetch()} />}
      {media.data && <Pagination page={page} pages={media.data.totalPages} onChange={setPage} />}
      <Field
        label="Video adresi"
        hint="Kitaplıktan seçebilir veya doğrudan bir video adresi girebilirsiniz."
      >
        <input
          name="videoUrl"
          required
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="/api/v1/media/…/manifest.m3u8"
        />
      </Field>
      <Field label="Süre (saniye)">
        <input
          name="durationSeconds"
          type="number"
          min="0"
          required
          value={duration}
          onChange={(e) => setDuration(Number(e.target.value))}
        />
      </Field>
    </>
  );
}
function LessonVideoForm({
  courseId,
  lesson,
  onDone,
}: {
  courseId: string;
  lesson: Lesson;
  onDone: () => void;
}) {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        setBusy(true);
        setError(null);
        try {
          await api(`/lessons/${lesson.id}/video`, {
            method: 'PUT',
            body: body({
              videoUrl: data.get('videoUrl'),
              durationSeconds: Number(data.get('durationSeconds') || 0),
            }),
          });
          onDone();
        } catch (err) {
          setError(err);
        } finally {
          setBusy(false);
        }
      }}
    >
      <p className="muted">Video bağlandığında ders video türüne geçer. Yazılı içerik korunur.</p>
      <VideoFields
        courseId={courseId}
        initialUrl={lesson.videoUrl}
        initialDuration={lesson.durationSeconds}
      />
      {!!error && <ErrorBox error={error} />}
      <Submit busy={busy}>Videoyu derse bağla</Submit>
    </form>
  );
}
function CurriculumForm({
  path,
  courseId,
  sectionId,
  onDone,
}: {
  path: 'sections' | 'lessons';
  courseId: string;
  sectionId?: string;
  onDone: () => void;
}) {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [type, setType] = useState('ARTICLE');
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const f = new FormData(e.currentTarget);
    const data = {
      ...Object.fromEntries(f),
      courseId,
      sectionId,
      orderIndex: Number(f.get('orderIndex') || 0),
      durationSeconds: Number(f.get('durationSeconds') || 0),
      previewAllowed: f.get('previewAllowed') === 'on',
    };
    try {
      await api(`/${path}`, { method: 'POST', body: body(data) });
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      <Field label="Başlık">
        <input name="title" required />
      </Field>
      <Field label="Sıra">
        <input name="orderIndex" type="number" min="0" defaultValue={0} />
      </Field>
      {path === 'sections' ? (
        <Field label="Açıklama">
          <textarea name="description" rows={3} />
        </Field>
      ) : (
        <>
          <Field label="Ders türü">
            <select name="type" value={type} onChange={(e) => setType(e.target.value)}>
              {Object.entries(lessonTypes).map(([k, v]) => (
                <option value={k} key={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Ders içeriği" hint="Markdown desteklenir.">
            <textarea name="content" rows={5} />
          </Field>
          {type === 'VIDEO' && <VideoFields courseId={courseId} />}
          <label className="checkbox-field">
            <input name="previewAllowed" type="checkbox" /> Bu ders herkese açık ön izlenebilir
          </label>
        </>
      )}
      {!!error && <ErrorBox error={error} />}
      <Submit busy={busy}>Ekle</Submit>
    </form>
  );
}
export function MediaLibrary() {
  const client = useQueryClient();
  const [params] = useSearchParams();
  const [page, setPage] = useState(0);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState('');
  const [kind, setKind] = useState('image');
  const [selectedCourse, setSelectedCourse] = useState(params.get('course') || '');
  const [inspect, setInspect] = useState<string>();
  const media = useQuery({
    queryKey: ['media', page],
    queryFn: () => api<Page<Media>>(`/media/my?size=12&page=${page}&sort=createdAt,desc`),
    refetchInterval: (q) =>
      q.state.data?.content.some((m) => m.status === 'PROCESSING') ? 10000 : false,
  });
  const courses = useQuery({
    queryKey: ['media-courses'],
    queryFn: () => api<Page<Course>>('/courses/mine?size=100'),
  });
  const course = useQuery({
    queryKey: ['course', selectedCourse],
    queryFn: () => api<Course>(`/courses/${selectedCourse}`),
    enabled: !!selectedCourse,
  });
  const details = useQuery({
    queryKey: ['media-item', inspect],
    queryFn: () => api<Media>(`/media/${inspect}`),
    enabled: !!inspect,
    refetchInterval: (q) => (q.state.data?.status === 'PROCESSING' ? 3000 : false),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/media/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['media'] });
      setSuccess('Dosya silindi.');
    },
  });
  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    const file = f.get('file');
    if (!(file instanceof File) || !file.size) return;
    const max = kind === 'image' ? 10 * 1024 * 1024 : 2 * 1024 * 1024 * 1024;
    if (file.size > max) {
      setError(
        new Error(
          kind === 'image' ? 'Görsel en fazla 10 MB olabilir.' : 'Video en fazla 2 GB olabilir.',
        ),
      );
      return;
    }
    if (!String(f.get('courseId'))) f.delete('courseId');
    if (!String(f.get('lessonId'))) f.delete('lessonId');
    setError(null);
    setSuccess('');
    setBusy(true);
    try {
      const uploaded = await api<Media>(`/media/${kind}`, { method: 'POST', body: f });
      void client.invalidateQueries({ queryKey: ['media'] });
      void client.invalidateQueries({ queryKey: ['video-picker'] });
      if (kind === 'video' && f.get('lessonId')) {
        try {
          await api(`/lessons/${f.get('lessonId')}/video`, {
            method: 'PUT',
            body: body({
              videoUrl: uploaded.streamUrl,
              durationSeconds: uploaded.durationSeconds || 0,
            }),
          });
          void client.invalidateQueries({ queryKey: ['course', selectedCourse] });
        } catch (err) {
          throw new Error(
            `Video yüklendi ancak derse bağlanamadı. Eğitmen stüdyosunda dersin Video ekle düğmesini kullanın. ${err instanceof Error ? err.message : ''}`,
          );
        }
      }
      setSuccess(
        kind === 'video'
          ? f.get('lessonId')
            ? 'Video derse bağlandı. İşleme tamamlandığında öğrenciler izleyebilir.'
            : 'Video yüklendi. Hazır olduğunda eğitmen stüdyosunda bir derse ekleyin.'
          : 'Görsel yüklendi.',
      );
      form.reset();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function copy(url?: string) {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setSuccess('Medya bağlantısı kopyalandı.');
    } catch {
      setError(
        new Error(
          'Bağlantı kopyalanamadı. Dosya ayrıntısından bağlantıyı seçip kopyalayabilirsiniz.',
        ),
      );
    }
  }
  return (
    <>
      <PageTitle
        eyebrow="İÇERİĞİNE HAYAT VER"
        title="Medya kitaplığı"
        description="Görsellerini ve videolarını yükle, kurslarında kullan."
      />
      <section className="panel">
        <h2>
          <Upload size={20} /> Yeni dosya yükle
        </h2>
        <form onSubmit={upload}>
          <div className="form-grid">
            <Field label="Dosya türü">
              <select value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="image">Görsel · En fazla 10 MB</option>
                <option value="video">Video · En fazla 2 GB</option>
              </select>
            </Field>
            <Field label="İlgili kurs">
              <select
                name="courseId"
                value={selectedCourse}
                onChange={(e) => setSelectedCourse(e.target.value)}
              >
                <option value="">Kursa bağlı değil</option>
                {courses.data?.content.map((c) => (
                  <option value={c.id} key={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label="İlgili ders (isteğe bağlı)"
              hint={
                kind === 'video'
                  ? 'Seçerseniz video bu derse otomatik bağlanır. Yazılı içerik korunur.'
                  : undefined
              }
            >
              <select name="lessonId" key={`${selectedCourse}-${kind}`}>
                <option value="">Ders seçilmedi</option>
                {course.data?.sections
                  ?.flatMap((s) => s.lessons || [])
                  .filter((l) => kind !== 'video' || ['VIDEO', 'ARTICLE'].includes(l.type))
                  .map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.title}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Dosya seç">
              <input
                key={kind}
                name="file"
                type="file"
                accept={kind === 'image' ? 'image/*' : 'video/*'}
                required
              />
            </Field>
          </div>
          {!!error && <ErrorBox error={error} />}
          <Submit busy={busy}>{busy ? 'Dosya yükleniyor…' : 'Dosyayı yükle'}</Submit>
        </form>
        {courses.error && <ErrorBox error={courses.error} />}{' '}
        {course.error && <ErrorBox error={course.error} />}
      </section>
      {success && <Success>{success}</Success>}
      {remove.error && <ErrorBox error={remove.error} />}
      <div className="section-heading">
        <h2>Dosyalarım</h2>
        <button className="text-button" onClick={() => void media.refetch()}>
          Yenile
        </button>
      </div>
      {media.isPending ? (
        <Loading />
      ) : media.error ? (
        <ErrorBox error={media.error} retry={() => void media.refetch()} />
      ) : media.data?.content.length ? (
        <>
          <div className="media-grid">
            {media.data.content.map((m) => (
              <article className="panel media-card" key={m.id}>
                <div className="media-preview">
                  {safeUrl(m.thumbnailUrl || (m.type === 'IMAGE' ? m.streamUrl : undefined)) ? (
                    <img src={safeUrl(m.thumbnailUrl || m.streamUrl)} alt={m.originalFileName} />
                  ) : m.type === 'VIDEO' ? (
                    <FileVideo size={36} />
                  ) : (
                    <Image size={36} />
                  )}
                </div>
                <Badge value={m.status} />
                <h3>{m.originalFileName}</h3>
                <p>
                  {(m.sizeBytes / 1024 / 1024).toFixed(1)} MB{' '}
                  {m.durationSeconds ? ` · ${Math.ceil(m.durationSeconds / 60)} dk` : ''}
                </p>
                {m.failureReason && <p className="error-text">{m.failureReason}</p>}
                <div className="form-actions">
                  <button className="button secondary small" onClick={() => setInspect(m.id)}>
                    Ayrıntı
                  </button>
                  <button
                    className="icon-button"
                    aria-label="Medya bağlantısını kopyala"
                    disabled={m.status !== 'READY'}
                    onClick={() => void copy(m.streamUrl)}
                  >
                    <Copy size={17} />
                  </button>
                  <button
                    className="icon-button danger"
                    aria-label={`${m.originalFileName} dosyasını sil`}
                    disabled={remove.isPending}
                    onClick={() => {
                      if (
                        window.confirm(
                          'Dosya kalıcı olarak silinsin mi? Bağlı dersler etkilenebilir.',
                        )
                      )
                        remove.mutate(m.id);
                    }}
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
              </article>
            ))}
          </div>
          <Pagination page={page} pages={media.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="İçeriklerin için yer hazır."
          text="İlk görselini veya videonu yükleyerek başla."
        />
      )}
      {inspect && (
        <Modal title="Dosya ayrıntısı" onClose={() => setInspect(undefined)}>
          {details.isPending ? (
            <Loading />
          ) : details.error ? (
            <ErrorBox error={details.error} />
          ) : (
            details.data && (
              <>
                <h3>{details.data.originalFileName}</h3>
                <Badge value={details.data.status} />
                {details.data.type === 'VIDEO' &&
                  details.data.status === 'READY' &&
                  details.data.streamUrl && <VideoPlayer src={details.data.streamUrl} />}
                {details.data.status === 'PROCESSING' && (
                  <p role="status">Video işleniyor. Hazır olduğunda oynatılabilir.</p>
                )}
                {details.data.failureReason && (
                  <ErrorBox error={new Error(details.data.failureReason)} />
                )}
                <Field label="Medya bağlantısı">
                  <input
                    readOnly
                    value={details.data.streamUrl || ''}
                    onFocus={(e) => e.currentTarget.select()}
                  />
                </Field>
                <p className="muted">
                  Video bağlantısını ders oluştururken kullanabilirsin. Görsel bağlantıları belirli
                  bir süre için geçerlidir.
                </p>
              </>
            )
          )}
        </Modal>
      )}
    </>
  );
}
