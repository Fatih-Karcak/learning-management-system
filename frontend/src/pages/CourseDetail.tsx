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
        <ArrowLeft size={16} /> Back to courses
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
              <h2>Who is this course for?</h2>
              <p className="pre-wrap">{c.targetAudience}</p>
            </section>
          )}
          <div className="detail-meta">
            <span className="rating">
              <Star size={16} fill="currentColor" /> {(c.averageRating || 0).toFixed(1)}{' '}
              <small>({c.totalReviews || 0} reviews)</small>
            </span>
            <span>
              <Users size={16} /> {c.totalEnrollments || 0} learners
            </span>
            <span>
              <Globe size={16} /> {c.language}
            </span>
          </div>
          <div className="instructor-line">
            <Avatar name={c.instructorName || 'Instructor'} />
            <div>
              <small>INSTRUCTOR</small>
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
              <h2>What you will learn</h2>
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
            <h2>About this course</h2>
            <div className="prose">
              <ReactMarkdown>{c.description || c.shortDescription}</ReactMarkdown>
            </div>
            {!!c.requirements?.length && (
              <>
                <h3>Before you start</h3>
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
              <h2>Course content</h2>
              <span>
                {c.sections?.length || 0} sections ·{' '}
                {c.sections?.reduce((n, s) => n + (s.lessons?.length || 0), 0) || 0} lessons
              </span>
            </div>
            {c.sections?.map((section, i) => (
              <details className="curriculum" key={section.id} open={i === 0}>
                <summary>
                  <span>{String(i + 1).padStart(2, '0')}</span>
                  {section.title}
                  <small>{section.lessons?.length || 0} lessons</small>
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
                      <small>{lesson.previewAllowed ? 'Preview' : lessonTypes[lesson.type]}</small>
                      {!!lesson.durationSeconds && (
                        <small>{Math.ceil(lesson.durationSeconds / 60)} min</small>
                      )}
                    </button>
                  ))}
                </div>
              </details>
            ))}
            {!c.sections?.length && <p className="muted">No lessons have been added yet.</p>}
          </section>
          <section className="detail-section">
            <h2>What learners are saying</h2>
            {reviews.isPending ? (
              <Loading />
            ) : reviews.error ? (
              <ErrorBox error={reviews.error} />
            ) : (
              <>
                {reviews.data?.content.map((r) => (
                  <article className="review" key={r.id}>
                    <div className="review-heading">
                      <Avatar name={r.studentName || 'Learner'} src={r.studentAvatarUrl} />
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
                            const reason = window.prompt('Reason for removing this review');
                            if (reason) moderate.mutate({ id: r.id, reason });
                          }}
                        >
                          Remove through moderation
                        </button>
                      )}
                      {session?.user.id === r.studentId && (
                        <button
                          className="icon-button danger"
                          aria-label="Delete review"
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
                {!reviews.data?.content.length && <p className="muted">No reviews yet.</p>}
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
                <h3>Share your experience</h3>
                <Field label="Your rating">
                  <select name="rating">
                    {[5, 4, 3, 2, 1].map((n) => (
                      <option key={n} value={n}>
                        {n} stars
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Your review">
                  <textarea name="comment" maxLength={2000} rows={3} />
                </Field>
                {review.error && <ErrorBox error={review.error} />}{' '}
                {review.isSuccess ? (
                  <p className="success-text">Your review has been published.</p>
                ) : (
                  <Submit busy={review.isPending}>Leave a review</Submit>
                )}
              </form>
            )}
          </section>
        </div>
        <aside className="purchase-card panel">
          <Cover course={c} />
          <div className="purchase-body">
            <div className="purchase-price">
              <strong>{price === 0 ? 'Free' : money(price)}</strong>
              {c.discountPrice != null && c.discountPrice < c.price && <del>{money(c.price)}</del>}
            </div>
            {owner ? (
              <Link className="button primary" to={`/instructor/${c.id}`}>
                Edit course <ArrowRight size={17} />
              </Link>
            ) : enrollment.error ? (
              <ErrorBox error={enrollment.error} retry={() => void enrollment.refetch()} />
            ) : enrollment.data ? (
              <Link className="button primary" to="/my-learning">
                Go to my learning <ArrowRight size={17} />
              </Link>
            ) : c.status !== 'PUBLISHED' ? (
              <p className="notice">This course is closed to new enrollments.</p>
            ) : price === 0 && session ? (
              <button
                className="button primary"
                disabled={join.isPending}
                onClick={() => join.mutate()}
              >
                {join.isPending ? 'Enrolling…' : 'Join free course'} <ArrowRight size={17} />
              </button>
            ) : (
              <Link
                className="button primary"
                to={price === 0 ? '/login' : `/checkout/${c.id}`}
                state={price === 0 ? { from: `/courses/${c.id}` } : undefined}
              >
                {price === 0 ? 'Enroll in course' : 'Enroll in course'} <ArrowRight size={17} />
              </Link>
            )}
            {join.error && <ErrorBox error={join.error} />}
            <p className="purchase-note">Learn at your own pace and return whenever you like.</p>
            <div className="purchase-features">
              <span>
                <GraduationCap size={18} /> Guidance from experienced instructors
              </span>
              <span>
                <Clock size={18} />{' '}
                {c.durationMinutes
                  ? `${c.durationMinutes} minutes of content`
                  : 'Self-paced learning'}
              </span>
              <span>
                <Check size={18} /> Progress tracking and certification
              </span>
            </div>
            <Badge value={c.status} />
            {c.previewVideoUrl && (
              <button className="text-button" onClick={() => setShowTrailer(true)}>
                <Play size={15} /> Watch course trailer
              </button>
            )}
          </div>
        </aside>
      </div>
      <RecommendedCourses courseId={c.id} />
      {showTrailer && c.previewVideoUrl && (
        <Modal title="Course trailer" onClose={() => setShowTrailer(false)}>
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
              title="Content is not available yet"
              text="You may need to enroll or wait for this lesson to be prepared."
            />
          )}
        </Modal>
      )}
    </>
  );
}
