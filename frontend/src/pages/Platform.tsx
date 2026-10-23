import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, body, date, safeUrl, fetchResource } from '../api';
import type { Course, Material } from '../types';
import { useAuth } from '../auth';
import { Empty, ErrorBox, Field, Loading, PageTitle, Submit, Success } from '../ui';
export function CourseAnalytics({ courseId, course }: { courseId: string; course: Course }) {
  const q = useQuery({
    queryKey: ['course-analytics', courseId],
    queryFn: async () => ({
      learning: await api<{
        summary: Record<string, number>;
        lessons: {
          lesson_id: string;
          started: number;
          completed: number;
          average_watched_seconds: number;
        }[];
        weekly: { week: string; enrollments: number }[];
      }>(`/enrollments/courses/${courseId}/analytics`),
      assessments: await api<{
        quizResults: {
          lesson_id: string;
          attempts: number;
          average_score: number;
          passed: number;
        }[];
        assignments: {
          lesson_id: string;
          pending: number;
          accepted: number;
          changes_requested: number;
        }[];
        questions: { lesson_id: string; asked: number; unresolved: number }[];
      }>(`/courses/${courseId}/analytics`),
    }),
  });
  if (q.isPending) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} />;
  const d = q.data!,
    lessons = course.sections?.flatMap((s) => s.lessons) || [];
  return (
    <section className="panel">
      <h2>Öğrenme raporu</h2>
      <div className="stats-grid">
        {[
          ['Kayıt', d.learning.summary.enrolled],
          ['Tamamlayan', d.learning.summary.completed],
          ['14 gündür çalışmayan', d.learning.summary.inactive],
          ['Ortalama ilerleme', `%${Math.round(d.learning.summary.average_progress)}`],
        ].map(([label, value]) => (
          <article key={label} className="panel">
            <small>{label}</small>
            <h3>{value}</h3>
          </article>
        ))}
      </div>
      <div className="table-scroll">
        <table>
          <caption>Ders bazında katılım ve sonuçlar</caption>
          <thead>
            <tr>
              <th>Ders</th>
              <th>Başlayan</th>
              <th>Tamamlayan</th>
              <th>Test ortalaması</th>
              <th>Bekleyen ödev</th>
              <th>Çözülmemiş soru</th>
            </tr>
          </thead>
          <tbody>
            {lessons.map((l) => {
              const progress = d.learning.lessons.find((x) => x.lesson_id === l.id),
                quiz = d.assessments.quizResults.find((x) => x.lesson_id === l.id),
                assignment = d.assessments.assignments.find((x) => x.lesson_id === l.id),
                question = d.assessments.questions.find((x) => x.lesson_id === l.id);
              return (
                <tr key={l.id}>
                  <th>{l.title}</th>
                  <td>{progress?.started || 0}</td>
                  <td>{progress?.completed || 0}</td>
                  <td>{quiz ? `%${quiz.average_score}` : '—'}</td>
                  <td>{assignment?.pending || 0}</td>
                  <td>{question?.unresolved || 0}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="muted">
        Başlayan ve tamamlayan sayıları ders ilerlemesi kaydedilen aktif öğrencileri gösterir.
        Raporlar izleme süresi, test ve ödev kayıtlarına dayanır.
      </p>
    </section>
  );
}
export function Announcements({
  courseId,
  instructor = false,
}: {
  courseId: string;
  instructor?: boolean;
}) {
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['announcements', courseId],
    queryFn: () =>
      api<{ id: string; title: string; content: string; created_at: string }[]>(
        `/courses/${courseId}/announcements`,
      ),
  });
  const send = useMutation({
    mutationFn: (data: unknown) =>
      api(`/courses/${courseId}/announcements`, { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['announcements', courseId] });
    },
  });
  return (
    <section className="panel">
      <h2>Kurs duyuruları</h2>
      {instructor && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            send.mutate({ title: f.get('title'), content: f.get('content') });
          }}
        >
          <Field label="Duyuru başlığı">
            <input name="title" required maxLength={200} />
          </Field>
          <Field label="Duyuru metni">
            <textarea name="content" required maxLength={10000} rows={4} />
          </Field>
          <Submit busy={send.isPending}>Duyuruyu yayımla</Submit>
          {send.isSuccess && (
            <Success>Duyuru kaydedildi. Öğrencilere bildirim gönderilecek.</Success>
          )}
          {send.error && <ErrorBox error={send.error} />}
        </form>
      )}
      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} />
      ) : q.data?.length ? (
        q.data.map((a) => (
          <article className="notice" key={a.id}>
            <h3>{a.title}</h3>
            <small>{date(a.created_at)}</small>
            <p className="pre-wrap">{a.content}</p>
          </article>
        ))
      ) : (
        <p className="muted">Henüz duyuru yok.</p>
      )}
    </section>
  );
}
type Task = { id: string; title: string; due_date: string; enrollment_id?: string; done: boolean };
type StudyData = {
  daily: { day: string; minutes: number }[];
  weekMinutes: number;
  tasks: Task[];
  bookmarks: Bookmark[];
};
type Bookmark = {
  id: string;
  enrollment_id: string;
  lesson_id: string;
  title: string;
  position_seconds: number;
  course_title?: string;
};
export function StudyPlan() {
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['study-plan'],
    queryFn: () => api<StudyData>('/enrollments/learning/dashboard'),
  });
  const prefs = useQuery({
    queryKey: ['preferences'],
    queryFn: () => api<{ weeklyTargetMinutes: number }>('/users/me/preferences'),
  });
  const action = useMutation({
    mutationFn: ({
      path,
      data,
      method = 'POST',
    }: {
      path: string;
      data?: unknown;
      method?: string;
    }) => api(path, { method, body: data === undefined ? undefined : body(data) }),
    onMutate: (input) => {
      const previous = client.getQueryData<StudyData>(['study-plan']);
      if (input.method === 'PUT' && input.path.startsWith('/enrollments/learning/tasks/')) {
        void client.cancelQueries({ queryKey: ['study-plan'] });
        const id = input.path.split('/').at(-1);
        client.setQueryData<StudyData>(['study-plan'], (old) =>
          old
            ? {
                ...old,
                tasks: old.tasks.map((t) =>
                  t.id === id ? { ...t, done: (input.data as { done: boolean }).done } : t,
                ),
              }
            : old,
        );
      }
      return { previous };
    },
    onError: (_error, _input, context) => {
      if (context?.previous) client.setQueryData(['study-plan'], context.previous);
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['study-plan'] });
    },
  });
  return (
    <>
      <PageTitle
        eyebrow="ÖĞRENME ALIŞKANLIĞIN"
        title="Çalışma planım"
        description="Hedeflerini belirle, çalışma zamanını takip et ve derslerine dön."
      />
      {q.error && <ErrorBox error={q.error} />}{' '}
      {q.isPending ? (
        <Loading />
      ) : (
        <>
          <section className="panel">
            <h2>Bu haftaki video çalışman</h2>
            <p>
              {Math.round(q.data?.weekMinutes || 0)} / {prefs.data?.weeklyTargetMinutes ?? 60}{' '}
              dakika
            </p>
            <progress
              max={prefs.data?.weeklyTargetMinutes ?? 60}
              value={q.data?.weekMinutes || 0}
            />
            <p className="muted">
              Süre, videolardaki doğrulanmış benzersiz izleme kapsamından hesaplanır.
            </p>
            <details>
              <summary>Son 28 gün</summary>
              <table>
                <thead>
                  <tr>
                    <th>Tarih</th>
                    <th>Dakika</th>
                  </tr>
                </thead>
                <tbody>
                  {q.data?.daily.map((d) => (
                    <tr key={d.day}>
                      <td>{date(d.day)}</td>
                      <td>{Math.round(d.minutes)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
            <Link to="/security">Haftalık hedefini değiştir</Link>
          </section>
          <section className="panel">
            <h2>Çalışma takvimim</h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                action.mutate({
                  path: '/enrollments/learning/tasks',
                  data: { title: f.get('title'), dueDate: f.get('dueDate') },
                });
              }}
            >
              <Field label="Çalışma hedefi">
                <input
                  required
                  name="title"
                  maxLength={200}
                  placeholder="Örneğin: Java dersini tekrar et"
                />
              </Field>
              <Field label="Çalışma tarihi">
                <input
                  required
                  type="date"
                  name="dueDate"
                  defaultValue={new Date().toISOString().slice(0, 10)}
                />
              </Field>
              <Submit busy={action.isPending}>Plana ekle</Submit>
            </form>
            {q.data?.tasks.map((t) => (
              <article className="lesson-row" key={t.id}>
                <TaskCheckbox
                  task={t}
                  pending={action.isPending}
                  toggle={(done) =>
                    action.mutateAsync({
                      path: `/enrollments/learning/tasks/${t.id}`,
                      method: 'PUT',
                      data: { done },
                    })
                  }
                />
                <small>{date(t.due_date)}</small>
                <button
                  className="text-button danger"
                  disabled={action.isPending}
                  onClick={() =>
                    action.mutate({ path: `/enrollments/learning/tasks/${t.id}`, method: 'DELETE' })
                  }
                >
                  Sil
                </button>
              </article>
            ))}
          </section>
          <section className="panel">
            <h2>Yer imlerim</h2>
            {q.data?.bookmarks.length ? (
              q.data.bookmarks.map((b) => (
                <p key={b.id}>
                  <Link
                    to={`/learn/${b.enrollment_id}?lesson=${b.lesson_id}&time=${b.position_seconds}`}
                  >
                    {b.title} · {b.course_title} · {b.position_seconds} sn
                  </Link>
                </p>
              ))
            ) : (
              <p>Ders ekranından önemli yerlere yer imi ekleyebilirsin.</p>
            )}
          </section>
        </>
      )}
      {action.error && <ErrorBox error={action.error} />}
    </>
  );
}
function TaskCheckbox({
  task,
  pending,
  toggle,
}: {
  task: Task;
  pending: boolean;
  toggle: (done: boolean) => Promise<unknown>;
}) {
  const [done, setDone] = useState(task.done);
  useEffect(() => setDone(task.done), [task.done]);
  return (
    <label>
      <input
        type="checkbox"
        checked={done}
        disabled={pending}
        onChange={async (e) => {
          const next = e.currentTarget.checked;
          setDone(next);
          try {
            await toggle(next);
          } catch {
            setDone(!next);
          }
        }}
      />{' '}
      {task.title}
    </label>
  );
}

export function LessonBookmarks({
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
  onSeek: (n: number) => void;
}) {
  const client = useQueryClient();
  const path = `/enrollments/${enrollmentId}/bookmarks`;
  const q = useQuery({
    queryKey: ['bookmarks', enrollmentId],
    queryFn: () => api<Bookmark[]>(path),
  });
  const add = useMutation({
    mutationFn: (title: string) =>
      api(path, {
        method: 'POST',
        body: body({ lessonId, sectionId, positionSeconds: position, title }),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['bookmarks', enrollmentId] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`${path}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['bookmarks', enrollmentId] });
    },
  });
  return (
    <section className="feature-section">
      <h3>Yer imleri</h3>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          add.mutate(String(f.get('title')));
        }}
      >
        <Field label={`Bu konuma yer imi ekle (${position} sn)`}>
          <input name="title" required maxLength={200} />
        </Field>
        <Submit busy={add.isPending}>Yer imi ekle</Submit>
      </form>
      {q.data
        ?.filter((b) => b.lesson_id === lessonId)
        .map((b) => (
          <p key={b.id}>
            <button className="text-button" onClick={() => onSeek(b.position_seconds)}>
              {b.title} · {b.position_seconds} sn
            </button>
            <button className="text-button danger" onClick={() => remove.mutate(b.id)}>
              Sil
            </button>
          </p>
        ))}
      {(q.error || add.error || remove.error) && (
        <ErrorBox error={q.error || add.error || remove.error} />
      )}
    </section>
  );
}
export function LessonMaterials({
  courseId,
  lessonId,
  onMaterials,
}: {
  courseId: string;
  lessonId: string;
  onMaterials?: (m: Material[]) => void;
}) {
  const [error, setError] = useState<unknown>();
  const q = useQuery({
    queryKey: ['materials', courseId, lessonId],
    queryFn: async () => {
      const m = await api<Material[]>(`/courses/${courseId}/lessons/${lessonId}/materials`);
      onMaterials?.(m);
      return m;
    },
  });
  async function download(m: Material) {
    try {
      if (m.url.startsWith('/api/v1/media/resources/')) {
        const blob = await fetchResource(m.url);
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = m.title;
        link.click();
        setTimeout(() => URL.revokeObjectURL(link.href), 10000);
      } else {
        const url = safeUrl(m.url);
        if (url) window.open(url, '_blank', 'noopener,noreferrer');
      }
    } catch (e) {
      setError(e);
    }
  }
  return (
    <section className="feature-section">
      <h3>Ders kaynakları</h3>
      {q.error && <ErrorBox error={q.error} />}{' '}
      {q.data
        ?.filter((m) => m.type === 'RESOURCE')
        .map((m, i) => (
          <p key={i}>
            <button className="text-button" onClick={() => void download(m)}>
              {m.title} ↗
            </button>
          </p>
        ))}
      {q.data && !q.data.some((m) => m.type === 'RESOURCE') && (
        <p className="muted">Bu ders için ek kaynak bulunmuyor.</p>
      )}
      {!!error && <ErrorBox error={error} />}
    </section>
  );
}
export function Wishlist() {
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['wishlist'],
    queryFn: () =>
      api<{ id: string; title: string; instructor_name: string }[]>('/courses/wishlist'),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/courses/${id}/wishlist`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });
  return (
    <>
      <PageTitle title="İstek listem" />
      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} />
      ) : q.data?.length ? (
        q.data.map((c) => (
          <article className="panel" key={c.id}>
            <h2>
              <Link to={`/courses/${c.id}`}>{c.title}</Link>
            </h2>
            <p>{c.instructor_name}</p>
            <button className="text-button danger" onClick={() => remove.mutate(c.id)}>
              Listeden kaldır
            </button>
          </article>
        ))
      ) : (
        <Empty
          title="İstek listen boş"
          text="Kurs sayfasından daha sonra öğrenmek istediğin kursları ekleyebilirsin."
        />
      )}
      {remove.error && <ErrorBox error={remove.error} />}
    </>
  );
}
export function WishButton({ courseId }: { courseId: string }) {
  const { session } = useAuth();
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['wishlist'],
    queryFn: () => api<{ id: string }[]>('/courses/wishlist'),
    enabled: !!session,
  });
  const found = q.data?.some((c) => c.id === courseId);
  const m = useMutation({
    mutationFn: () => api(`/courses/${courseId}/wishlist`, { method: found ? 'DELETE' : 'PUT' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });
  return session ? (
    <>
      <button
        className="button secondary"
        disabled={m.isPending || q.isPending}
        onClick={() => m.mutate()}
      >
        {found ? 'İstek listesinden kaldır' : 'İstek listeme ekle'}
      </button>
      {m.error && <ErrorBox error={m.error} />}
    </>
  ) : null;
}
export function Inbox() {
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['inbox'],
    queryFn: () =>
      api<
        {
          id: string;
          subject: string;
          message: string;
          path: string;
          read_at?: string;
          created_at: string;
        }[]
      >('/notifications/inbox'),
    refetchInterval: 30000,
  });
  const read = useMutation({
    mutationFn: (id: string) => api(`/notifications/inbox/${id}/read`, { method: 'PUT' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['inbox'] });
    },
  });
  return (
    <section className="panel">
      <h2>Gelen kutusu</h2>
      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} />
      ) : q.data?.length ? (
        q.data.map((n) => (
          <article className={`notice ${n.read_at ? '' : 'warning'}`} key={n.id}>
            <h3>{n.subject}</h3>
            <p className="pre-wrap">{n.message}</p>
            <small>{date(n.created_at)}</small>
            <div className="form-actions">
              {n.path?.startsWith('/') && !n.path.startsWith('//') && (
                <Link
                  className="button secondary small"
                  to={n.path}
                  onClick={() => read.mutate(n.id)}
                >
                  Görüntüle
                </Link>
              )}
              {!n.read_at && (
                <button className="text-button" onClick={() => read.mutate(n.id)}>
                  Okundu işaretle
                </button>
              )}
            </div>
          </article>
        ))
      ) : (
        <p>Henüz uygulama içi bildirim yok.</p>
      )}
      {read.error && <ErrorBox error={read.error} />}
    </section>
  );
}

export function RecommendedCourses({ courseId }: { courseId: string }) {
  const q = useQuery({
    queryKey: ['recommended', courseId],
    queryFn: () =>
      api<{ id: string; title: string; instructor_name: string }[]>(
        `/courses/${courseId}/recommendations`,
      ),
  });
  return q.data?.length ? (
    <section className="panel">
      <h2>İlgini çekebilecek kurslar</h2>
      <div className="form-grid">
        {q.data.map((c) => (
          <article key={c.id}>
            <h3>
              <Link to={`/courses/${c.id}`}>{c.title}</Link>
            </h3>
            <p>{c.instructor_name}</p>
          </article>
        ))}
      </div>
    </section>
  ) : null;
}

export function VideoOperations() {
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ['video-operations'],
    queryFn: () =>
      api<{ id: string; original_file_name: string; status: string; failure_reason?: string }[]>(
        '/media/operations',
      ),
    refetchInterval: 15000,
  });
  const retry = useMutation({
    mutationFn: (id: string) => api(`/media/operations/${id}/retry`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['video-operations'] });
      void client.invalidateQueries({ queryKey: ['media'] });
    },
  });
  return (
    <section className="panel">
      <h2>Video işleme durumu</h2>
      {q.error && <ErrorBox error={q.error} />}{' '}
      {q.data?.length ? (
        q.data.map((v) => (
          <article className="notice" key={v.id}>
            <h3>{v.original_file_name}</h3>
            <p>{v.status === 'PROCESSING' ? 'İşleniyor' : 'İşlenemedi'}</p>
            {v.failure_reason && <p>{v.failure_reason}</p>}
            {v.status === 'FAILED' && (
              <button
                className="button secondary"
                disabled={retry.isPending}
                onClick={() => retry.mutate(v.id)}
              >
                Yeniden işle
              </button>
            )}
          </article>
        ))
      ) : (
        <p>Bekleyen veya başarısız video işlemi yok.</p>
      )}
      {retry.error && <ErrorBox error={retry.error} />}
    </section>
  );
}
