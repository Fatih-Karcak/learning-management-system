import { WishButton, RecommendedCourses } from './Platform';
import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Clock,
  Globe,
  GraduationCap,
  LockKeyhole,
  Play,
  Star,
  Trash2,
  Users,
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { api, body, date, lessonTypes, levels, money } from '../api';
import { useAuth } from '../auth';
import type { Course, Lesson, Page, Review, Enrollment } from '../types';
import {
  Avatar,
  Badge,
  Cover,
  Empty,
  ErrorBox,
  Field,
  Loading,
  Modal,
  Pagination,
  Submit,
} from '../ui';
import VideoPlayer from '../VideoPlayer';

export default function CourseDetail() {
  const { courseId } = useParams();
  const { session } = useAuth();
  const client = useQueryClient();
  const navigate = useNavigate();
  const join = useMutation({
    mutationFn: () => api<Enrollment>(`/enrollments/free/${courseId}`, { method: 'POST' }),
    onSuccess: (e) => {
      void client.invalidateQueries({ queryKey: ['enrollments'] });
      void client.invalidateQueries({ queryKey: ['enrolled', courseId] });
      navigate(`/learn/${e.id}`);
    },
  });
  const [reviewPage, setReviewPage] = useState(0);
  const [preview, setPreview] = useState<Lesson>();
  const [showTrailer, setShowTrailer] = useState(false);
  const course = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api<Course>(`/courses/${courseId}`),
  });
  const reviews = useQuery({
    queryKey: ['reviews', courseId, reviewPage],
    queryFn: () => api<Page<Review>>(`/reviews/course/${courseId}?page=${reviewPage}&size=5`),
  });
  const enrollment = useQuery({
    queryKey: ['enrolled', courseId, session?.user.id],
    queryFn: () => api<boolean>(`/enrollments/course/${courseId}/check`),
    enabled: !!session,
  });
  const review = useMutation({
    mutationFn: (data: unknown) =>
      api(`/reviews/course/${courseId}`, { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['reviews', courseId] });
      void client.invalidateQueries({ queryKey: ['course', courseId] });
    },
  });
  const moderate = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api(`/courses/admin/reviews/${id}/remove`, { method: 'POST', body: body({ reason }) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['reviews', courseId] });
      void client.invalidateQueries({ queryKey: ['course', courseId] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/reviews/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['reviews', courseId] });
      void client.invalidateQueries({ queryKey: ['course', courseId] });
    },
  });
  if (course.isPending) return <Loading />;
  if (course.error) return <ErrorBox error={course.error} retry={() => void course.refetch()} />;
  const c = course.data!;
  const owner = session && (c.instructorId === session.user.id || session.user.role === 'ADMIN');
  const price = c.discountPrice ?? c.price;
  function addReview(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    review.mutate({ rating: Number(f.get('rating')), comment: f.get('comment') });
  }
  return (
    <>
      <Link className="back-link" to="/catalog">
        <ArrowLeft size={16} /> Kurslara dön
      </Link>
      <div className="course-detail-layout">
        <div>
          <div className="detail-category">
            {c.categoryName} <span>•</span> {levels[c.level]}
          </div>
          <h1 className="detail-title">{c.title}</h1>
          <WishButton courseId={c.id} />
          <p className="detail-description">{c.shortDescription}</p>
          {c.targetAudience && (
            <section className="panel">
              <h2>Bu kurs kimler için?</h2>
              <p className="pre-wrap">{c.targetAudience}</p>
            </section>
          )}
          <div className="detail-meta">
            <span className="rating">
              <Star size={16} fill="currentColor" /> {(c.averageRating || 0).toFixed(1)}{' '}
              <small>({c.totalReviews || 0} değerlendirme)</small>
            </span>
            <span>
              <Users size={16} /> {c.totalEnrollments || 0} öğrenci
            </span>
            <span>
              <Globe size={16} /> {c.language}
            </span>
          </div>
          <div className="instructor-line">
            <Avatar name={c.instructorName || 'Eğitmen'} />
            <div>
              <small>EĞİTMEN</small>
              <strong>
                {c.instructorId ? (
                  <Link to={`/instructors/${c.instructorId}`}>{c.instructorName}</Link>
                ) : (
                  c.instructorName
                )}
              </strong>
            </div>
          </div>
          {!!c.outcomes?.length && (
            <section className="panel outcomes">
              <h2>Neler öğreneceksin?</h2>
              <div className="outcomes-grid">
                {c.outcomes.map((o, i) => (
                  <p key={i}>
                    <Check size={17} />
                    {o}
                  </p>
                ))}
              </div>
            </section>
          )}
          <section className="detail-section">
            <h2>Bu kurs hakkında</h2>
            <div className="prose">
              <ReactMarkdown>{c.description || c.shortDescription}</ReactMarkdown>
            </div>
            {!!c.requirements?.length && (
              <>
                <h3>Başlamadan önce</h3>
                <ul>
                  {c.requirements.map((r, i) => (
                    <li key={i}>{r}</li>
                  ))}
                </ul>
              </>
            )}
          </section>
          <section className="detail-section">
            <div className="section-heading">
              <h2>Kurs içeriği</h2>
              <span>
                {c.sections?.length || 0} bölüm ·{' '}
                {c.sections?.reduce((n, s) => n + (s.lessons?.length || 0), 0) || 0} ders
              </span>
            </div>
            {c.sections?.map((section, i) => (
              <details className="curriculum" key={section.id} open={i === 0}>
                <summary>
                  <span>{String(i + 1).padStart(2, '0')}</span>
                  {section.title}
                  <small>{section.lessons?.length || 0} ders</small>
                </summary>
                <div>
                  {section.lessons?.map((lesson) => (
                    <button
                      className="lesson-row"
                      key={lesson.id}
                      disabled={!lesson.previewAllowed && !owner && !enrollment.data}
                      onClick={() => setPreview(lesson)}
                    >
                      {lesson.previewAllowed || owner || enrollment.data ? (
                        <Play size={15} />
                      ) : (
                        <LockKeyhole size={15} />
                      )}
                      <span>{lesson.title}</span>
                      <small>
                        {lesson.previewAllowed ? 'Ön izleme' : lessonTypes[lesson.type]}
                      </small>
                      {!!lesson.durationSeconds && (
                        <small>{Math.ceil(lesson.durationSeconds / 60)} dk</small>
                      )}
                    </button>
                  ))}
                </div>
              </details>
            ))}
            {!c.sections?.length && <p className="muted">Dersler henüz eklenmemiş.</p>}
          </section>
          <section className="detail-section">
            <h2>Öğrenciler ne düşünüyor?</h2>
            {reviews.isPending ? (
              <Loading />
            ) : reviews.error ? (
              <ErrorBox error={reviews.error} />
            ) : (
              <>
                {reviews.data?.content.map((r) => (
                  <article className="review" key={r.id}>
                    <div className="review-heading">
                      <Avatar name={r.studentName || 'Öğrenci'} src={r.studentAvatarUrl} />
                      <div>
                        <strong>{r.studentName}</strong>
                        <small>{date(r.createdAt)}</small>
                      </div>
                      <span className="rating">{'★'.repeat(r.rating)}</span>
                      {session?.user.role === 'ADMIN' && session.user.adminScope !== 'SUPPORT' && (
                        <button
                          className="text-button danger"
                          disabled={moderate.isPending}
                          onClick={() => {
                            const reason = window.prompt('Değerlendirmeyi kaldırma gerekçesi');
                            if (reason) moderate.mutate({ id: r.id, reason });
                          }}
                        >
                          Moderasyonla kaldır
                        </button>
                      )}
                      {session?.user.id === r.studentId && (
                        <button
                          className="icon-button danger"
                          aria-label="Yorumu sil"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm('Yorumunuz silinsin mi?')) remove.mutate(r.id);
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      )}
                    </div>
                    <p>{r.comment}</p>
                  </article>
                ))}
                {!reviews.data?.content.length && (
                  <p className="muted">Henüz değerlendirme yapılmamış.</p>
                )}
                <Pagination
                  page={reviewPage}
                  pages={reviews.data?.totalPages || 0}
                  onChange={setReviewPage}
                />
              </>
            )}
            {moderate.error && <ErrorBox error={moderate.error} />}
            {remove.error && <ErrorBox error={remove.error} />}{' '}
            {session?.user.role === 'STUDENT' && enrollment.data && (
              <form className="panel review-form" onSubmit={addReview}>
                <h3>Deneyimini paylaş</h3>
                <Field label="Puanın">
                  <select name="rating">
                    {[5, 4, 3, 2, 1].map((n) => (
                      <option key={n} value={n}>
                        {n} yıldız
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Yorumun">
                  <textarea name="comment" maxLength={2000} rows={3} />
                </Field>
                {review.error && <ErrorBox error={review.error} />}{' '}
                {review.isSuccess ? (
                  <p className="success-text">Yorumun yayınlandı.</p>
                ) : (
                  <Submit busy={review.isPending}>Değerlendir</Submit>
                )}
              </form>
            )}
          </section>
        </div>
        <aside className="purchase-card panel">
          <Cover course={c} />
          <div className="purchase-body">
            <div className="purchase-price">
              <strong>{price === 0 ? 'Ücretsiz' : money(price)}</strong>
              {c.discountPrice != null && c.discountPrice < c.price && <del>{money(c.price)}</del>}
            </div>
            {owner ? (
              <Link className="button primary" to={`/instructor/${c.id}`}>
                Kursu düzenle <ArrowRight size={17} />
              </Link>
            ) : enrollment.error ? (
              <ErrorBox error={enrollment.error} retry={() => void enrollment.refetch()} />
            ) : enrollment.data ? (
              <Link className="button primary" to="/my-learning">
                Öğrenim alanına git <ArrowRight size={17} />
              </Link>
            ) : c.status !== 'PUBLISHED' ? (
              <p className="notice">Bu kurs yeni kayıtlara kapalı.</p>
            ) : price === 0 && session ? (
              <button
                className="button primary"
                disabled={join.isPending}
                onClick={() => join.mutate()}
              >
                {join.isPending ? 'Kayıt yapılıyor…' : 'Ücretsiz kursa katıl'}{' '}
                <ArrowRight size={17} />
              </button>
            ) : (
              <Link
                className="button primary"
                to={price === 0 ? '/login' : `/checkout/${c.id}`}
                state={price === 0 ? { from: `/courses/${c.id}` } : undefined}
              >
                {price === 0 ? 'Kursa katıl' : 'Kursa kaydol'} <ArrowRight size={17} />
              </Link>
            )}
            {join.error && <ErrorBox error={join.error} />}
            <p className="purchase-note">Kendi hızında öğren, istediğin zaman devam et.</p>
            <div className="purchase-features">
              <span>
                <GraduationCap size={18} /> Uzman eğitmen rehberliği
              </span>
              <span>
                <Clock size={18} />{' '}
                {c.durationMinutes ? `${c.durationMinutes} dakika içerik` : 'Kendi hızında öğrenme'}
              </span>
              <span>
                <Check size={18} /> İlerleme takibi ve sertifika
              </span>
            </div>
            <Badge value={c.status} />
            {c.previewVideoUrl && (
              <button className="text-button" onClick={() => setShowTrailer(true)}>
                <Play size={15} /> Tanıtım videosunu izle
              </button>
            )}
          </div>
        </aside>
      </div>
      <RecommendedCourses courseId={c.id} />
      {showTrailer && c.previewVideoUrl && (
        <Modal title="Kurs tanıtımı" onClose={() => setShowTrailer(false)}>
          <VideoPlayer src={c.previewVideoUrl} />
        </Modal>
      )}
      {preview && (
        <Modal title={preview.title} onClose={() => setPreview(undefined)}>
          {preview.videoUrl ? (
            <VideoPlayer src={preview.videoUrl} />
          ) : preview.content ? (
            <div className="prose">
              <ReactMarkdown>{preview.content}</ReactMarkdown>
            </div>
          ) : (
            <Empty
              title="İçerik henüz kullanılamıyor"
              text="Dersi görüntülemek için kursa kaydolman veya içeriğin hazırlanmasını beklemen gerekebilir."
            />
          )}
        </Modal>
      )}
    </>
  );
}
