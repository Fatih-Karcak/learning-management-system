import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useBlocker } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { api, body, levels, lessonTypes } from '../api';
import type { Course, Category, Lesson, Quiz, Media, Page, Material, Workspace } from '../types';
import { Badge, ErrorBox, Field, Loading, Modal, PageTitle, Pagination, Success } from '../ui';
import VideoPlayer from '../VideoPlayer';
import { AssignmentGrading, CourseStudents, CourseDiscussion } from './LearningFeatures';
import { CourseAnalytics, Announcements } from './Platform';
import RichTextEditor from '../RichTextEditor';
import { duplicateLesson } from '../courseAuthoring';
import { useAuth } from '../auth';
import { CourseTeam } from './CourseAuthoring';
import { LearningRules } from './LearningPaths';
import AssignmentPolicyEditor from './AssignmentPolicy';
const blankQuiz = (): Quiz => ({ passingScore: 70, questions: [] });
const blankQuestion = () => ({
  id: crypto.randomUUID(),
  prompt: '',
  options: ['', ''],
  correctOption: 0,
  explanation: '',
});
export default function CourseWorkspace({
  courseId,
  review = false,
}: {
  courseId: string;
  review?: boolean;
}) {
  const client = useQueryClient();
  const { session } = useAuth();
  const [mediaPage, setMediaPage] = useState(0);
  const [mediaSearch, setMediaSearch] = useState('');
  const query = useQuery({
    queryKey: ['workspace', courseId],
    queryFn: () => api<Workspace>(`/courses/${courseId}/workspace`),
  });
  const live = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api<Course>(`/courses/${courseId}`),
    enabled: !review,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
  });
  const media = useQuery({
    queryKey: ['workspace-media', courseId, mediaPage, mediaSearch],
    queryFn: () =>
      api<Page<Media>>(
        `/media/my?courseId=${courseId}&type=VIDEO&status=READY&query=${encodeURIComponent(mediaSearch)}&page=${mediaPage}&size=12&sort=createdAt,desc`,
      ),
    enabled: !review,
    refetchInterval: (q) =>
      q.state.data?.content.some((m) => m.status === 'PROCESSING') ? 4000 : false,
  });
  const [workspace, setWorkspace] = useState<Workspace>();
  const [dirty, setDirty] = useState(false);
  const [preview, setPreview] = useState(false);
  const [selected, setSelected] = useState<string>();
  const [tool, setTool] = useState<string>();
  const [uploadError, setUploadError] = useState<unknown>();
  const [uploading, setUploading] = useState(false);
  const blocker = useBlocker(dirty && !review);
  const recoveryKey = `lms.draft.${session?.user.id}.${courseId}`;
  const initialized = useRef('');
  const [recovery, setRecovery] = useState<Workspace>();
  useEffect(() => {
    if (!query.data || review || initialized.current === recoveryKey) return;
    initialized.current = recoveryKey;
    try {
      const stored = JSON.parse(sessionStorage.getItem(recoveryKey) || 'null');
      if (
        stored?.workspace?.document?.course?.id === courseId &&
        Date.now() - stored.savedAt < 7 * 86400000
      )
        setRecovery(stored.workspace);
    } catch {
      sessionStorage.removeItem(recoveryKey);
    }
  }, [query.data, recoveryKey, courseId, review]);
  useEffect(() => {
    if (!dirty || !workspace || review) return;
    try {
      sessionStorage.setItem(recoveryKey, JSON.stringify({ savedAt: Date.now(), workspace }));
    } catch {
      /* Navigation protection and server saving still work if storage is full. */
    }
  }, [workspace, dirty, recoveryKey, review]);
  useEffect(() => {
    if (query.data && !dirty) setWorkspace(structuredClone(query.data));
  }, [query.data, dirty]);
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);
  const path = `/courses/${courseId}/workspace`;
  const archive = useMutation({
    mutationFn: () => api(`/courses/${courseId}/archive`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course', courseId] });
      void client.invalidateQueries({ queryKey: ['workspace', courseId] });
      void client.invalidateQueries({ queryKey: ['own-courses'] });
    },
  });
  const saveTemplate = useMutation({
    mutationFn: (name: string) =>
      api(`/courses/${courseId}/templates`, {
        method: 'POST',
        body: body({ name, revision: workspace!.revision }),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course-templates'] });
    },
  });
  const action = useMutation({
    mutationFn: async (kind: string) => {
      if (kind === 'save')
        return api<Workspace>(path, {
          method: 'PUT',
          body: body({ revision: workspace!.revision, document: workspace!.document }),
        });
      if (kind === 'submit')
        return api<Workspace>(`${path}/submit?revision=${workspace!.revision}`, { method: 'POST' });
      if (kind === 'withdraw') return api<Workspace>(`${path}/withdraw`, { method: 'POST' });
      const reason = kind === 'reject' ? window.prompt('Reason for requesting changes') : null;
      if (kind === 'reject' && !reason) throw new Error('A reason is required.');
      return api<Workspace>(
        `/courses/admin/${courseId}/workspace/decision?revision=${workspace!.revision}`,
        {
          method: 'POST',
          body: body({ action: kind === 'approve' ? 'APPROVE' : 'REJECT', reason }),
        },
      );
    },
    onSuccess: (w) => {
      sessionStorage.removeItem(recoveryKey);
      setDirty(false);
      setWorkspace(structuredClone(w));
      client.setQueryData(['workspace', courseId], w);
      void client.invalidateQueries({ queryKey: ['own-courses'] });
      void client.invalidateQueries({ queryKey: ['workspace-queue'] });
      void client.invalidateQueries({ queryKey: ['course', courseId] });
    },
  });
  useEffect(() => {
    if (
      !dirty ||
      review ||
      workspace?.status === 'PENDING_REVIEW' ||
      uploading ||
      blocker.state === 'blocked' ||
      action.isPending ||
      action.isError ||
      recovery
    )
      return;
    const timer = setTimeout(() => action.mutate('save'), 3000);
    return () => clearTimeout(timer);
  }, [
    dirty,
    workspace,
    review,
    uploading,
    action.isPending,
    action.isError,
    recovery,
    blocker.state,
  ]);
  function change(update: (w: Workspace) => void) {
    setWorkspace((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      update(next);
      return next;
    });
    setDirty(true);
    action.reset();
  }
  if (query.isPending || !workspace)
    return query.error ? <ErrorBox error={query.error} /> : <Loading />;
  const w = workspace,
    c = w.document.course;
  const canManage = session?.user.id === c.instructorId || session?.user.role === 'ADMIN';
  const locked = review || w.status === 'PENDING_REVIEW';
  const publishedLessonIds = new Set(
    live.data?.publishedAt ? live.data.sections?.flatMap((s) => s.lessons.map((l) => l.id)) : [],
  );
  const lessons = c.sections?.flatMap((s) => s.lessons) || [];
  const lesson = lessons.find((l) => l.id === selected);
  function editLesson(update: (l: Lesson) => void) {
    change((next) => {
      const l = next.document.course
        .sections!.flatMap((s) => s.lessons)
        .find((x) => x.id === selected);
      if (l) update(l);
    });
  }
  function moveSection(index: number, direction: number) {
    change((next) => {
      const rows = next.document.course.sections!;
      const destination = index + direction;
      if (destination >= 0 && destination < rows.length)
        [rows[index], rows[destination]] = [rows[destination], rows[index]];
    });
  }
  function moveLesson(sectionId: string, index: number, direction: number) {
    change((next) => {
      const rows = next.document.course.sections!.find((s) => s.id === sectionId)!.lessons;
      const dest = index + direction;
      if (dest >= 0 && dest < rows.length) [rows[index], rows[dest]] = [rows[dest], rows[index]];
    });
  }
  function setQuiz(update: (q: Quiz) => void) {
    change((next) => {
      const q = next.document.quizzes[selected!] || blankQuiz();
      update(q);
      next.document.quizzes[selected!] = q;
    });
  }
  async function uploadMaterial(file: File, type: 'RESOURCE' | 'SUBTITLE') {
    setUploading(true);
    setUploadError(null);
    try {
      const form = new FormData();
      form.set('file', file);
      form.set('courseId', courseId);
      form.set('purpose', 'MATERIAL');
      const result = await api<{ name: string; url: string }>('/media/resources', {
        method: 'POST',
        body: form,
      });
      change((next) => {
        const rows = next.document.materials[selected!] || [];
        rows.push({
          title: result.name,
          url: result.url,
          type,
          language: type === 'SUBTITLE' ? 'tr' : undefined,
        });
        next.document.materials[selected!] = rows;
      });
    } catch (e) {
      setUploadError(e);
    } finally {
      setUploading(false);
    }
  }
  return (
    <>
      {blocker.state === 'blocked' && (
        <Modal title="Unsaved changes" onClose={() => blocker.reset()}>
          <p>Save your changes before leaving this course.</p>
          {action.error && <ErrorBox error={action.error} />}
          <div className="form-actions">
            <button
              className="button primary"
              disabled={action.isPending}
              onClick={async () => {
                try {
                  await action.mutateAsync('save');
                  blocker.proceed();
                } catch {
                  /* Keep the editor open. */
                }
              }}
            >
              Save and leave
            </button>
            <button
              className="button secondary"
              disabled={action.isPending}
              onClick={() => {
                sessionStorage.removeItem(recoveryKey);
                setDirty(false);
                blocker.proceed();
              }}
            >
              Discard changes
            </button>
            <button className="button secondary" onClick={() => blocker.reset()}>
              Keep editing
            </button>
          </div>
        </Modal>
      )}
      {recovery && (
        <Modal title="Recover your draft" onClose={() => setRecovery(undefined)}>
          <p>An unsaved draft was found in this browser.</p>
          {recovery.revision !== w.revision ? (
            <p>
              The server version has changed. Download your draft to compare it before applying
              changes.
            </p>
          ) : (
            <button
              className="button primary"
              onClick={() => {
                setWorkspace(recovery);
                setDirty(true);
                setRecovery(undefined);
              }}
            >
              Recover draft
            </button>
          )}
          <button
            className="button secondary"
            onClick={() => {
              const url = URL.createObjectURL(
                new Blob([JSON.stringify(recovery.document, null, 2)], {
                  type: 'application/json',
                }),
              );
              const anchor = document.createElement('a');
              anchor.href = url;
              anchor.download = 'course-draft.json';
              anchor.click();
              setTimeout(() => URL.revokeObjectURL(url), 1000);
            }}
          >
            Download draft
          </button>
          <button
            className="button secondary"
            onClick={() => {
              sessionStorage.removeItem(recoveryKey);
              setRecovery(undefined);
            }}
          >
            Use server version
          </button>
        </Modal>
      )}
      <Link className="back-link" to={review ? '/admin/courses' : '/instructor'}>
        ← {review ? 'Reviews' : 'My courses'}
      </Link>
      <PageTitle
        eyebrow={review ? 'COURSE REVIEW' : 'INSTRUCTOR STUDIO'}
        title={c.title}
        description="Approved changes are published to learners."
      />
      <div className="panel workspace-toolbar">
        <Badge value={w.status} />
        <span>
          Draft revision {w.revision}
          {dirty ? ' · Unsaved changes' : ''}
        </span>
        <div className="form-actions">
          <button className="button secondary" onClick={() => setPreview(!preview)}>
            {preview ? 'Back to editing' : 'Preview as a learner'}
          </button>
          {!review && !locked && (
            <>
              <button
                className="button primary"
                disabled={!dirty || action.isPending || uploading}
                onClick={() => action.mutate('save')}
              >
                Save draft
              </button>
              {canManage && (
                <button
                  className="button secondary"
                  disabled={dirty || w.revision === 0 || action.isPending || uploading}
                  onClick={() => action.mutate('submit')}
                >
                  Submit for review
                </button>
              )}
            </>
          )}
          {!review && canManage && locked && (
            <button
              className="button secondary"
              disabled={action.isPending}
              onClick={() => action.mutate('withdraw')}
            >
              Withdraw submission
            </button>
          )}
          {review && w.status === 'PENDING_REVIEW' && (
            <>
              <button
                className="button primary"
                disabled={action.isPending}
                onClick={() => action.mutate('approve')}
              >
                Approve and publish draft
              </button>
              <button
                className="button secondary"
                disabled={action.isPending}
                onClick={() => action.mutate('reject')}
              >
                Request changes
              </button>
            </>
          )}
          {!review && (
            <button
              className="button secondary"
              disabled={dirty || saveTemplate.isPending}
              onClick={() => {
                const name = window.prompt('Personal template name');
                if (name?.trim()) saveTemplate.mutate(name.trim());
              }}
            >
              Save as template
            </button>
          )}
          {!review && canManage && live.data?.status !== 'ARCHIVED' && (
            <button
              className="text-button danger"
              disabled={dirty || archive.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    'The course will close to new enrollments. Existing learners keep access. Any pending review will be withdrawn.',
                  )
                )
                  archive.mutate();
              }}
            >
              Archive course
            </button>
          )}
        </div>
      </div>
      {!review && !canManage && (
        <p className="notice">
          You are a co-instructor on this course. You can edit the draft; the owner submits it for
          publication.
        </p>
      )}
      {saveTemplate.error && <ErrorBox error={saveTemplate.error} />}
      {saveTemplate.isSuccess && (
        <Success>
          Personal template saved. Create a new course from your template list in the instructor
          studio.
        </Success>
      )}
      {!review && canManage && <LearningRules courseId={courseId} />}
      {archive.error && <ErrorBox error={archive.error} />}
      {archive.isSuccess && <Success>Course archived. Existing learners keep access.</Success>}
      {w.feedback && <p className="notice warning">Administrator feedback: {w.feedback}</p>}
      {action.error && <ErrorBox error={action.error} />}{' '}
      {action.isSuccess && <Success>Changes saved.</Success>}
      {!!w.checklist.length && (
        <details className="panel">
          <summary>Publication checklist ({w.checklist.length})</summary>
          <ul>
            {w.checklist.map((i) => (
              <li key={i}>{i}</li>
            ))}
          </ul>
        </details>
      )}
      {preview ? (
        <section className="panel">
          <h2>{c.title}</h2>
          <ReactMarkdown>{c.description || ''}</ReactMarkdown>
          <h3>Learning outcomes</h3>
          <ul>
            {c.outcomes?.map((o) => (
              <li key={o}>{o}</li>
            ))}
          </ul>
          {c.sections?.map((s) => (
            <div key={s.id}>
              <h3>{s.title}</h3>
              {s.lessons.map((l) => (
                <details key={l.id}>
                  <summary>
                    {l.title} · {lessonTypes[l.type]}
                  </summary>
                  {l.type === 'VIDEO' && l.videoUrl ? (
                    <VideoPlayer src={l.videoUrl} />
                  ) : (
                    <ReactMarkdown>{l.content || ''}</ReactMarkdown>
                  )}
                  {l.type === 'QUIZ' && (
                    <ol>
                      {w.document.quizzes[l.id]?.questions.map((q) => (
                        <li key={q.id}>
                          {q.prompt}
                          <ul>
                            {q.options.map((o, i) => (
                              <li key={i}>{o}</li>
                            ))}
                          </ul>
                        </li>
                      ))}
                    </ol>
                  )}
                </details>
              ))}
            </div>
          ))}
        </section>
      ) : (
        <>
          <fieldset className="panel workspace-fields" disabled={locked || action.isPending}>
            <legend>Course details</legend>
            <Field label="Course title">
              <input
                value={c.title}
                maxLength={200}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.title = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Short description">
              <textarea
                value={c.shortDescription || ''}
                maxLength={500}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.shortDescription = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Who is this course for?">
              <textarea
                value={c.targetAudience || ''}
                maxLength={2000}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.targetAudience = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Description">
              <RichTextEditor
                rows={6}
                value={c.description || ''}
                onChange={(value) =>
                  change((n) => {
                    n.document.course.description = value;
                  })
                }
              />
            </Field>
            <div className="form-grid">
              <Field label="Category">
                <select
                  value={w.document.categoryId}
                  onChange={(e) =>
                    change((n) => {
                      n.document.categoryId = Number(e.target.value);
                    })
                  }
                >
                  {categories.data?.map((cat) => (
                    <option value={cat.id} key={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Level">
                <select
                  value={c.level}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.level = e.target.value as Course['level'];
                    })
                  }
                >
                  {Object.entries(levels).map(([v, label]) => (
                    <option key={v} value={v}>
                      {label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Language">
                <input
                  value={c.language || ''}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.language = e.target.value;
                    })
                  }
                />
              </Field>
              <Field label="Price">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={c.price}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.price = Number(e.target.value);
                    })
                  }
                />
              </Field>
              <Field label="Discounted price">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={c.discountPrice ?? ''}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.discountPrice =
                        e.target.value === '' ? null : Number(e.target.value);
                    })
                  }
                />
              </Field>
            </div>
            <Field label="Upload course cover">
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                aria-label="Upload course cover"
                disabled={uploading}
                onChange={async (event) => {
                  const file = event.target.files?.[0];
                  if (!file) return;
                  setUploading(true);
                  setUploadError(undefined);
                  try {
                    const data = new FormData();
                    data.set('file', file);
                    data.set('courseId', courseId);
                    const uploaded = await api<Media & { publicUrl: string }>('/media/image', {
                      method: 'POST',
                      body: data,
                    });
                    change((next) => {
                      next.document.course.thumbnailUrl = uploaded.publicUrl;
                    });
                  } catch (error) {
                    setUploadError(error);
                  } finally {
                    setUploading(false);
                  }
                }}
              />
            </Field>
            <Field label="Course cover URL">
              <input
                value={c.thumbnailUrl || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.thumbnailUrl = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Requirements — one per line">
              <textarea
                value={c.requirements?.join('\n') || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.requirements = e.target.value.split('\n');
                  })
                }
              />
            </Field>
            <Field label="Learning outcomes — one per line">
              <textarea
                value={c.outcomes?.join('\n') || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.outcomes = e.target.value.split('\n');
                  })
                }
              />
            </Field>
          </fieldset>
          <section className="panel">
            <h2>Curriculum</h2>
            <p className="muted">
              Published lesson identities are preserved. Content and order can change; create a new
              course to remove lessons or change their types.
            </p>
            {c.sections?.map((s, si) => (
              <article key={s.id} className="editor-section">
                <div className="form-actions">
                  <input
                    aria-label={`Section ${si + 1} title`}
                    disabled={locked}
                    value={s.title}
                    onChange={(e) =>
                      change((n) => {
                        n.document.course.sections![si].title = e.target.value;
                      })
                    }
                  />
                  <button
                    className="button secondary small"
                    disabled={locked || si === 0}
                    aria-label={`${s.title} move section up`}
                    onClick={() => moveSection(si, -1)}
                  >
                    ↑
                  </button>
                  <button
                    className="button secondary small"
                    disabled={locked || si === c.sections!.length - 1}
                    aria-label={`${s.title} move section down`}
                    onClick={() => moveSection(si, 1)}
                  >
                    ↓
                  </button>
                  <button
                    className="text-button danger"
                    disabled={locked || s.lessons.some((l) => publishedLessonIds.has(l.id))}
                    onClick={() => {
                      if (
                        window.confirm(
                          'Remove this section from the draft? Published lessons cannot be removed.',
                        )
                      )
                        change((n) => {
                          n.document.course.sections!.splice(si, 1);
                        });
                    }}
                  >
                    Remove section
                  </button>
                </div>
                {s.lessons.map((l, li) => (
                  <div className="lesson-row" key={l.id}>
                    <button className="text-button" onClick={() => setSelected(l.id)}>
                      {l.title} · {lessonTypes[l.type]}
                    </button>
                    <button
                      className="icon-button"
                      disabled={locked || li === 0}
                      aria-label={`${l.title} move lesson up`}
                      onClick={() => moveLesson(s.id, li, -1)}
                    >
                      ↑
                    </button>
                    <button
                      className="icon-button"
                      disabled={locked || li === s.lessons.length - 1}
                      aria-label={`${l.title} move lesson down`}
                      onClick={() => moveLesson(s.id, li, 1)}
                    >
                      ↓
                    </button>
                    <button
                      className="button secondary small"
                      disabled={locked}
                      aria-label={`${l.title} duplicate lesson`}
                      onClick={() =>
                        change((next) => {
                          duplicateLesson(next, l.id);
                        })
                      }
                    >
                      Duplicate lesson
                    </button>
                  </div>
                ))}
                {!locked && (
                  <button
                    className="button secondary small"
                    onClick={() => {
                      const id = crypto.randomUUID();
                      change((n) => {
                        n.document.course.sections![si].lessons.push({
                          id,
                          title: 'New lesson',
                          type: 'ARTICLE',
                          orderIndex: s.lessons.length,
                          previewAllowed: false,
                          content: '',
                        });
                      });
                      setSelected(id);
                    }}
                  >
                    Add lesson
                  </button>
                )}
              </article>
            ))}
            {!locked && (
              <div className="form-actions">
                <button
                  className="button secondary"
                  onClick={() =>
                    change((n) => {
                      n.document.course.sections ||= [];
                      n.document.course.sections.push({
                        id: crypto.randomUUID(),
                        title: 'New section',
                        orderIndex: n.document.course.sections.length,
                        lessons: [],
                      });
                    })
                  }
                >
                  Add section
                </button>
                <Link className="button secondary" to={`/media?course=${courseId}`}>
                  Upload videos
                </Link>
              </div>
            )}
          </section>
        </>
      )}
      {!review && (
        <>
          <div className="feature-tabs">
            {[
              ['students', 'Learners'],
              ['reports', 'Learning report'],
              ['announcements', 'Announcements'],
              ['questions', 'Questions and answers'],
              ['versions', 'Publication history'],
              ['team', 'Course team'],
            ]
              .filter(([key]) => canManage || ['team', 'versions'].includes(key))
              .map(([key, label]) => (
                <button
                  className="button secondary"
                  key={key}
                  onClick={() => setTool(tool === key ? undefined : key)}
                >
                  {label}
                </button>
              ))}
          </div>
          {tool === 'students' && <CourseStudents courseId={courseId} />}{' '}
          {tool === 'reports' && <CourseAnalytics courseId={courseId} course={c} />}{' '}
          {tool === 'announcements' && <Announcements courseId={courseId} instructor />}
          {tool === 'questions' && (
            <CourseDiscussion courseId={courseId} instructorId={c.instructorId} />
          )}{' '}
          {tool === 'versions' && <VersionHistory courseId={courseId} />}
          {tool === 'team' && <CourseTeam courseId={courseId} />}
        </>
      )}
      {lesson && (
        <Modal title={lesson.title} onClose={() => setSelected(undefined)}>
          <fieldset disabled={locked || uploading} className="workspace-fields">
            <Field label="Lesson title">
              <input
                value={lesson.title}
                maxLength={200}
                onChange={(e) =>
                  editLesson((l) => {
                    l.title = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Lesson type">
              <select
                disabled={publishedLessonIds.has(lesson.id)}
                value={lesson.type}
                onChange={(e) =>
                  editLesson((l) => {
                    l.type = e.target.value as Lesson['type'];
                  })
                }
              >
                {Object.entries(lessonTypes).map(([v, label]) => (
                  <option key={v} value={v}>
                    {label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Section">
              <select
                value={c.sections?.find((s) => s.lessons.some((l) => l.id === lesson.id))?.id}
                onChange={(e) =>
                  change((n) => {
                    const sections = n.document.course.sections!;
                    const source = sections.find((s) => s.lessons.some((l) => l.id === lesson.id))!;
                    source.lessons = source.lessons.filter((l) => l.id !== lesson.id);
                    sections.find((s) => s.id === e.target.value)!.lessons.push(lesson);
                  })
                }
              >
                {c.sections?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>
            </Field>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={lesson.previewAllowed}
                onChange={(e) =>
                  editLesson((l) => {
                    l.previewAllowed = e.target.checked;
                  })
                }
              />{' '}
              Allow public preview of this lesson
            </label>
            <Field
              label={
                lesson.type === 'ASSIGNMENT'
                  ? 'Assignment instructions and assessment criteria'
                  : 'Lesson text'
              }
            >
              <RichTextEditor
                rows={7}
                value={lesson.content || ''}
                onChange={(value) =>
                  editLesson((l) => {
                    l.content = value;
                  })
                }
              />
            </Field>
            {lesson.type === 'VIDEO' && (
              <>
                <Field label="Search course videos">
                  <input
                    aria-label="Search course videos"
                    placeholder="Search videos by filename"
                    value={mediaSearch}
                    onChange={(e) => {
                      setMediaSearch(e.target.value);
                      setMediaPage(0);
                    }}
                  />
                </Field>
                <Field label="Lesson video">
                  <select
                    value={lesson.videoUrl || ''}
                    onChange={(e) =>
                      editLesson((l) => {
                        l.videoUrl = e.target.value;
                      })
                    }
                  >
                    <option value="">Select video</option>
                    {lesson.videoUrl &&
                      !media.data?.content.some((m) => m.streamUrl === lesson.videoUrl) && (
                        <option value={lesson.videoUrl}>Current video</option>
                      )}
                    {media.data?.content
                      .filter(
                        (m) =>
                          m.type === 'VIDEO' &&
                          m.status === 'READY' &&
                          (!m.courseId || m.courseId === courseId),
                      )
                      .map((m) => (
                        <option key={m.id} value={m.streamUrl}>
                          {m.originalFileName}
                        </option>
                      ))}
                  </select>
                </Field>
                {media.error && <ErrorBox error={media.error} />}
                <Pagination
                  page={mediaPage}
                  pages={media.data?.totalPages || 0}
                  onChange={setMediaPage}
                />
                <p className="muted">Processed videos appear when they are ready.</p>
              </>
            )}
            {lesson.type === 'ASSIGNMENT' && (
              <AssignmentPolicyEditor
                value={w.document.assignments?.[lesson.id]}
                onChange={(policy) =>
                  change((next) => {
                    next.document.assignments = {
                      ...next.document.assignments,
                      [lesson.id]: policy,
                    };
                  })
                }
              />
            )}
            {lesson.type === 'QUIZ' && (
              <>
                <h3>Quiz and question bank</h3>
                <div className="form-grid">
                  <Field label="Passing score">
                    <input
                      type="number"
                      min="1"
                      max="100"
                      value={w.document.quizzes[lesson.id]?.passingScore ?? 70}
                      onChange={(e) =>
                        setQuiz((q) => {
                          q.passingScore = Number(e.target.value);
                        })
                      }
                    />
                  </Field>
                  {[
                    ['maxAttempts', 'Attempt limit (blank: unlimited)'],
                    ['timeLimitSeconds', 'Time limit (seconds, blank: unlimited)'],
                    ['questionCount', 'Number of questions to select from the bank'],
                  ].map(([key, label]) => (
                    <Field key={key} label={label}>
                      <input
                        type="number"
                        min="1"
                        value={w.document.quizzes[lesson.id]?.[key as 'maxAttempts'] ?? ''}
                        onChange={(e) =>
                          setQuiz((q) => {
                            q[key as 'maxAttempts'] = e.target.value
                              ? Number(e.target.value)
                              : undefined;
                          })
                        }
                      />
                    </Field>
                  ))}
                </div>
                <label>
                  <input
                    type="checkbox"
                    checked={w.document.quizzes[lesson.id]?.randomize ?? false}
                    onChange={(e) =>
                      setQuiz((q) => {
                        q.randomize = e.target.checked;
                      })
                    }
                  />{' '}
                  Randomize question selection and order
                </label>
                {w.document.quizzes[lesson.id]?.questions.map((q, qi) => (
                  <article className="panel" key={q.id}>
                    <Field label={`Question ${qi + 1}`}>
                      <textarea
                        value={q.prompt}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].prompt = e.target.value;
                          })
                        }
                      />
                    </Field>
                    {q.options.map((o, oi) => (
                      <Field key={oi} label={`Option ${oi + 1}`}>
                        <input
                          value={o}
                          onChange={(e) =>
                            setQuiz((quiz) => {
                              quiz.questions[qi].options[oi] = e.target.value;
                            })
                          }
                        />
                      </Field>
                    ))}
                    <Field label="Question type">
                      <select
                        value={q.questionType || 'SINGLE_CHOICE'}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].questionType = e.target.value as
                              'SINGLE_CHOICE' | 'MULTIPLE_CHOICE';
                            quiz.questions[qi].correctOptions = [q.correctOption || 0];
                          })
                        }
                      >
                        <option value="SINGLE_CHOICE">Single answer</option>
                        <option value="MULTIPLE_CHOICE">Multiple answers</option>
                      </select>
                    </Field>
                    <Field label="Question weight">
                      <input
                        type="number"
                        min={1}
                        max={100}
                        value={q.weight || 1}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].weight = Number(e.target.value);
                          })
                        }
                      />
                    </Field>
                    {q.questionType === 'MULTIPLE_CHOICE' && (
                      <fieldset>
                        <legend>Correct answers</legend>
                        {q.options.map((option, index) => (
                          <label key={index}>
                            <input
                              type="checkbox"
                              checked={(q.correctOptions || [q.correctOption || 0]).includes(index)}
                              onChange={(e) =>
                                setQuiz((quiz) => {
                                  const current = q.correctOptions || [q.correctOption || 0];
                                  quiz.questions[qi].correctOptions = e.target.checked
                                    ? [...current, index]
                                    : current.filter((i) => i !== index);
                                })
                              }
                            />
                            {option || `Option ${index + 1}`}
                          </label>
                        ))}
                      </fieldset>
                    )}
                    {q.questionType !== 'MULTIPLE_CHOICE' && (
                      <Field label="Correct option">
                        <select
                          value={q.correctOption ?? 0}
                          onChange={(e) =>
                            setQuiz((quiz) => {
                              quiz.questions[qi].correctOption = Number(e.target.value);
                              quiz.questions[qi].correctOptions = [Number(e.target.value)];
                            })
                          }
                        >
                          {q.options.map((_, i) => (
                            <option value={i} key={i}>
                              {i + 1}
                            </option>
                          ))}
                        </select>
                      </Field>
                    )}
                    <Field label="Answer explanation">
                      <textarea
                        value={q.explanation || ''}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].explanation = e.target.value;
                          })
                        }
                      />
                    </Field>
                    <div className="form-actions">
                      <button
                        className="button secondary small"
                        disabled={q.options.length >= 8}
                        onClick={() =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].options.push('');
                          })
                        }
                      >
                        Add option
                      </button>
                      <button
                        className="text-button danger"
                        onClick={() =>
                          setQuiz((quiz) => {
                            quiz.questions.splice(qi, 1);
                          })
                        }
                      >
                        Remove question
                      </button>
                    </div>
                  </article>
                ))}
                <button
                  className="button secondary"
                  onClick={() =>
                    setQuiz((q) => {
                      q.questions.push(blankQuestion());
                    })
                  }
                >
                  Add question
                </button>
              </>
            )}
            <h3>Resources and subtitles</h3>
            {(w.document.materials[lesson.id] || []).map((m, i) => (
              <div className="panel" key={i}>
                <Field label="Resource name">
                  <input
                    value={m.title}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].title = e.target.value;
                      })
                    }
                  />
                </Field>
                <Field label="URL">
                  <input
                    value={m.url}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].url = e.target.value;
                      })
                    }
                  />
                </Field>
                <Field label="Type">
                  <select
                    value={m.type}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].type = e.target
                          .value as Material['type'];
                      })
                    }
                  >
                    <option value="RESOURCE">Downloadable resource</option>
                    <option value="SUBTITLE">WebVTT subtitles</option>
                  </select>
                </Field>
                {m.type === 'SUBTITLE' && (
                  <Field label="Language code">
                    <input
                      value={m.language || ''}
                      onChange={(e) =>
                        change((n) => {
                          n.document.materials[lesson.id][i].language = e.target.value;
                        })
                      }
                    />
                  </Field>
                )}
                <button
                  className="text-button danger"
                  onClick={() =>
                    change((n) => {
                      n.document.materials[lesson.id].splice(i, 1);
                    })
                  }
                >
                  Remove resource
                </button>
              </div>
            ))}
            <div className="form-actions">
              <button
                className="button secondary"
                onClick={() =>
                  change((n) => {
                    n.document.materials[lesson.id] ||= [];
                    n.document.materials[lesson.id].push({
                      title: 'New resource',
                      url: '',
                      type: 'RESOURCE',
                    });
                  })
                }
              >
                Add link
              </button>
              <label className="button secondary">
                Upload file
                <input
                  className="sr-only"
                  type="file"
                  accept=".pdf,.txt,.vtt"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f)
                      void uploadMaterial(
                        f,
                        f.name.toLowerCase().endsWith('.vtt') ? 'SUBTITLE' : 'RESOURCE',
                      );
                    e.target.value = '';
                  }}
                />
              </label>
            </div>
            <button
              className="text-button danger"
              disabled={publishedLessonIds.has(lesson.id)}
              onClick={() => {
                if (window.confirm('Remove this lesson from the draft?')) {
                  change((n) => {
                    for (const s of n.document.course.sections!)
                      s.lessons = s.lessons.filter((l) => l.id !== lesson.id);
                    delete n.document.quizzes[lesson.id];
                    delete n.document.materials[lesson.id];
                  });
                  setSelected(undefined);
                }
              }}
            >
              Remove lesson from draft
            </button>
          </fieldset>
          {!!uploadError && <ErrorBox error={uploadError} />}{' '}
          {uploading && <p role="status">Uploading file…</p>}
          {!review && canManage && lesson.type === 'ASSIGNMENT' && (
            <AssignmentGrading courseId={courseId} lessonId={lesson.id} />
          )}
          <p className="muted">Lesson changes are saved with Save draft on the main editor.</p>
          <button className="button primary" onClick={() => setSelected(undefined)}>
            Finish editing
          </button>
        </Modal>
      )}
    </>
  );
}
function VersionHistory({ courseId }: { courseId: string }) {
  const q = useQuery({
    queryKey: ['versions', courseId],
    queryFn: () =>
      api<{ version_number: number; published_at: string }[]>(`/courses/${courseId}/versions`),
  });
  return (
    <section className="panel">
      <h2>Publication history</h2>
      {q.error ? (
        <ErrorBox error={q.error} />
      ) : q.isPending ? (
        <Loading />
      ) : (
        <ul>
          {q.data?.map((v) => (
            <li key={v.version_number}>
              Version {v.version_number} · {new Date(v.published_at).toLocaleString('en-US')}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
