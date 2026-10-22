import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import { api, ApiError, body, date, safeUrl } from '../api';
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
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const quiz = useQuery({ queryKey: ['quiz', lessonId], queryFn: () => api<Quiz>(path) });
  const attempts = useQuery({
    queryKey: ['quiz-attempts', lessonId],
    queryFn: () => api<QuizAttempt[]>(`${path}/attempts`),
  });
  const submit = useMutation({
    mutationFn: () =>
      api<QuizAttempt>(`${path}/attempts`, {
        method: 'POST',
        body: body({
          enrollmentId,
          answers: quiz.data!.questions.map((q) => ({ questionId: q.id, option: answers[q.id] })),
        }),
      }),
    onSuccess: (result) => {
      void client.invalidateQueries({ queryKey: ['quiz-attempts', lessonId] });
      if (result.passed) onCompleted();
    },
  });
  if (quiz.isPending) return <Loading />;
  if (quiz.error) return <ErrorBox error={quiz.error} retry={() => void quiz.refetch()} />;
  return (
    <section className="feature-section">
      <h3>Ders testi</h3>
      <p>Geçme puanı: %{quiz.data!.passingScore}.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit.mutate();
        }}
      >
        {quiz.data!.questions.map((q, i) => (
          <fieldset className="quiz-question" key={q.id}>
            <legend>
              {i + 1}. {q.prompt}
            </legend>
            {q.options.map((option, index) => (
              <label className="checkbox-field" key={index}>
                <input
                  type="radio"
                  name={q.id}
                  required
                  checked={answers[q.id] === index}
                  onChange={() => setAnswers({ ...answers, [q.id]: index })}
                />
                {option}
              </label>
            ))}
          </fieldset>
        ))}
        {submit.error && <ErrorBox error={submit.error} />}
        {submit.data && (
          <div className={`notice ${submit.data.passed ? 'success' : 'warning'}`} role="status">
            Puanın: %{submit.data.score}.{' '}
            {submit.data.passed
              ? 'Testi geçtin; ders tamamlandı.'
              : 'Geçme puanına ulaşamadın. Yeniden deneyebilirsin.'}
          </div>
        )}
        <Submit busy={submit.isPending}>Testi gönder</Submit>
      </form>
      {attempts.error && <ErrorBox error={attempts.error} />}
      {!!attempts.data?.length && (
        <details>
          <summary>Önceki denemeler ({attempts.data.length})</summary>
          <ul>
            {attempts.data.map((a) => (
              <li key={a.id}>
                %{a.score} · {a.passed ? 'Geçti' : 'Tekrar dene'}
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
  const client = useQueryClient();
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
      onCompleted();
    },
  });
  if (submission.isPending) return <Loading />;
  if (submission.error)
    return <ErrorBox error={submission.error} retry={() => void submission.refetch()} />;
  const s = submission.data;
  return (
    <section className="feature-section">
      <h3>Ödev teslimi</h3>
      {s && (
        <>
          <Badge value={s.status} />
          {s.score != null && <p>Puan: {s.score}/100</p>}
          {s.feedback && (
            <div className="notice">
              <b>Eğitmen geri bildirimi</b>
              <p>{s.feedback}</p>
            </div>
          )}
        </>
      )}
      {s?.status === 'ACCEPTED' ? (
        <Success>Ödevin kabul edildi. Ders tamamlandı.</Success>
      ) : (
        <form
          key={s?.submittedAt || 'new'}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            send.mutate({
              enrollmentId,
              content: f.get('content'),
              attachmentUrl: String(f.get('attachmentUrl') || '') || null,
            });
          }}
        >
          <Field label="Ödev yanıtın">
            <textarea
              name="content"
              rows={6}
              required
              maxLength={20000}
              defaultValue={s?.content}
            />
          </Field>
          <Field label="Çalışma bağlantısı (isteğe bağlı)">
            <input
              name="attachmentUrl"
              type="url"
              maxLength={2000}
              placeholder="https://…"
              defaultValue={s?.attachmentUrl}
            />
          </Field>
          {send.error && <ErrorBox error={send.error} />}
          <Submit busy={send.isPending}>{s ? 'Ödevi güncelle' : 'Ödevi gönder'}</Submit>
        </form>
      )}
    </section>
  );
}

const newQuestion = () => ({
  id: crypto.randomUUID(),
  prompt: '',
  options: ['', ''],
  correctOption: 0,
  explanation: '',
});
export function QuizEditor({ courseId, lessonId }: { courseId: string; lessonId: string }) {
  const client = useQueryClient();
  const path = `/courses/${courseId}/lessons/${lessonId}/quiz/manage`;
  const quiz = useQuery({
    queryKey: ['quiz-editor', lessonId],
    queryFn: () => api<Quiz>(path),
    retry: false,
  });
  if (quiz.isPending) return <Loading />;
  if (quiz.error && !(quiz.error instanceof ApiError && quiz.error.status === 404))
    return <ErrorBox error={quiz.error} />;
  return (
    <QuizEditorForm
      key={lessonId}
      path={path}
      initial={quiz.data}
      onSaved={(value) => client.setQueryData(['quiz-editor', lessonId], value)}
    />
  );
}
function QuizEditorForm({
  path,
  initial,
  onSaved,
}: {
  path: string;
  initial?: Quiz;
  onSaved: (value: Quiz) => void;
}) {
  const [quiz, setQuiz] = useState<Quiz>(
    initial || { passingScore: 70, questions: [newQuestion()] },
  );
  const save = useMutation({
    mutationFn: () => api<Quiz>(path, { method: 'PUT', body: body(quiz) }),
    onSuccess: (value) => {
      setQuiz(value);
      onSaved(value);
    },
  });
  function change(index: number, patch: Partial<Quiz['questions'][number]>) {
    setQuiz({
      ...quiz,
      questions: quiz.questions.map((q, i) => (i === index ? { ...q, ...patch } : q)),
    });
  }
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <Field label="Geçme puanı (%)">
        <input
          type="number"
          min={1}
          max={100}
          required
          value={quiz.passingScore}
          onChange={(e) => setQuiz({ ...quiz, passingScore: Number(e.target.value) })}
        />
      </Field>
      {quiz.questions.map((q, index) => (
        <fieldset className="quiz-question" key={q.id}>
          <legend>Soru {index + 1}</legend>
          <Field label={`Soru ${index + 1} metni`}>
            <textarea
              required
              maxLength={2000}
              value={q.prompt}
              onChange={(e) => change(index, { prompt: e.target.value })}
            />
          </Field>
          {q.options.map((option, i) => (
            <Field key={i} label={`Soru ${index + 1}, seçenek ${i + 1}`}>
              <input
                required
                maxLength={1000}
                value={option}
                onChange={(e) =>
                  change(index, {
                    options: q.options.map((value, j) => (j === i ? e.target.value : value)),
                  })
                }
              />
            </Field>
          ))}
          <div className="form-actions">
            <button
              type="button"
              className="text-button"
              disabled={q.options.length >= 8}
              onClick={() => change(index, { options: [...q.options, ''] })}
            >
              Seçenek ekle
            </button>
            <button
              type="button"
              className="text-button"
              disabled={q.options.length <= 2}
              onClick={() =>
                change(index, {
                  options: q.options.slice(0, -1),
                  correctOption: Math.min(q.correctOption || 0, q.options.length - 2),
                })
              }
            >
              Son seçeneği kaldır
            </button>
          </div>
          <Field label={`Soru ${index + 1}, doğru seçenek`}>
            <select
              value={q.correctOption || 0}
              onChange={(e) => change(index, { correctOption: Number(e.target.value) })}
            >
              {q.options.map((_, i) => (
                <option value={i} key={i}>
                  {i + 1}. seçenek
                </option>
              ))}
            </select>
          </Field>
          <button
            type="button"
            className="text-button danger"
            disabled={quiz.questions.length === 1}
            onClick={() =>
              setQuiz({ ...quiz, questions: quiz.questions.filter((_, i) => i !== index) })
            }
          >
            Soruyu kaldır
          </button>
        </fieldset>
      ))}
      <button
        type="button"
        className="button secondary"
        disabled={quiz.questions.length >= 100}
        onClick={() => setQuiz({ ...quiz, questions: [...quiz.questions, newQuestion()] })}
      >
        Soru ekle
      </button>
      {save.error && <ErrorBox error={save.error} />}{' '}
      {save.isSuccess && (
        <Success>Test kaydedildi. Yanıt anahtarı öğrencilere gönderilmez.</Success>
      )}
      <Submit busy={save.isPending}>Testi kaydet</Submit>
    </form>
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
            {safeUrl(s.attachmentUrl) && (
              <a href={safeUrl(s.attachmentUrl)} target="_blank" rel="noreferrer">
                Çalışmayı aç
              </a>
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
                });
              }}
            >
              <Field label={`${s.studentName}, değerlendirme`}>
                <select
                  name="status"
                  defaultValue={s.status === 'CHANGES_REQUESTED' ? 'CHANGES_REQUESTED' : 'ACCEPTED'}
                >
                  <option value="ACCEPTED">Kabul et ve dersi tamamla</option>
                  <option value="CHANGES_REQUESTED" disabled={s.status === 'ACCEPTED'}>
                    Düzeltme iste
                  </option>
                </select>
              </Field>
              <Field label={`${s.studentName}, puan`}>
                <input name="score" type="number" min={0} max={100} defaultValue={s.score} />
              </Field>
              <Field label={`${s.studentName}, geri bildirim`}>
                <textarea name="feedback" maxLength={10000} defaultValue={s.feedback} />
              </Field>
              <Submit busy={grade.isPending}>Değerlendirmeyi kaydet</Submit>
            </form>
          </article>
        ))
      ) : (
        <Empty title="Henüz ödev teslim edilmedi" text="Öğrencilerin çalışmaları burada görünür." />
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
      <h3>Kişisel ders notlarım</h3>
      <p className="muted">
        Notlarını yalnızca sen görebilirsin. Yeni not konumu: {Math.floor(position / 60)}:
        {String(Math.floor(position) % 60).padStart(2, '0')}
      </p>
      <form
        key={editing?.id || String(save.isSuccess)}
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate(String(new FormData(e.currentTarget).get('content')));
        }}
      >
        <Field label="Ders notun">
          <textarea
            name="content"
            required
            maxLength={10000}
            rows={3}
            defaultValue={editing?.content}
          />
        </Field>
        <Submit busy={save.isPending}>{editing ? 'Notu güncelle' : 'Not ekle'}</Submit>
        {editing && (
          <button type="button" className="text-button" onClick={() => setEditing(undefined)}>
            Vazgeç
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
                Notu düzenle
              </button>
              <button
                className="text-button danger"
                disabled={remove.isPending}
                onClick={() => remove.mutate(note.id)}
              >
                Notu sil
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
      <h3>Soru-cevap</h3>
      <div className="form-grid">
        <Field label="Sorularda ara">
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
          Yalnızca yanıtsız sorular
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
              {question.authorName} · {question.replyCount} yanıt{' '}
              {question.acceptedReplyId ? '· Çözüldü' : ''}
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
                  {r.authorName} {r.instructor ? '· Eğitmen' : ''}
                </b>
                <p>{r.content}</p>
                {open.acceptedReplyId === r.id ? (
                  <Badge value="ACCEPTED" />
                ) : (
                  (session?.user.id === open.authorId ||
                    session?.user.id === instructorId ||
                    session?.user.role === 'ADMIN') && (
                    <button className="text-button" onClick={() => accept.mutate(r.id)}>
                      Yanıtı kabul et
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
            <Field label="Yanıtın">
              <textarea name="content" required maxLength={10000} />
            </Field>
            <Submit busy={reply.isPending}>Yanıt gönder</Submit>
          </form>
          {(session?.user.id === open.authorId ||
            session?.user.id === instructorId ||
            session?.user.role === 'ADMIN') && (
            <button
              className="text-button danger"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm('Soru ve yanıtları silinsin mi?')) remove.mutate(open.id);
              }}
            >
              Soruyu sil
            </button>
          )}
        </article>
      )}
      {[ask.error, reply.error, accept.error, remove.error].filter(Boolean).map((error, i) => (
        <ErrorBox error={error} key={i} />
      ))}
      <details>
        <summary>Yeni soru sor</summary>
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
          <Field label="Soru başlığı">
            <input name="title" required maxLength={200} />
          </Field>
          <Field label="Sorunun ayrıntısı">
            <textarea name="content" required maxLength={10000} rows={4} />
          </Field>
          <Submit busy={ask.isPending}>Soruyu gönder</Submit>
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
      <h2>Öğrenciler ve ilerleme</h2>
      {revoke.error && <ErrorBox error={revoke.error} />}{' '}
      {students.isPending ? (
        <Loading />
      ) : students.error ? (
        <ErrorBox error={students.error} />
      ) : (
        <>
          <p>{students.data?.totalElements || 0} kayıt</p>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th>Durum</th>
                  <th>İlerleme</th>
                  <th>Son erişim</th>
                  {session?.user.role === 'ADMIN' && <th>İşlem</th>}
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
                        %{Math.round(e.progressPercentage || 0)} · {e.completedLessons}/
                        {e.totalLessons} ders
                      </span>
                    </td>
                    <td>{e.lastAccessedAt ? date(e.lastAccessedAt) : 'Henüz başlamadı'}</td>
                    {session?.user.role === 'ADMIN' && (
                      <td>
                        <button
                          className="text-button danger"
                          disabled={revoke.isPending || !['ACTIVE', 'COMPLETED'].includes(e.status)}
                          onClick={() => {
                            const reason = window.prompt('Kayıt iptal gerekçesi');
                            if (reason?.trim()) revoke.mutate({ id: e.id, reason });
                          }}
                        >
                          Kaydı iptal et
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
