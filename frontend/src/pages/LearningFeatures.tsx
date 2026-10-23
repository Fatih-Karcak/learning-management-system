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
      setAnswers({});
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
          answers: actual!.questions.map((q) => ({ questionId: q.id, option: answers[q.id] })),
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
      <p>
        Geçme puanı: %{actual!.passingScore}.
        {quiz.data?.maxAttempts ? ` Deneme sınırı: ${quiz.data.maxAttempts}.` : ''}
        {quiz.data?.timeLimitSeconds ? ` Süre: ${quiz.data.timeLimitSeconds} saniye.` : ''}
      </p>
      {managed && !active ? (
        <button
          className="button primary"
          disabled={start.isPending}
          onClick={() => start.mutate()}
        >
          Testi başlat / devam et
        </button>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit.mutate();
          }}
        >
          {active && quiz.data?.timeLimitSeconds && (
            <p role="timer">Kalan süre: {remaining} saniye</p>
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
            <Submit busy={submit.isPending}>Testi gönder</Submit>
          </fieldset>
          {submit.error && <ErrorBox error={submit.error} />}{' '}
          {submit.data && (
            <div className={`notice ${submit.data.passed ? 'success' : 'warning'}`} role="status">
              Puanın: %{submit.data.score}.{' '}
              {submit.data.passed ? 'Testi geçtin; ders tamamlandı.' : 'Geçme puanına ulaşamadın.'}
              <ul>
                {submit.data.feedback?.map((f) => (
                  <li key={f.questionId}>
                    {actual!.questions.find((q) => q.id === f.questionId)?.prompt}:{' '}
                    {f.correct ? 'Doğru' : 'Tekrar çalış'}
                    {f.explanation ? ` — ${f.explanation}` : ''}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {(submit.isSuccess || expired) && (
            <>
              <p>{expired ? 'Test süresi doldu. Yeni deneme kalan haklarından düşer.' : ''}</p>
              <button
                type="button"
                className="button secondary"
                disabled={start.isPending}
                onClick={() => (managed ? start.mutate() : (submit.reset(), setAnswers({})))}
              >
                Yeniden dene
              </button>
            </>
          )}
        </form>
      )}
      {start.error && <ErrorBox error={start.error} />}{' '}
      {attempts.error && <ErrorBox error={attempts.error} />}{' '}
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
      <h3>Ödev teslimi</h3>
      {!!history.data?.length && (
        <details>
          <summary>Teslim geçmişi ({history.data.length})</summary>
          {history.data.map((h) => (
            <article className="notice" key={h.id}>
              <small>{date(h.created_at)}</small>
              <p className="pre-wrap">{h.content}</p>
              {h.feedback && <p>Eğitmen: {h.feedback}</p>}
            </article>
          ))}
        </details>
      )}
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
              attachmentUrl:
                attachment ?? (String(f.get('attachmentUrl') || '') || s?.attachmentUrl || null),
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
          {s?.attachmentUrl?.startsWith('/api/v1/media/resources/') && (
            <ProtectedDownload url={s.attachmentUrl} />
          )}
          <Field label="Ödev dosyası (PDF veya TXT, en fazla 25 MB)">
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
              Dosya yüklendi. Ödevi gönderdiğinde teslimine eklenecek.
            </p>
          )}
          {!!uploadError && <ErrorBox error={uploadError} />}{' '}
          {uploading && <p role="status">Dosya yükleniyor…</p>}
          <Field label="Çalışma bağlantısı (isteğe bağlı)">
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
            {s ? 'Ödevi güncelle' : 'Ödevi gönder'}
          </Submit>
        </form>
      )}
    </section>
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
            {s.attachmentUrl?.startsWith('/api/v1/media/resources/') ? (
              <ProtectedDownload url={s.attachmentUrl} />
            ) : (
              safeUrl(s.attachmentUrl) && (
                <a href={safeUrl(s.attachmentUrl)} target="_blank" rel="noreferrer">
                  Çalışmayı aç
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
            link.download = 'odev-dosyasi';
            link.click();
            setTimeout(() => URL.revokeObjectURL(link.href), 10000);
          } catch (e) {
            setError(e);
          }
        }}
      >
        Ödev dosyasını indir
      </button>
      {!!error && <ErrorBox error={error} />}
    </>
  );
}
