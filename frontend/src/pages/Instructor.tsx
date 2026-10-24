import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Copy, FileVideo, Image, Plus, Trash2, Upload } from 'lucide-react';
import { api, body, levels, money, safeUrl } from '../api';
import type { Category, Course, Media, Page } from '../types';
import VideoPlayer from '../VideoPlayer';
import CourseWorkspace from './Workspace';
import { VideoOperations } from './Platform';
import RichTextEditor from '../RichTextEditor';
import { CourseInvitations, TemplateLauncher } from './CourseAuthoring';
import { uploadVideo } from '../uploads';
import { useAuth } from '../auth';
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
        eyebrow="YOUR KNOWLEDGE MATTERS"
        title="Instructor studio"
        description="Share your knowledge and inspire someone's journey."
      >
        <TemplateLauncher />
        <Link className="button primary" to="/instructor/new">
          <Plus size={18} /> Create a new course
        </Link>
      </PageTitle>
      <CourseInvitations />
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
                    <span>{c.totalEnrollments || 0} learners</span>
                    <strong>{money(c.discountPrice ?? c.price)}</strong>
                  </div>
                  <Link className="button secondary" to={`/instructor/${c.id}`}>
                    Edit course <ArrowRight size={16} />
                  </Link>
                </div>
              </article>
            ))}
          </div>
          <Pagination page={page} pages={courses.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Your first course starts here."
          text="Create a draft, add lessons and publish when you are ready."
        >
          <Link className="button primary" to="/instructor/new">
            <Plus size={17} /> Create course
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
      setError(new Error('The discounted price cannot exceed the regular price.'));
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
      <h2>Course details</h2>
      <Field label="Course title">
        <input
          name="title"
          minLength={10}
          maxLength={200}
          required
          defaultValue={course?.title}
          placeholder="What will your learners achieve?"
        />
      </Field>
      <Field label="Short description" hint="Shown in the catalog. Maximum 500 characters.">
        <textarea
          name="shortDescription"
          rows={2}
          required
          maxLength={500}
          defaultValue={course?.shortDescription}
        />
      </Field>
      <Field label="Who is this course for?">
        <textarea
          name="targetAudience"
          rows={3}
          maxLength={2000}
          defaultValue={course?.targetAudience}
        />
      </Field>
      <Field label="Course description" hint="Use Markdown for headings and lists.">
        <RichTextEditor name="description" rows={6} required defaultValue={course?.description} />
      </Field>
      <div className="form-grid">
        <Field label="Price (TRY)">
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
          label="Discounted price (TRY)"
          hint={
            course?.discountPrice != null
              ? 'Enter the regular price to remove the discount.'
              : 'Optional.'
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
          <Field label="Category">
            <select name="categoryId" required defaultValue="">
              <option value="" disabled>
                Select category
              </option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
        )}
        <Field label="Level">
          <select name="level" defaultValue={course?.level || 'ALL_LEVELS'}>
            {Object.entries(levels).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        {!course && (
          <Field label="Course language">
            <input name="language" defaultValue="English" required />
          </Field>
        )}
      </div>
      <Field label="Course cover URL">
        <input
          name="thumbnailUrl"
          type="url"
          defaultValue={course?.thumbnailUrl}
          placeholder="https://…"
        />
      </Field>
      {!course && (
        <Field label="Course trailer URL">
          <input name="previewVideoUrl" type="url" placeholder="https://…" />
        </Field>
      )}
      <div className="form-grid">
        <Field label="Requirements" hint="Enter one item per line.">
          <textarea name="requirements" rows={4} defaultValue={course?.requirements?.join('\n')} />
        </Field>
        <Field label="Learning outcomes" hint="Enter one learning outcome per line.">
          <textarea name="outcomes" rows={4} defaultValue={course?.outcomes?.join('\n')} />
        </Field>
      </div>
      {categories.error && (
        <ErrorBox error={categories.error} retry={() => void categories.refetch()} />
      )}{' '}
      {!!error && <ErrorBox error={error} />}
      <Submit busy={busy}>{course ? 'Save changes' : 'Create draft'}</Submit>
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
        <ArrowLeft size={16} /> My courses
      </Link>
      <PageTitle
        eyebrow="INSTRUCTOR STUDIO"
        title="Turn your knowledge into a course."
        description="Start with course details, then prepare lessons in your draft."
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
  const { session } = useAuth();
  const uploadController = useRef<AbortController | null>(null);
  const [uploadProgress, setUploadProgress] = useState({ sent: 0, total: 0 });
  useEffect(() => () => uploadController.current?.abort(), []);
  const client = useQueryClient();
  const [params] = useSearchParams();
  const [page, setPage] = useState(0);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState('');
  const [kind, setKind] = useState('image');
  const [selectedCourse, setSelectedCourse] = useState(params.get('course') || '');
  const [inspect, setInspect] = useState<string>();
  const pendingUploads = useQuery({
    queryKey: ['pending-uploads'],
    queryFn: () =>
      api<{ id: string; file_name: string; received_bytes: number; total_bytes: number }[]>(
        '/media/uploads',
      ),
  });
  const cancelUpload = useMutation({
    mutationFn: (id: string) => api(`/media/uploads/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['pending-uploads'] });
    },
  });

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
      setSuccess('File deleted.');
    },
  });
  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const values = new FormData(form);
    const files = values.getAll('file').filter((f): f is File => f instanceof File && f.size > 0);
    if (!files.length) return;
    if (files.length > 20) {
      setError(new Error('Upload up to 20 files at a time.'));
      return;
    }
    if (files.length > 1 && values.get('lessonId')) {
      setError(
        new Error(
          'Clear the lesson selection when uploading multiple files. Attach videos from the course editor afterward.',
        ),
      );
      return;
    }
    const max = kind === 'image' ? 10 * 1024 * 1024 : 2 * 1024 * 1024 * 1024;
    if (files.some((f) => f.size > max)) {
      setError(new Error('One of the files exceeds the size limit.'));
      return;
    }
    setBusy(true);
    uploadController.current = new AbortController();
    setError(null);
    setSuccess('');
    let uploadedCount = 0;
    try {
      for (const file of files) {
        setSuccess(`${uploadedCount + 1}/${files.length}: ${file.name} uploading…`);
        const data = new FormData();
        data.set('file', file);
        if (values.get('courseId')) data.set('courseId', String(values.get('courseId')));
        const uploaded =
          kind === 'video'
            ? await uploadVideo(
                file,
                String(values.get('courseId') || '') || undefined,
                session!.user.id,
                (sent, total) => setUploadProgress({ sent, total }),
                uploadController.current.signal,
              )
            : await api<Media>('/media/image', {
                method: 'POST',
                body: data,
                signal: uploadController.current.signal,
              });
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
            throw new Error(
              'The lesson was not found in the draft. Select the video in the course editor.',
            );
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
        `${uploadedCount} ${uploadedCount === 1 ? 'file' : 'files'} uploaded. Select ready videos in the course draft and submit it for review.`,
      );
      form.reset();
    } catch (e) {
      if (uploadController.current?.signal.aborted && kind === 'video') {
        setSuccess(
          'Upload paused. Select the same file and course to continue from the saved position.',
        );
      } else
        setError(
          new Error(
            `${uploadedCount}/${files.length} files uploaded. ${e instanceof Error ? e.message : 'The upload could not be completed.'}`,
          ),
        );
    } finally {
      setBusy(false);
      void pendingUploads.refetch();
    }
  }
  async function copy(url?: string) {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setSuccess('Media link copied.');
    } catch {
      setError(new Error('The link could not be copied. You can select it in the file details.'));
    }
  }
  return (
    <>
      <PageTitle
        eyebrow="BRING YOUR CONTENT TO LIFE"
        title="Media library"
        description="Upload images and videos to use in your courses."
      />
      <section className="panel">
        <h2>
          <Upload size={20} /> Upload a new file
        </h2>
        <form onSubmit={upload}>
          <div className="form-grid">
            <Field label="File type">
              <select value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="image">Image · Up to 10 MB</option>
                <option value="video">Video · Up to 2 GB</option>
              </select>
            </Field>
            <Field label="Related course">
              <select
                name="courseId"
                value={selectedCourse}
                onChange={(e) => setSelectedCourse(e.target.value)}
              >
                <option value="">Not linked to a course</option>
                {courses.data?.content.map((c) => (
                  <option value={c.id} key={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label="Related lesson (optional)"
              hint={
                kind === 'video'
                  ? 'Selecting one video updates the lesson draft. Submit the draft for review to publish it.'
                  : undefined
              }
            >
              <select name="lessonId" key={`${selectedCourse}-${kind}`}>
                <option value="">No lesson selected</option>
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
            <Field label="Choose files">
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
          {busy && kind === 'video' && (
            <div role="status">
              <progress max={uploadProgress.total || 1} value={uploadProgress.sent} />
              <span>
                {Math.floor((100 * uploadProgress.sent) / (uploadProgress.total || 1))}% uploaded
              </span>
              <button
                type="button"
                className="button secondary"
                onClick={() => uploadController.current?.abort()}
              >
                Pause upload
              </button>
            </div>
          )}
          <p className="muted">
            Interrupted video uploads can resume for seven days. Select the same file and course to
            continue.
          </p>
          <Submit busy={busy}>{busy ? 'Uploading file…' : 'Upload file'}</Submit>
        </form>
        {courses.error && <ErrorBox error={courses.error} />}{' '}
        {course.error && <ErrorBox error={course.error} />}
      </section>
      {pendingUploads.error && <ErrorBox error={pendingUploads.error} />}
      {cancelUpload.error && <ErrorBox error={cancelUpload.error} />}
      {!!pendingUploads.data?.length && (
        <section className="panel">
          <h2>Unfinished video uploads</h2>
          <p>
            Select the same file and course above to resume, or cancel an upload to free its slot.
          </p>
          {pendingUploads.data.map((upload) => (
            <div className="section-heading" key={upload.id}>
              <span>
                {upload.file_name} ·{' '}
                {Math.floor((100 * upload.received_bytes) / upload.total_bytes)}% uploaded
              </span>
              <button
                className="button secondary"
                disabled={busy || cancelUpload.isPending}
                onClick={() => cancelUpload.mutate(upload.id)}
              >
                Cancel upload
              </button>
            </div>
          ))}
        </section>
      )}
      <VideoOperations />
      {success && <Success>{success}</Success>}
      {remove.error && <ErrorBox error={remove.error} />}
      <div className="section-heading">
        <h2>My files</h2>
        <button className="text-button" onClick={() => void media.refetch()}>
          Refresh
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
                  {m.durationSeconds ? ` · ${Math.ceil(m.durationSeconds / 60)} min` : ''}
                </p>
                {m.failureReason && <p className="error-text">{m.failureReason}</p>}
                <div className="form-actions">
                  <button className="button secondary small" onClick={() => setInspect(m.id)}>
                    Details
                  </button>
                  <button
                    className="icon-button"
                    aria-label="Copy media link"
                    disabled={m.status !== 'READY'}
                    onClick={() => void copy(m.streamUrl)}
                  >
                    <Copy size={17} />
                  </button>
                  <button
                    className="icon-button danger"
                    aria-label={`${m.originalFileName} delete file`}
                    disabled={remove.isPending}
                    onClick={() => {
                      if (
                        window.confirm(
                          'Permanently delete this file? Linked lessons may be affected.',
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
          title="Ready for your content."
          text="Start by uploading your first image or video."
        />
      )}
      {inspect && (
        <Modal title="File details" onClose={() => setInspect(undefined)}>
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
                  <p role="status">
                    Video processing is in progress. Playback will be available when it is ready.
                  </p>
                )}
                {details.data.failureReason && (
                  <ErrorBox error={new Error(details.data.failureReason)} />
                )}
                <Field label="Media link">
                  <input
                    readOnly
                    value={details.data.streamUrl || ''}
                    onFocus={(e) => e.currentTarget.select()}
                  />
                </Field>
                <p className="muted">
                  Use the video link when creating a lesson. Private image links expire; use the
                  course cover uploader for a permanent cover.
                </p>
              </>
            )
          )}
        </Modal>
      )}
    </>
  );
}
