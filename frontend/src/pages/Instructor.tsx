import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Copy, FileVideo, Image, Plus, Trash2, Upload } from 'lucide-react';
import { api, body, levels, money, safeUrl } from '../api';
import type { Category, Course, Media, Page } from '../types';
import VideoPlayer from '../VideoPlayer';
import CourseWorkspace from './Workspace';
import { VideoOperations } from './Platform';
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
      <Field label="Bu kurs kimler için?">
        <textarea
          name="targetAudience"
          rows={3}
          maxLength={2000}
          defaultValue={course?.targetAudience}
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
  return courseId ? <CourseWorkspace courseId={courseId} /> : <CreateCourseView />;
}
function CreateCourseView() {
  const navigate = useNavigate();
  const client = useQueryClient();
  return (
    <>
      <Link className="back-link" to="/instructor">
        <ArrowLeft size={16} /> Kurslarım
      </Link>
      <PageTitle
        eyebrow="EĞİTMEN STÜDYOSU"
        title="Bilgini bir kursa dönüştür."
        description="Önce kurs bilgilerini ekle, ardından derslerini taslakta hazırla."
      />
      <CourseForm
        onSaved={(course) => {
          client.setQueryData(['course', course.id], course);
          void client.invalidateQueries({ queryKey: ['own-courses'] });
          navigate(`/instructor/${course.id}`, { replace: true });
        }}
      />
    </>
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
    const values = new FormData(form);
    const files = values.getAll('file').filter((f): f is File => f instanceof File && f.size > 0);
    if (!files.length) return;
    if (files.length > 20) {
      setError(new Error('Tek seferde en fazla 20 dosya yükleyin.'));
      return;
    }
    if (files.length > 1 && values.get('lessonId')) {
      setError(
        new Error(
          'Birden çok dosya için ders seçimini kaldırın. Videoları daha sonra taslaktaki derslere ekleyin.',
        ),
      );
      return;
    }
    const max = kind === 'image' ? 10 * 1024 * 1024 : 2 * 1024 * 1024 * 1024;
    if (files.some((f) => f.size > max)) {
      setError(new Error('Dosyalardan biri boyut sınırını aşıyor.'));
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess('');
    let uploadedCount = 0;
    try {
      for (const file of files) {
        setSuccess(`${uploadedCount + 1}/${files.length}: ${file.name} yükleniyor…`);
        const data = new FormData();
        data.set('file', file);
        if (values.get('courseId')) data.set('courseId', String(values.get('courseId')));
        const uploaded = await api<Media>(`/media/${kind}`, { method: 'POST', body: data });
        uploadedCount++;
        if (kind === 'video' && values.get('lessonId')) {
          const id = String(values.get('lessonId'));
          const workspace = await api<import('../types').Workspace>(
            `/courses/${selectedCourse}/workspace`,
          );
          const lesson = workspace.document.course.sections
            ?.flatMap((s) => s.lessons)
            .find((l) => l.id === id);
          if (!lesson)
            throw new Error('Ders taslakta bulunamadı. Videoyu kurs düzenleyicisinden seçin.');
          lesson.videoUrl = uploaded.streamUrl;
          lesson.durationSeconds = uploaded.durationSeconds || 0;
          lesson.type = 'VIDEO';
          await api(`/courses/${selectedCourse}/workspace`, {
            method: 'PUT',
            body: body({ revision: workspace.revision, document: workspace.document }),
          });
          void client.invalidateQueries({ queryKey: ['workspace', selectedCourse] });
        }
        void client.invalidateQueries({ queryKey: ['media'] });
        void client.invalidateQueries({ queryKey: ['workspace-media'] });
        void client.invalidateQueries({ queryKey: ['video-picker'] });
      }
      setSuccess(
        `${uploadedCount} dosya yüklendi. Videolar hazır olduğunda kurs taslağında seçip incelemeye gönderin.`,
      );
      form.reset();
    } catch (e) {
      setError(
        new Error(
          `${uploadedCount}/${files.length} dosya yüklendi. ${e instanceof Error ? e.message : 'Yükleme tamamlanamadı.'}`,
        ),
      );
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
                  ? 'Tek video seçerseniz ders taslağı güncellenir. Yayın için taslağı incelemeye gönderin.'
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
                multiple
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
      <VideoOperations />
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
