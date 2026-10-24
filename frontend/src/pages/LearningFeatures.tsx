import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import { api, body, date, safeUrl, fetchResource } from '../api';
import { useAuth } from '../auth';
import type {
  AssignmentSubmission,
  CourseQuestion,
  QuestionReply,
  Quiz,
  QuizAttempt,
  LessonNote,
  Page,
  RosterRow,
  AssignmentPolicy,
} from '../types';
import { Badge, Empty, ErrorBox, Field, Loading, Pagination, Submit, Success } from '../ui';

export function QuizLesson({
  courseId,
  lessonId,
  enrollmentId,
  onCompleted,
}: {
  courseId: string;
  lessonId: string;
  enrollmentId: string;
  onCompleted: () => void;
}) {
  const path = `/courses/${courseId}/lessons/${lessonId}/quiz`;
  const client = useQueryClient();
  const { session } = useAuth();
  const [answers, setAnswers] = useState<Record<string, number[]>>({});
  const [active, setActive] = useState<{ id: string; expiresAt: string; quiz: Quiz }>();
  const [now, setNow] = useState(Date.now());
  const quiz = useQuery({ queryKey: ['quiz', lessonId], queryFn: () => api<Quiz>(path) });
  const attempts = useQuery({
    queryKey: ['quiz-attempts', lessonId],
    queryFn: () => api<QuizAttempt[]>(`${path}/attempts`),
  });
  const start = useMutation({
    mutationFn: () =>
      api<{ id: string; expiresAt: string; quiz: Quiz }>(`${path}/start`, {
        method: 'POST',
        body: body({ enrollmentId }),
      }),
    onSuccess: (value) => {
      setActive(value);
      try {
        setAnswers(
          JSON.parse(
            sessionStorage.getItem(
              `lms.quiz.${session?.user.id}.${enrollmentId}.${lessonId}.${value.id}`,
            ) || '{}',
          ),
        );
      } catch {
        setAnswers({});
      }
      setNow(Date.now());
      submit.reset();
    },
  });
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
  const actual = active?.quiz || quiz.data;
  const answerKey = `lms.quiz.${session?.user.id}.${enrollmentId}.${lessonId}.${active?.id || 'open'}`;
  useEffect(() => {
    if (!Object.keys(answers).length) return;
    try {
      sessionStorage.setItem(answerKey, JSON.stringify(answers));
    } catch {
      /* Submission remains available when browser storage is full. */
    }
  }, [answers, answerKey]);
  const managed = !!(
    quiz.data?.maxAttempts ||
    quiz.data?.timeLimitSeconds ||
    quiz.data?.questionCount ||
    quiz.data?.randomize
  );
  const remaining = active
    ? Math.max(0, Math.floor((new Date(active.expiresAt).getTime() - now) / 1000))
    : undefined;
  const expired = remaining === 0;
  const submit = useMutation({
    mutationFn: () =>
      api<QuizAttempt>(`${path}/attempts`, {
        method: 'POST',
        body: body({
          enrollmentId,
          sessionId: active?.id,
          answers: actual!.questions.map((q) => ({
            questionId: q.id,
            option: answers[q.id]?.[0],
            options: answers[q.id],
          })),
        }),
      }),
    onSuccess: (result) => {
      sessionStorage.removeItem(answerKey);
      void client.invalidateQueries({ queryKey: ['quiz-attempts', lessonId] });
      if (result.passed) onCompleted();
    },
  });
  if (quiz.isPending) return <Loading />;
  if (quiz.error) return <ErrorBox error={quiz.error} retry={() => void quiz.refetch()} />;
  return (
    <section className="feature-section">
      <h3>Lesson quiz</h3>
      <p>
        Passing score: {actual!.passingScore}%.
        {quiz.data?.maxAttempts ? ` Attempt limit: ${quiz.data.maxAttempts}.` : ''}
        {quiz.data?.timeLimitSeconds ? ` Time limit: ${quiz.data.timeLimitSeconds} saniye.` : ''}
      </p>
      {managed && !active ? (
        <button
          className="button primary"
          disabled={start.isPending}
          onClick={() => start.mutate()}
        >
          Start / resume quiz
        </button>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit.mutate();
          }}
        >
          {active && quiz.data?.timeLimitSeconds && (
            <p role="timer">Time remaining: {remaining} seconds</p>
          )}
          <fieldset
            disabled={expired || submit.isSuccess || submit.isPending}
            className="workspace-fields"
          >
            {actual!.questions.map((q, i) => (
              <fieldset className="quiz-question" key={q.id}>
                <legend>
                  {i + 1}. {q.prompt}
                </legend>
                {q.options.map((option, index) => (
                  <label className="checkbox-field" key={index}>
                    <input
                      type={q.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
                      name={q.id}
                      required={q.questionType !== 'MULTIPLE_CHOICE'}
                      checked={answers[q.id]?.includes(index) || false}
                      onChange={(event) =>
                        setAnswers({
                          ...answers,
                          [q.id]:
                            q.questionType === 'MULTIPLE_CHOICE'
                              ? event.target.checked
                                ? [...(answers[q.id] || []), index]
                                : (answers[q.id] || []).filter((value) => value !== index)
                              : [index],
                        })
                      }
                    />
                    {option}
                  </label>
                ))}
              </fieldset>
            ))}
            <Submit busy={submit.isPending}>Submit quiz</Submit>
          </fieldset>
          {submit.error && <ErrorBox error={submit.error} />}{' '}
          {submit.data && (
            <div className={`notice ${submit.data.passed ? 'success' : 'warning'}`} role="status">
              Your score: {submit.data.score}%.{' '}
              {submit.data.passed
                ? 'You passed the quiz and completed the lesson.'
                : 'You have not reached the passing score.'}
              <ul>
                {submit.data.feedback?.map((f) => (
                  <li key={f.questionId}>
                    {actual!.questions.find((q) => q.id === f.questionId)?.prompt}:{' '}
                    {f.correct ? 'Correct' : 'Review this topic'}
                    {f.explanation ? ` — ${f.explanation}` : ''}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {(submit.isSuccess || expired) && (
            <>
              <p>
                {expired
                  ? 'The quiz has expired. A new attempt uses one of your remaining attempts.'
                  : ''}
              </p>
              <button
                type="button"
                className="button secondary"
                disabled={start.isPending}
                onClick={() => (managed ? start.mutate() : (submit.reset(), setAnswers({})))}
              >
                Try again
              </button>
            </>
          )}
        </form>
      )}
      {start.error && <ErrorBox error={start.error} />}{' '}
      {attempts.error && <ErrorBox error={attempts.error} />}{' '}
      {!!attempts.data?.length && (
        <details>
          <summary>Previous attempts ({attempts.data.length})</summary>
          <ul>
            {attempts.data.map((a) => (
              <li key={a.id}>
                {a.score}% · {a.passed ? 'Passed' : 'Try again'}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

export function AssignmentLesson({
  courseId,
  lessonId,
  enrollmentId,
  onCompleted,
}: {
  courseId: string;
  lessonId: string;
  enrollmentId: string;
  onCompleted: () => void;
}) {
  const path = `/courses/${courseId}/lessons/${lessonId}/assignment`;
  const [attachment, setAttachment] = useState<string>();
  const [uploadError, setUploadError] = useState<unknown>();
  const [uploading, setUploading] = useState(false);
  const client = useQueryClient();
  const history = useQuery({
    queryKey: ['assignment-history', lessonId],
    queryFn: () =>
      api<{ id: string; content: string; status: string; feedback?: string; created_at: string }[]>(
        `${path}/history`,
      ),
  });
  const submission = useQuery({
    queryKey: ['assignment', lessonId],
    queryFn: () => api<AssignmentSubmission | null>(`${path}/my`),
    refetchInterval: (q) => (q.state.data?.status === 'SUBMITTED' ? 15000 : false),
  });
  const send = useMutation({
    mutationFn: (data: unknown) =>
      api<AssignmentSubmission>(path, { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['assignment', lessonId] });
      void client.invalidateQueries({ queryKey: ['assignment-history', lessonId] });
      onCompleted();
    },
  });
  if (submission.isPending) return <Loading />;
  if (submission.error)
    return <ErrorBox error={submission.error} retry={() => void submission.refetch()} />;
  const s = submission.data;
  return (
    <section className="feature-section">
      <h3>Assignment submission</h3>
      <AssignmentInformation courseId={courseId} lessonId={lessonId} />
      {!!history.data?.length && (
        <details>
          <summary>Submission history ({history.data.length})</summary>
          {history.data.map((h) => (
            <article className="notice" key={h.id}>
              <small>{date(h.created_at)}</small>
              <p className="pre-wrap">{h.content}</p>
              {h.feedback && <p>Instructor: {h.feedback}</p>}
            </article>
          ))}
        </details>
      )}
      {s && (
        <>
          <Badge value={s.status} />
          {s.score != null && <p>Score: {s.score}/100</p>}
          {s.feedback && (
            <div className="notice">
              <b>Instructor feedback</b>
              <p>{s.feedback}</p>
            </div>
          )}
        </>
      )}
      {s?.status === 'ACCEPTED' ? (
        <Success>Your assignment has been accepted. The lesson is complete.</Success>
      ) : (
        <form
          key={s?.submittedAt || 'new'}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            send.mutate({
              enrollmentId,
              content: f.get('content'),
              attachmentUrl:
                attachment ?? (String(f.get('attachmentUrl') || '') || s?.attachmentUrl || null),
            });
          }}
        >
          <Field label="Your assignment response">
            <textarea
              name="content"
              rows={6}
              required
              maxLength={20000}
              defaultValue={s?.content}
            />
          </Field>
          {s?.attachmentUrl?.startsWith('/api/v1/media/resources/') && (
            <ProtectedDownload url={s.attachmentUrl} />
          )}
          <Field label="Assignment file (PDF or TXT, up to 25 MB)">
            <input
              type="file"
              accept=".pdf,.txt"
              disabled={uploading}
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                setUploading(true);
                setUploadError(null);
                try {
                  const form = new FormData();
                  form.set('file', file);
                  form.set('purpose', 'ASSIGNMENT');
                  form.set('courseId', courseId);
                  const resource = await api<{ url: string }>('/media/resources', {
                    method: 'POST',
                    body: form,
                  });
                  setAttachment(resource.url);
                } catch (e) {
                  setUploadError(e);
                } finally {
                  setUploading(false);
                }
              }}
            />
          </Field>
          {attachment && (
            <p className="notice success">
              File uploaded. It will be attached when you submit the assignment.
            </p>
          )}
          {!!uploadError && <ErrorBox error={uploadError} />}{' '}
          {uploading && <p role="status">Uploading file…</p>}
          <Field label="Work URL (optional)">
            <input
              name="attachmentUrl"
              type="url"
              maxLength={2000}
              placeholder="https://…"
              defaultValue={s?.attachmentUrl?.startsWith('/api/') ? '' : s?.attachmentUrl}
            />
          </Field>
          {send.error && <ErrorBox error={send.error} />}
          <Submit busy={send.isPending || uploading}>
            {s ? 'Update assignment' : 'Submit assignment'}
          </Submit>
        </form>
      )}
    </section>
  );
}

function AssignmentInformation({ courseId, lessonId }: { courseId: string; lessonId: string }) {
  const path = `/courses/${courseId}/lessons/${lessonId}/assignment`;
  const policy = useQuery({
    queryKey: ['assignment-policy', lessonId],
    queryFn: () => api<AssignmentPolicy>(`${path}/policy`),
  });
  const submission = useQuery({
    queryKey: ['assignment', lessonId],
    queryFn: () => api<AssignmentSubmission | null>(`${path}/my`),
  });
  return (
    <>
      {policy.data?.dueAt && (
        <p>
          Submission deadline: {date(policy.data.dueAt)} ·{' '}
          {policy.data.allowLate ? 'Late submissions are accepted' : 'Late submissions are closed'}
        </p>
      )}
      {!!policy.data?.rubric.length && (
        <details>
          <summary>Grading rubric</summary>
          <ul>
            {policy.data.rubric.map((criterion) => (
              <li key={criterion.id}>
                {criterion.label}: {criterion.maxPoints} points
              </li>
            ))}
          </ul>
        </details>
      )}
      {submission.data?.late && (
        <p className="notice warning">This work was submitted after the deadline.</p>
      )}
      {!!submission.data?.rubric?.length && (
        <ul>
          {submission.data.rubric.map((criterion) => (
            <li key={criterion.id}>
              {criterion.label}: {submission.data!.criterionScores?.[criterion.id] ?? '—'} /{' '}
              {criterion.maxPoints}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export function AssignmentGrading({ courseId, lessonId }: { courseId: string; lessonId: string }) {
  const [page, setPage] = useState(0);
  const client = useQueryClient();
  const path = `/courses/${courseId}/lessons/${lessonId}/assignment/submissions`;
  const submissions = useQuery({
    queryKey: ['assignment-grading', lessonId, page],
    queryFn: () => api<Page<AssignmentSubmission>>(`${path}?page=${page}&size=10`),
  });
  const grade = useMutation({
    mutationFn: ({
      id,
      ...data
    }: {
      id: string;
      status: string;
      score: number | null;
      feedback: string;
      criterionScores: Record<string, number>;
    }) => api(`${path}/${id}`, { method: 'PUT', body: body(data) }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['assignment-grading', lessonId] }),
  });
  if (submissions.isPending) return <Loading />;
  if (submissions.error) return <ErrorBox error={submissions.error} />;
  return (
    <>
      {grade.error && <ErrorBox error={grade.error} />}{' '}
      {submissions.data?.content.length ? (
        submissions.data.content.map((s) => (
          <article className="feature-section" key={s.id}>
            <h3>{s.studentName}</h3>
            <Badge value={s.status} />
            <div className="prose">
              <ReactMarkdown>{s.content}</ReactMarkdown>
            </div>
            {s.attachmentUrl?.startsWith('/api/v1/media/resources/') ? (
              <ProtectedDownload url={s.attachmentUrl} />
            ) : (
              safeUrl(s.attachmentUrl) && (
                <a href={safeUrl(s.attachmentUrl)} target="_blank" rel="noreferrer">
                  Open submitted work
                </a>
              )
            )}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                grade.mutate({
                  id: s.id,
                  status: String(f.get('status')),
                  score: f.get('score') ? Number(f.get('score')) : null,
                  feedback: String(f.get('feedback') || ''),
                  criterionScores: Object.fromEntries(
                    (s.rubric || [])
                      .filter((c) => f.get(`criterion-${c.id}`) !== '')
                      .map((c) => [c.id, Number(f.get(`criterion-${c.id}`))]),
                  ),
                });
              }}
            >
              <Field label={`${s.studentName}, assessment`}>
                <select
                  name="status"
                  defaultValue={s.status === 'CHANGES_REQUESTED' ? 'CHANGES_REQUESTED' : 'ACCEPTED'}
                >
                  <option value="ACCEPTED">Accept and complete lesson</option>
                  <option value="CHANGES_REQUESTED" disabled={s.status === 'ACCEPTED'}>
                    Request changes
                  </option>
                </select>
              </Field>
              {s.rubric?.map((criterion) => (
                <Field
                  key={criterion.id}
                  label={`${s.studentName}: ${criterion.label} (max ${criterion.maxPoints})`}
                >
                  <input
                    name={`criterion-${criterion.id}`}
                    type="number"
                    min={0}
                    max={criterion.maxPoints}
                    defaultValue={s.criterionScores?.[criterion.id]}
                  />
                </Field>
              ))}
              <Field label={`${s.studentName}, score`}>
                <input
                  name="score"
                  type="number"
                  min={0}
                  max={100}
                  defaultValue={s.score}
                  disabled={!!s.rubric?.length}
                />
              </Field>
              <Field label={`${s.studentName}, feedback`}>
                <textarea name="feedback" maxLength={10000} defaultValue={s.feedback} />
              </Field>
              <Submit busy={grade.isPending}>Save assessment</Submit>
            </form>
          </article>
        ))
      ) : (
        <Empty
          title="No assignments have been submitted yet"
          text="Learner submissions will appear here."
        />
      )}
      <Pagination page={page} pages={submissions.data?.totalPages || 0} onChange={setPage} />
    </>
  );
}

export function LessonNotes({
  enrollmentId,
  lessonId,
  sectionId,
  position,
  onSeek,
}: {
  enrollmentId: string;
  lessonId: string;
  sectionId: string;
  position: number;
  onSeek: (position: number) => void;
}) {
  const client = useQueryClient();
  const [editing, setEditing] = useState<LessonNote>();
  const path = `/progress/${enrollmentId}/notes`;
  const notes = useQuery({
    queryKey: ['notes', enrollmentId, lessonId],
    queryFn: () => api<LessonNote[]>(`${path}?lessonId=${lessonId}`),
  });
  const save = useMutation({
    mutationFn: (content: string) =>
      api(`${path}${editing ? `/${editing.id}` : ''}`, {
        method: editing ? 'PUT' : 'POST',
        body: body({
          lessonId,
          sectionId,
          positionSeconds: editing?.positionSeconds ?? Math.floor(position),
          content,
        }),
      }),
    onSuccess: () => {
      setEditing(undefined);
      void client.invalidateQueries({ queryKey: ['notes', enrollmentId, lessonId] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`${path}/${id}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['notes', enrollmentId, lessonId] }),
  });
  return (
    <section className="feature-section">
      <h3>My private lesson notes</h3>
      <p className="muted">
        Only you can see your notes. New note position: {Math.floor(position / 60)}:
        {String(Math.floor(position) % 60).padStart(2, '0')}
      </p>
      <form
        key={editing?.id || String(save.isSuccess)}
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate(String(new FormData(e.currentTarget).get('content')));
        }}
      >
        <Field label="Your lesson note">
          <textarea
            name="content"
            required
            maxLength={10000}
            rows={3}
            defaultValue={editing?.content}
          />
        </Field>
        <Submit busy={save.isPending}>{editing ? 'Update note' : 'Add note'}</Submit>
        {editing && (
          <button type="button" className="text-button" onClick={() => setEditing(undefined)}>
            Cancel
          </button>
        )}
      </form>
      {save.error && <ErrorBox error={save.error} />}{' '}
      {remove.error && <ErrorBox error={remove.error} />}{' '}
      {notes.isPending ? (
        <Loading />
      ) : notes.error ? (
        <ErrorBox error={notes.error} />
      ) : (
        notes.data?.map((note) => (
          <article key={note.id} className="note-item">
            <button className="text-button" onClick={() => onSeek(note.positionSeconds)}>
              {Math.floor(note.positionSeconds / 60)}:
              {String(note.positionSeconds % 60).padStart(2, '0')}
            </button>
            <p>{note.content}</p>
            <div className="form-actions">
              <button className="text-button" onClick={() => setEditing(note)}>
                Edit note
              </button>
              <button
                className="text-button danger"
                disabled={remove.isPending}
                onClick={() => remove.mutate(note.id)}
              >
                Delete note
              </button>
            </div>
          </article>
        ))
      )}
    </section>
  );
}

export function CourseDiscussion({
  courseId,
  lessonId,
  instructorId,
}: {
  courseId: string;
  lessonId?: string;
  instructorId?: string;
}) {
  const { session } = useAuth();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [unanswered, setUnanswered] = useState(false);
  const [open, setOpen] = useState<CourseQuestion>();
  const path = `/courses/${courseId}/questions`;
  const questions = useQuery({
    queryKey: ['questions', courseId, lessonId, page, query, unanswered],
    queryFn: () =>
      api<Page<CourseQuestion>>(
        `${path}?page=${page}&size=10&query=${encodeURIComponent(query)}&unanswered=${unanswered}${lessonId ? `&lessonId=${lessonId}` : ''}`,
      ),
  });
  const replies = useQuery({
    queryKey: ['replies', open?.id],
    queryFn: () => api<QuestionReply[]>(`${path}/${open!.id}/replies`),
    enabled: !!open,
  });
  const refresh = () => {
    void client.invalidateQueries({ queryKey: ['questions', courseId] });
    if (open) void client.invalidateQueries({ queryKey: ['replies', open.id] });
  };
  const ask = useMutation({
    mutationFn: (data: unknown) => api<CourseQuestion>(path, { method: 'POST', body: body(data) }),
    onSuccess: (question) => {
      setOpen(question);
      refresh();
    },
  });
  const reply = useMutation({
    mutationFn: (content: string) =>
      api(`${path}/${open!.id}/replies`, { method: 'POST', body: body({ content }) }),
    onSuccess: refresh,
  });
  const accept = useMutation({
    mutationFn: (id: string) => api(`${path}/${open!.id}/accepted/${id}`, { method: 'PUT' }),
    onSuccess: (_, id) => {
      setOpen((current) => (current ? { ...current, acceptedReplyId: id } : current));
      refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`${path}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setOpen(undefined);
      refresh();
    },
  });
  return (
    <section className="feature-section">
      <h3>Questions and answers</h3>
      <div className="form-grid">
        <Field label="Search questions">
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <label className="checkbox-field">
          <input
            type="checkbox"
            checked={unanswered}
            onChange={(e) => {
              setUnanswered(e.target.checked);
              setPage(0);
            }}
          />
          Unanswered questions only
        </label>
      </div>
      {questions.isPending ? (
        <Loading />
      ) : questions.error ? (
        <ErrorBox error={questions.error} />
      ) : (
        questions.data?.content.map((question) => (
          <button
            className="discussion-question"
            key={question.id}
            onClick={() => setOpen(question)}
          >
            <strong>{question.title}</strong>
            <span>
              {question.authorName} · {question.replyCount} replies{' '}
              {question.acceptedReplyId ? '· Resolved' : ''}
            </span>
          </button>
        ))
      )}
      <Pagination page={page} pages={questions.data?.totalPages || 0} onChange={setPage} />
      {open && (
        <article className="question-thread">
          <h4>{open.title}</h4>
          <p>{open.content}</p>
          {replies.isPending ? (
            <Loading />
          ) : replies.error ? (
            <ErrorBox error={replies.error} />
          ) : (
            replies.data?.map((r) => (
              <div className="note-item" key={r.id}>
                <b>
                  {r.authorName} {r.instructor ? '· Instructor' : ''}
                </b>
                <p>{r.content}</p>
                {open.acceptedReplyId === r.id ? (
                  <Badge value="ACCEPTED" />
                ) : (
                  (session?.user.id === open.authorId ||
                    session?.user.id === instructorId ||
                    session?.user.role === 'ADMIN') && (
                    <button className="text-button" onClick={() => accept.mutate(r.id)}>
                      Accept answer
                    </button>
                  )
                )}
              </div>
            ))
          )}
          <form
            key={`${open.id}-${reply.isSuccess}`}
            onSubmit={(e) => {
              e.preventDefault();
              reply.mutate(String(new FormData(e.currentTarget).get('content')));
            }}
          >
            <Field label="Your reply">
              <textarea name="content" required maxLength={10000} />
            </Field>
            <Submit busy={reply.isPending}>Send reply</Submit>
          </form>
          {(session?.user.id === open.authorId ||
            session?.user.id === instructorId ||
            session?.user.role === 'ADMIN') && (
            <button
              className="text-button danger"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm('Delete this question and its replies?')) remove.mutate(open.id);
              }}
            >
              Delete question
            </button>
          )}
        </article>
      )}
      {[ask.error, reply.error, accept.error, remove.error].filter(Boolean).map((error, i) => (
        <ErrorBox error={error} key={i} />
      ))}
      <details>
        <summary>Ask a question</summary>
        <form
          key={String(ask.isSuccess)}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            ask.mutate({
              lessonId: lessonId || null,
              title: f.get('title'),
              content: f.get('content'),
            });
          }}
        >
          <Field label="Question title">
            <input name="title" required maxLength={200} />
          </Field>
          <Field label="Question details">
            <textarea name="content" required maxLength={10000} rows={4} />
          </Field>
          <Submit busy={ask.isPending}>Submit question</Submit>
        </form>
      </details>
    </section>
  );
}

export function CourseStudents({ courseId }: { courseId: string }) {
  const { session } = useAuth();
  const [page, setPage] = useState(0);
  const client = useQueryClient();
  const students = useQuery({
    queryKey: ['course-students', courseId, page],
    queryFn: () =>
      api<Page<RosterRow>>(`/enrollments/courses/${courseId}/students?page=${page}&size=20`),
  });
  const revoke = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api(`/enrollments/admin/${id}/revoke`, { method: 'POST', body: body({ reason }) }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['course-students', courseId] }),
  });
  return (
    <section className="panel">
      <h2>Learners and progress</h2>
      {revoke.error && <ErrorBox error={revoke.error} />}{' '}
      {students.isPending ? (
        <Loading />
      ) : students.error ? (
        <ErrorBox error={students.error} />
      ) : (
        <>
          <p>{students.data?.totalElements || 0} enrollments</p>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Learner</th>
                  <th>Status</th>
                  <th>Progress</th>
                  <th>Last activity</th>
                  {session?.user.role === 'ADMIN' && <th>Action</th>}
                </tr>
              </thead>
              <tbody>
                {students.data?.content.map(({ enrollment: e, studentName, email }) => (
                  <tr key={e.id}>
                    <td>
                      {studentName}
                      <small className="muted"> {email}</small>
                    </td>
                    <td>
                      <Badge value={e.status} />
                    </td>
                    <td>
                      <progress value={e.progressPercentage || 0} max={100} />
                      <span>
                        {' '}
                        {Math.round(e.progressPercentage || 0)}% · {e.completedLessons}/
                        {e.totalLessons} lessons
                      </span>
                    </td>
                    <td>{e.lastAccessedAt ? date(e.lastAccessedAt) : 'Not started yet'}</td>
                    {session?.user.role === 'ADMIN' && (
                      <td>
                        <button
                          className="text-button danger"
                          disabled={revoke.isPending || !['ACTIVE', 'COMPLETED'].includes(e.status)}
                          onClick={() => {
                            const reason = window.prompt('Reason for revoking enrollment');
                            if (reason?.trim()) revoke.mutate({ id: e.id, reason });
                          }}
                        >
                          Revoke enrollment
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} pages={students.data?.totalPages || 0} onChange={setPage} />
        </>
      )}
    </section>
  );
}

function ProtectedDownload({ url }: { url: string }) {
  const [error, setError] = useState<unknown>();
  return (
    <>
      <button
        className="text-button"
        onClick={async () => {
          try {
            const blob = await fetchResource(url);
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = 'assignment-file';
            link.click();
            setTimeout(() => URL.revokeObjectURL(link.href), 10000);
          } catch (e) {
            setError(e);
          }
        }}
      >
        Download assignment file
      </button>
      {!!error && <ErrorBox error={error} />}
    </>
  );
}
