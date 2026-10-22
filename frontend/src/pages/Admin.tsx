import { useState } from 'react';
import { NavLink, Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, body, date } from '../api';
import type { Category, Course, Page, User } from '../types';
import {
  Badge,
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
import { CourseStudents } from './LearningFeatures';

export function AdminNavigation() {
  return (
    <nav className="feature-tabs" aria-label="Yönetim menüsü">
      {[
        ['/admin', 'Genel durum'],
        ['/admin/users', 'Kullanıcılar'],
        ['/admin/courses', 'Kurs onayları'],
        ['/admin/categories', 'Kategoriler'],
        ['/admin/enrollments', 'Kayıtlar'],
        ['/admin/audit', 'İşlem kayıtları'],
      ].map(([to, label]) => (
        <NavLink end={to === '/admin'} key={to} to={to} className="button secondary small">
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
export function AdminDashboard() {
  const data = useQuery({
    queryKey: ['admin-summary'],
    queryFn: async () => {
      const [users, courses, enrollments] = await Promise.all([
        api<Record<string, number>>('/users/admin/summary'),
        api<Record<string, number>>('/courses/admin/summary'),
        api<Record<string, number>>('/enrollments/admin/summary'),
      ]);
      return { users, courses, enrollments };
    },
  });
  return (
    <>
      <PageTitle
        eyebrow="PLATFORM YÖNETİMİ"
        title="Yönetici paneli"
        description="Kullanıcıları, kurs kalitesini ve öğrenme sonuçlarını yönet."
      />
      <AdminNavigation />
      {data.isPending ? (
        <Loading />
      ) : data.error ? (
        <ErrorBox error={data.error} retry={() => void data.refetch()} />
      ) : (
        <>
          <div className="stats-grid">
            {[
              ['Kullanıcılar', data.data!.users.total],
              ['Eğitmenler', data.data!.users.instructors],
              ['Onay bekleyen kurs', data.data!.courses.pending],
              ['Yayındaki kurs', data.data!.courses.published],
              ['Aktif kayıt', data.data!.enrollments.active],
              ['Tamamlanan kayıt', data.data!.enrollments.completed],
            ].map(([label, value]) => (
              <article className="panel" key={label}>
                <span className="muted">{label}</span>
                <h2>{value}</h2>
              </article>
            ))}
          </div>
          <section className="panel">
            <h2>İnceleme akışı</h2>
            <p>
              Eğitmen kursunu incelemeye gönderir. Yönetici onayladığında katalogda yayınlanır;
              düzeltme istenirse gerekçe eğitmene görünür.
            </p>
            <Link className="button primary" to="/admin/courses?status=PENDING_REVIEW">
              Kursları incele
            </Link>
          </section>
        </>
      )}
    </>
  );
}
export function AdminCourses() {
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('PENDING_REVIEW');
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<{ course: Course; action: string }>();
  const courses = useQuery({
    queryKey: ['admin-courses', status, query, page],
    queryFn: () =>
      api<Page<Course>>(
        `/courses/admin?query=${encodeURIComponent(query)}&page=${page}&size=12${status ? `&status=${status}` : ''}`,
      ),
  });
  const decision = useMutation({
    mutationFn: (reason: string) =>
      api(`/courses/admin/${selected!.course.id}/decision`, {
        method: 'POST',
        body: body({ action: selected!.action, reason }),
      }),
    onSuccess: () => {
      setSelected(undefined);
      void client.invalidateQueries({ queryKey: ['admin-courses'] });
      void client.invalidateQueries({ queryKey: ['admin-summary'] });
      void client.invalidateQueries({ queryKey: ['courses'] });
    },
  });
  return (
    <>
      <PageTitle eyebrow="YÖNETİCİ ALANI" title="Kurs incelemeleri" />
      <AdminNavigation />
      <div className="form-grid">
        <Field label="Kurs durumuna göre filtrele">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tüm durumlar</option>
            {['PENDING_REVIEW', 'PUBLISHED', 'DRAFT', 'ARCHIVED'].map((s) => (
              <option key={s} value={s}>
                {
                  {
                    PENDING_REVIEW: 'Onay bekliyor',
                    PUBLISHED: 'Yayında',
                    DRAFT: 'Taslak',
                    ARCHIVED: 'Arşivlendi',
                  }[s]
                }
              </option>
            ))}
          </select>
        </Field>
        <Field label="Kurslarda ara">
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
        </Field>
      </div>
      {courses.isPending ? (
        <Loading />
      ) : courses.error ? (
        <ErrorBox error={courses.error} />
      ) : courses.data?.content.length ? (
        <>
          {courses.data.content.map((c) => (
            <article className="panel" key={c.id}>
              <Badge value={c.status} />
              <h2>
                <Link to={`/courses/${c.id}`}>{c.title}</Link>
              </h2>
              <p>
                {c.instructorName} · {c.categoryName}
              </p>
              <p>{c.shortDescription}</p>
              {c.reviewFeedback && <p className="notice">{c.reviewFeedback}</p>}
              <div className="form-actions">
                <Link className="button secondary" to={`/courses/${c.id}`}>
                  İçeriği incele
                </Link>
                {c.status === 'PENDING_REVIEW' && (
                  <>
                    <button
                      className="button primary"
                      onClick={() => {
                        decision.reset();
                        setSelected({ course: c, action: 'APPROVE' });
                      }}
                    >
                      Onayla
                    </button>
                    <button
                      className="button secondary"
                      onClick={() => {
                        decision.reset();
                        setSelected({ course: c, action: 'REJECT' });
                      }}
                    >
                      Düzeltme iste
                    </button>
                  </>
                )}
                {c.status !== 'ARCHIVED' && (
                  <button
                    className="text-button danger"
                    onClick={() => {
                      decision.reset();
                      setSelected({ course: c, action: 'ARCHIVE' });
                    }}
                  >
                    Arşivle
                  </button>
                )}
              </div>
            </article>
          ))}
          <Pagination page={page} pages={courses.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Bu durumda kurs bulunmuyor"
          text="Filtreyi değiştirerek diğer kursları inceleyebilirsin."
        />
      )}
      {selected && (
        <Modal
          title={
            selected.action === 'APPROVE'
              ? 'Kursu onayla'
              : selected.action === 'REJECT'
                ? 'Düzeltme iste'
                : 'Kursu arşivle'
          }
          onClose={() => setSelected(undefined)}
        >
          <p>{selected.course.title}</p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              decision.mutate(String(new FormData(e.currentTarget).get('reason') || ''));
            }}
          >
            <Field label="Karar gerekçesi">
              <textarea name="reason" required={selected.action !== 'APPROVE'} maxLength={2000} />
            </Field>
            {decision.error && <ErrorBox error={decision.error} />}
            <Submit busy={decision.isPending}>Kararı kaydet</Submit>
          </form>
        </Modal>
      )}
    </>
  );
}
export function AdminCategories() {
  const client = useQueryClient();
  const [editing, setEditing] = useState<Category>();
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
  });
  const save = useMutation({
    mutationFn: (data: unknown) =>
      api(`/courses/admin/categories${editing ? `/${editing.id}` : ''}`, {
        method: editing ? 'PUT' : 'POST',
        body: body(data),
      }),
    onSuccess: () => {
      setEditing(undefined);
      void client.invalidateQueries({ queryKey: ['categories'] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => api(`/courses/admin/categories/${id}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['categories'] }),
  });
  return (
    <>
      <PageTitle eyebrow="YÖNETİCİ ALANI" title="Kategori yönetimi" />
      <AdminNavigation />
      <section className="panel">
        <h2>{editing ? 'Kategoriyi düzenle' : 'Kategori ekle'}</h2>
        <form
          key={editing?.id || String(save.isSuccess)}
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(Object.fromEntries(new FormData(e.currentTarget)));
          }}
        >
          <Field label="Kategori adı">
            <input name="name" required maxLength={100} defaultValue={editing?.name} />
          </Field>
          <Field label="Kategori açıklaması">
            <textarea name="description" maxLength={5000} defaultValue={editing?.description} />
          </Field>
          {save.error && <ErrorBox error={save.error} />}
          <Submit busy={save.isPending}>Kategoriyi kaydet</Submit>
          {editing && (
            <button type="button" className="text-button" onClick={() => setEditing(undefined)}>
              Vazgeç
            </button>
          )}
        </form>
      </section>
      {remove.error && <ErrorBox error={remove.error} />}{' '}
      {categories.isPending ? (
        <Loading />
      ) : categories.error ? (
        <ErrorBox error={categories.error} />
      ) : (
        categories.data?.map((c) => (
          <article className="panel" key={c.id}>
            <h3>{c.name}</h3>
            <p>{c.description}</p>
            <div className="form-actions">
              <button
                className="text-button"
                onClick={() => {
                  setEditing(c);
                  save.reset();
                }}
              >
                Kategoriyi düzenle
              </button>
              <button
                className="text-button danger"
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm('Kategori silinsin mi? Kurs içeren kategoriler silinemez.'))
                    remove.mutate(c.id);
                }}
              >
                Kategoriyi sil
              </button>
            </div>
          </article>
        ))
      )}
    </>
  );
}
export function AdminEnrollments() {
  const [courseId, setCourseId] = useState('');
  const [search, setSearch] = useState('');
  const client = useQueryClient();
  const courses = useQuery({
    queryKey: ['admin-course-options'],
    queryFn: () => api<Page<Course>>('/courses/admin?size=100'),
  });
  const users = useQuery({
    queryKey: ['admin-student-options', search],
    queryFn: () => api<Page<User>>(`/users/search?query=${encodeURIComponent(search)}&size=20`),
  });
  const grant = useMutation({
    mutationFn: (data: unknown) =>
      api('/enrollments/admin/grant', { method: 'POST', body: body(data) }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['course-students', courseId] }),
  });
  return (
    <>
      <PageTitle
        eyebrow="YÖNETİCİ ALANI"
        title="Kurs kayıtları"
        description="Ödeme gerektirmeyen yönetici kayıtlarını ve öğrenci ilerlemesini yönet."
      />
      <AdminNavigation />
      <section className="panel">
        <Field label="Kayıtları görüntülenecek kurs">
          <select value={courseId} onChange={(e) => setCourseId(e.target.value)}>
            <option value="">Kurs seçin</option>
            {courses.data?.content.map((c) => (
              <option value={c.id} key={c.id}>
                {c.title} · {c.status}
              </option>
            ))}
          </select>
        </Field>
        {courses.error && <ErrorBox error={courses.error} />}
      </section>
      {courseId && (
        <>
          <section className="panel">
            <h2>Öğrenciyi kursa ekle</h2>
            <Field label="Öğrencilerde ara">
              <input value={search} onChange={(e) => setSearch(e.target.value)} />
            </Field>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                grant.mutate({ ...Object.fromEntries(new FormData(e.currentTarget)), courseId });
              }}
            >
              <Field label="Kaydedilecek öğrenci">
                <select name="studentId" required>
                  <option value="">Öğrenci seçin</option>
                  {users.data?.content.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.firstName} {u.lastName} · {u.email}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Kayıt gerekçesi">
                <textarea name="reason" required maxLength={1000} />
              </Field>
              {users.error && <ErrorBox error={users.error} />}{' '}
              {grant.error && <ErrorBox error={grant.error} />}{' '}
              {grant.isSuccess && <Success>Öğrenci kursa kaydedildi.</Success>}
              <Submit busy={grant.isPending}>Kursa kaydet</Submit>
            </form>
          </section>
          <CourseStudents key={courseId} courseId={courseId} />
        </>
      )}
    </>
  );
}
export function AdminAudit() {
  const [page, setPage] = useState(0);
  const audit = useQuery({
    queryKey: ['admin-audit', page],
    queryFn: () =>
      api<
        {
          id: string;
          actor_id: string;
          target_id: string;
          action: string;
          reason: string;
          created_at: string;
        }[]
      >(`/users/admin/audit?page=${page}`),
  });
  return (
    <>
      <PageTitle eyebrow="YÖNETİCİ ALANI" title="Yönetici işlem kayıtları" />
      <AdminNavigation />
      {audit.isPending ? (
        <Loading />
      ) : audit.error ? (
        <ErrorBox error={audit.error} />
      ) : (
        <>
          <div className="table-scroll panel">
            <table>
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>İşlem</th>
                  <th>Gerekçe</th>
                  <th>Yönetici / kullanıcı</th>
                </tr>
              </thead>
              <tbody>
                {audit.data?.map((a) => (
                  <tr key={a.id}>
                    <td>{date(a.created_at)}</td>
                    <td>{a.action}</td>
                    <td>{a.reason}</td>
                    <td>
                      <small>
                        {a.actor_id}
                        <br />
                        {a.target_id}
                      </small>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="form-actions">
            <button
              className="button secondary"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Önceki
            </button>
            <button
              className="button secondary"
              disabled={(audit.data?.length || 0) < 25}
              onClick={() => setPage(page + 1)}
            >
              Sonraki
            </button>
          </div>
        </>
      )}
    </>
  );
}
