import { useState, useRef, useEffect, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Award,
  CheckCircle2,
  Download,
  Play,
  ShieldCheck,
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { api, body, date, lessonTypes, safeUrl } from '../api';
import { useAuth } from '../auth';
import type { Certificate, Course, Enrollment, Page, Progress, Material } from '../types';
import {
  Badge,
  Cover,
  Empty,
  ErrorBox,
  Field,
  Loading,
  PageTitle,
  Pagination,
  Submit,
} from '../ui';
import VideoPlayer from '../VideoPlayer';
import { LessonBookmarks, LessonMaterials, Announcements } from './Platform';
import { QuizLesson, AssignmentLesson, LessonNotes, CourseDiscussion } from './LearningFeatures';

export function MyLearning() {
  const [page, setPage] = useState(0);
  const items = useQuery({
    queryKey: ['enrollments', page],
    queryFn: () =>
      api<Page<Enrollment>>(`/enrollments/my?page=${page}&size=9&sort=enrolledAt,desc`),
  });
  return (
    <>
      <PageTitle
        eyebrow="TIME FOR YOURSELF"
        title="My learning"
        description="Small steps build lasting knowledge."
      />
      {items.isPending ? (
        <Loading />
      ) : items.error ? (
        <ErrorBox error={items.error} retry={() => void items.refetch()} />
      ) : items.data?.content.length ? (
        <>
          <div className="course-grid">
            {items.data.content.map((e) => (
              <article className="course-card" key={e.id}>
                <Cover
                  course={{
                    id: e.courseId,
                    title: e.courseTitle,
                    thumbnailUrl: e.courseThumbnailUrl,
                    categoryName: 'Your learning journey',
                  }}
                />
                <div className="course-card-body">
                  <Badge value={e.status} />
                  <h3>{e.courseTitle}</h3>
                  <p className="instructor">{e.instructorName}</p>
                  <div className="learning-progress">
                    <progress max="100" value={e.progressPercentage || 0} />
                    <small>
                      {e.completedLessons} / {e.totalLessons} lessons{' '}
                      <b>{Math.round(e.progressPercentage || 0)}%</b>
                    </small>
                  </div>
                  {['ACTIVE', 'COMPLETED'].includes(e.status) ? (
                    <Link className="button secondary" to={`/learn/${e.id}`}>
                      {e.status === 'COMPLETED' ? 'Review course' : 'Continue'}{' '}
                      <ArrowRight size={16} />
                    </Link>
                  ) : (
                    <p className="muted">This enrollment is currently unavailable.</p>
                  )}
                </div>
              </article>
            ))}
          </div>
          <Pagination page={page} pages={items.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Ready to take your first step?"
          text="Your enrolled courses will appear here. Start with something that inspires you."
        >
          <Link className="button primary" to="/catalog">
            Explore courses <ArrowRight size={17} />
          </Link>
        </Empty>
      )}
    </>
  );
}
export function Learning() {
  const { enrollmentId } = useParams();
  const [params, setParams] = useSearchParams();
  const client = useQueryClient();
  const [position, setPosition] = useState(0);
  const [seekTo, setSeekTo] = useState<{ seconds: number; nonce: number }>();
  const [playbackError, setPlaybackError] = useState<unknown>();
  const playbackQueue = useRef<Promise<unknown>>(Promise.resolve());
  const enrollment = useQuery({
    queryKey: ['enrollment', enrollmentId],
    queryFn: () => api<Enrollment>(`/enrollments/${enrollmentId}`),
    refetchInterval: 15000,
  });
  const course = useQuery({
    queryKey: ['course', enrollment.data?.courseId],
    queryFn: () => api<Course>(`/courses/${enrollment.data!.courseId}`),
    enabled: !!enrollment.data,
  });
  const progress = useQuery({
    queryKey: ['progress', enrollmentId],
    queryFn: () => api<Progress[]>(`/progress/${enrollmentId}`),
    refetchInterval: 15000,
  });
  const lessons =
    course.data?.sections?.flatMap((s) =>
      (s.lessons || []).map((l) => ({ ...l, sectionId: s.id, sectionTitle: s.title })),
    ) || [];
  const recent = progress.data
    ?.filter((p) => p.status === 'IN_PROGRESS')
    .sort((a, b) => (b.lastWatchedAt || '').localeCompare(a.lastWatchedAt || ''))[0];
  const nextLesson =
    lessons.find((l) => l.id === recent?.lessonId) ||
    lessons.find(
      (l) => !progress.data?.some((p) => p.lessonId === l.id && p.status === 'COMPLETED'),
    ) ||
    lessons[0];
  const lesson = lessons.find((l) => l.id === params.get('lesson')) || nextLesson;
  useEffect(() => {
    if (!params.get('lesson') && lesson && course.data && progress.data)
      setParams({ lesson: lesson.id }, { replace: true });
  }, [params, lesson, course.data, progress.data, setParams]);
  const current = progress.data?.find((p) => p.lessonId === lesson?.id);
  const materials = useQuery({
    queryKey: ['materials', enrollment.data?.courseId, lesson?.id],
    queryFn: () =>
      api<Material[]>(`/courses/${enrollment.data!.courseId}/lessons/${lesson!.id}/materials`),
    enabled: !!lesson && !lesson.locked && !!enrollment.data,
  });

  useEffect(() => {
    setPosition(0);
    setSeekTo(undefined);
    setPlaybackError(null);
  }, [lesson?.id]);
  const refreshProgress = () => {
    void client.invalidateQueries({ queryKey: ['progress', enrollmentId] });
    void client.invalidateQueries({ queryKey: ['enrollment', enrollmentId] });
    void client.invalidateQueries({ queryKey: ['enrollments'] });
    void client.invalidateQueries({ queryKey: ['course'] });
    void client.invalidateQueries({ queryKey: ['learning-path'] });
  };
  const update = useMutation({
    mutationFn: ({
      lessonId,
      sectionId,
      watchedSeconds,
      completed,
    }: {
      lessonId: string;
      sectionId: string;
      watchedSeconds?: number;
      completed?: boolean;
    }) =>
      api<Progress>(`/progress/${enrollmentId}/lesson/${lessonId}`, {
        method: 'PUT',
        body: body({ sectionId, watchedSeconds, completed }),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['progress', enrollmentId] });
      void client.invalidateQueries({ queryKey: ['enrollment', enrollmentId] });
      void client.invalidateQueries({ queryKey: ['enrollments'] });
      void client.invalidateQueries({ queryKey: ['course'] });
      void client.invalidateQueries({ queryKey: ['learning-path'] });
    },
  });
  if (enrollment.error || course.error || progress.error)
    return (
      <ErrorBox
        error={enrollment.error || course.error || progress.error}
        retry={() => {
          void enrollment.refetch();
          void course.refetch();
          void progress.refetch();
        }}
      />
    );
  if (enrollment.isPending || course.isPending || progress.isPending) return <Loading />;
  const e = enrollment.data!;
  if (!['ACTIVE', 'COMPLETED'].includes(e.status))
    return (
      <Empty
        title="This course enrollment is not active"
        text="Check the payment or refund status in your account."
      >
        <Link to="/payments" className="button primary">
          My payments
        </Link>
      </Empty>
    );
  return (
    <>
      <Link className="back-link" to="/my-learning">
        <ArrowLeft size={16} /> My learning
      </Link>
      <PageTitle eyebrow="EVERY LESSON IS A NEW STEP" title={e.courseTitle}>
        <div className="course-completion">
          <strong>{Math.round(e.progressPercentage || 0)}%</strong>
          <span>
            {e.completedLessons}/{e.totalLessons} lessons
          </span>
          <progress value={e.progressPercentage || 0} max="100" />
        </div>
      </PageTitle>
      <div className="learning-layout">
        <section className="lesson-content panel">
          {lesson ? (
            <>
              <div className="lesson-heading">
                <span className="eyebrow">
                  {lesson.sectionTitle} · {lessonTypes[lesson.type]}
                </span>
                <h2>{lesson.title}</h2>
              </div>
              {lesson.videoUrl && (
                <VideoPlayer
                  key={lesson.id}
                  src={lesson.videoUrl}
                  initialTime={
                    Number(params.get('time')) ||
                    current?.positionSeconds ||
                    current?.watchedSeconds ||
                    0
                  }
                  captions={materials.data?.filter((m) => m.type === 'SUBTITLE')}
                  seekTo={seekTo}
                  onPosition={setPosition}
                  onPlayback={(seconds, playing) => {
                    const path = `/progress/${enrollmentId}/lesson/${lesson.id}/playback`;
                    const data = { sectionId: lesson.sectionId, positionSeconds: seconds, playing };
                    playbackQueue.current = playbackQueue.current
                      .catch(() => {})
                      .then(async () => {
                        const saved = await api<Progress>(path, {
                          method: 'POST',
                          body: body(data),
                          keepalive: true,
                        });
                        setPlaybackError(null);
                        client.setQueryData<Progress[]>(['progress', enrollmentId], (old) => [
                          ...(old || []).filter((p) => p.lessonId !== saved.lessonId),
                          saved,
                        ]);
                        if (saved.status === 'COMPLETED') refreshProgress();
                      })
                      .catch((error) => setPlaybackError(error));
                  }}
                />
              )}
              {lesson.locked && (
                <p role="status" className="notice warning">
                  {lesson.lockReason}
                </p>
              )}
              {lesson.type === 'VIDEO' && !lesson.videoUrl && !lesson.locked && (
                <p role="status" className="notice">
                  No video is attached to this lesson yet. Your instructor needs to add one.
                </p>
              )}
              <div className="prose">
                <ReactMarkdown>
                  {lesson.content ||
                    (lesson.videoUrl ? '' : 'No content has been added to this lesson yet.')}
                </ReactMarkdown>
              </div>
              {!!playbackError && <ErrorBox error={playbackError} />}
              {lesson.type === 'QUIZ' && !lesson.locked && (
                <QuizLesson
                  key={lesson.id}
                  courseId={e.courseId}
                  lessonId={lesson.id}
                  enrollmentId={e.id}
                  onCompleted={refreshProgress}
                />
              )}
              {lesson.type === 'ASSIGNMENT' && !lesson.locked && (
                <AssignmentLesson
                  key={lesson.id}
                  courseId={e.courseId}
                  lessonId={lesson.id}
                  enrollmentId={e.id}
                  onCompleted={refreshProgress}
                />
              )}
              {update.error && <ErrorBox error={update.error} />}
              <div className="lesson-footer">
                {current?.status === 'COMPLETED' ? (
                  <span className="success-text">
                    <CheckCircle2 size={18} /> Lesson completed
                  </span>
                ) : lesson.type === 'ARTICLE' ? (
                  <button
                    className="button primary"
                    disabled={lesson.locked || update.isPending || e.status !== 'ACTIVE'}
                    onClick={() =>
                      update.mutate({
                        lessonId: lesson.id,
                        sectionId: lesson.sectionId,
                        completed: true,
                      })
                    }
                  >
                    I have read this lesson <CheckCircle2 size={17} />
                  </button>
                ) : (
                  <p className="muted">
                    {lesson.type === 'VIDEO'
                      ? 'Your playback position is saved. The lesson completes after you watch 90% of the video.'
                      : lesson.type === 'QUIZ'
                        ? 'The lesson completes when you reach the passing score.'
                        : 'The lesson completes when your instructor accepts your assignment.'}
                  </p>
                )}
                {lessons.indexOf(lesson) + 1 < lessons.length && (
                  <button
                    className="button secondary"
                    onClick={() => setParams({ lesson: lessons[lessons.indexOf(lesson) + 1].id })}
                  >
                    Next lesson <ArrowRight size={16} />
                  </button>
                )}
              </div>
              {!lesson.locked && <LessonMaterials courseId={e.courseId} lessonId={lesson.id} />}
              <LessonBookmarks
                enrollmentId={e.id}
                lessonId={lesson.id}
                sectionId={lesson.sectionId}
                position={lesson.type === 'VIDEO' ? position : 0}
                onSeek={(seconds) => setSeekTo({ seconds, nonce: Date.now() })}
              />
              <LessonNotes
                key={`notes-${lesson.id}`}
                enrollmentId={e.id}
                lessonId={lesson.id}
                sectionId={lesson.sectionId}
                position={lesson.videoUrl ? position : 0}
                onSeek={(seconds) => setSeekTo({ seconds, nonce: Date.now() })}
              />
              <Announcements courseId={e.courseId} />
              <CourseDiscussion
                key={`discussion-${lesson.id}`}
                courseId={e.courseId}
                lessonId={lesson.id}
                instructorId={course.data?.instructorId}
              />
            </>
          ) : (
            <Empty
              title="Lessons are being prepared"
              text="Lessons will appear here when they are added."
            />
          )}
        </section>
        <aside className="lesson-sidebar panel">
          <h3>Course content</h3>
          {course.data?.sections?.map((section) => (
            <div className="lesson-group" key={section.id}>
              <h4>{section.title}</h4>
              {section.lessons?.map((l) => (
                <button
                  key={l.id}
                  className={`lesson-nav ${l.id === lesson?.id ? 'selected' : ''}`}
                  onClick={() => setParams({ lesson: l.id })}
                >
                  {progress.data?.find((p) => p.lessonId === l.id)?.status === 'COMPLETED' ? (
                    <CheckCircle2 size={18} />
                  ) : (
                    <Play size={17} />
                  )}
                  <span>
                    {l.title}
                    <small>
                      {lessonTypes[l.type]}
                      {l.durationSeconds ? ` · ${Math.ceil(l.durationSeconds / 60)} min` : ''}
                    </small>
                  </span>
                </button>
              ))}
            </div>
          ))}
          {e.status === 'COMPLETED' && (
            <Link className="button primary" to={`/certificates?enrollment=${e.id}`}>
              <Award size={17} /> View my certificate
            </Link>
          )}
        </aside>
      </div>
    </>
  );
}
function CertificateCard({ enrollmentId }: { enrollmentId: string }) {
  const cert = useQuery({
    queryKey: ['certificate', enrollmentId],
    queryFn: () => api<Certificate>(`/enrollments/${enrollmentId}/certificate`),
  });
  return (
    <div className="panel certificate-panel">
      {cert.isPending ? (
        <Loading />
      ) : cert.error ? (
        <ErrorBox error={cert.error} retry={() => void cert.refetch()} />
      ) : (
        cert.data && <CertificateView certificate={cert.data} />
      )}
    </div>
  );
}
function CertificateView({ certificate: c }: { certificate: Certificate }) {
  return (
    <>
      <div className="certificate-art">
        <Award size={52} />
        <span className="eyebrow">CERTIFICATE OF COMPLETION</span>
        <h2>{c.courseTitle}</h2>
        <p>{c.studentName}</p>
        <small>
          {c.instructorName} · {date(c.issuedAt)}
        </small>
        <div className="certificate-code">{c.verificationCode}</div>
      </div>
      <div className="form-actions">
        {safeUrl(c.pdfUrl) && (
          <a
            href={safeUrl(c.pdfUrl)}
            className="button primary"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Download size={17} /> Download PDF
          </a>
        )}
        <Link
          className="button secondary"
          to={`/certificates/verify/${encodeURIComponent(c.verificationCode)}`}
        >
          <ShieldCheck size={17} /> Verify
        </Link>
      </div>
    </>
  );
}
export function Certificates() {
  const { session } = useAuth();
  const { code } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const enrollmentId = params.get('enrollment');
  const enrolled = useQuery({
    queryKey: ['cert-enrollments', page],
    queryFn: () => api<Page<Enrollment>>(`/enrollments/my?size=12&page=${page}`),
    enabled: !!session && !code && !enrollmentId,
  });
  const verified = useQuery({
    queryKey: ['verify-certificate', code],
    queryFn: () =>
      api<Certificate>(`/enrollments/certificates/verify/${encodeURIComponent(code!)}`),
    enabled: !!code,
    retry: false,
  });
  function verify(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    navigate(`/certificates/verify/${encodeURIComponent(String(f.get('code')).trim())}`);
  }
  return (
    <>
      <PageTitle
        eyebrow="CELEBRATE YOUR PROGRESS"
        title="Show what you have learned."
        description="Explore your achievements, download a certificate or verify one."
      />
      {code ? (
        <section className="panel narrow">
          {verified.isPending ? (
            <Loading />
          ) : verified.error ? (
            <ErrorBox error={verified.error} />
          ) : (
            verified.data && (
              <>
                <div className="notice success">
                  <ShieldCheck size={20} /> Certificate verified.
                </div>
                <CertificateView certificate={verified.data} />
              </>
            )
          )}
          <Link className="back-link" to="/certificates">
            <ArrowLeft size={16} /> Back to certificates
          </Link>
        </section>
      ) : (
        <>
          {enrollmentId && session ? (
            <CertificateCard enrollmentId={enrollmentId} />
          ) : session ? (
            enrolled.isPending ? (
              <Loading />
            ) : enrolled.error ? (
              <ErrorBox error={enrolled.error} />
            ) : (
              <>
                <div className="certificate-grid">
                  {enrolled.data?.content
                    .filter((e) => e.status === 'COMPLETED')
                    .map((e) => (
                      <CertificateCard key={e.id} enrollmentId={e.id} />
                    ))}
                </div>
                {!enrolled.data?.content.some((e) => e.status === 'COMPLETED') && (
                  <Empty
                    title="Your success story is taking shape."
                    text="Certificates for completed courses will appear here. Keep learning."
                  >
                    <Link to="/my-learning" className="button primary">
                      Go to my learning
                    </Link>
                  </Empty>
                )}
                <Pagination page={page} pages={enrolled.data?.totalPages || 0} onChange={setPage} />
              </>
            )
          ) : null}
          <section className="panel verify-panel">
            <ShieldCheck size={32} />
            <div>
              <h2>Verify a certificate</h2>
              <p>Check a certificate using its verification code.</p>
              <form onSubmit={verify}>
                <Field label="Verification code">
                  <input name="code" required placeholder="The code printed on the certificate" />
                </Field>
                <Submit busy={false}>Verify certificate</Submit>
              </form>
            </div>
          </section>
        </>
      )}
    </>
  );
}
