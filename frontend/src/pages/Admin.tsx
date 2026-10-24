import { useAuth } from '../auth';
import { useState } from 'react';
import { NavLink, Link, Navigate } from 'react-router-dom';
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
import CourseWorkspace from './Workspace';

export function AdminNavigation() {
  const scope = useAuth().session?.user.adminScope || 'FULL';
  return (
    <nav className="feature-tabs" aria-label="Administration navigation">
      {[
        ['/admin', 'Overview'],
        ['/admin/users', 'Users'],
        ['/admin/courses', 'Course reviews'],
        ['/admin/categories', 'Categories'],
        ['/admin/enrollments', 'Enrollments'],
        ['/admin/audit', 'Audit log'],
        ['/admin/support', 'Support and reports'],
        ['/admin/operations', 'Operations and data requests'],
      ]
        .filter(
          ([to]) =>
            scope === 'FULL' ||
            (scope === 'MODERATION'
              ? to === '/admin/courses' || to === '/admin/categories'
              : to === '/admin/support' ||
                to === '/admin/operations' ||
                to === '/admin/enrollments'),
        )
        .map(([to, label]) => (
          <NavLink end={to === '/admin'} key={to} to={to} className="button secondary small">
            {label}
          </NavLink>
        ))}
    </nav>
  );
}
export function AdminDashboard() {
  const scope = useAuth().session?.user.adminScope || 'FULL';
  return scope === 'FULL' ? (
    <FullAdminDashboard />
  ) : (
    <Navigate to={scope === 'MODERATION' ? '/admin/courses' : '/admin/support'} replace />
  );
}
function FullAdminDashboard() {
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
        eyebrow="PLATFORM ADMINISTRATION"
        title="Administration"
        description="Manage users, course quality and learning outcomes."
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
              ['Users', data.data!.users.total],
              ['Instructors', data.data!.users.instructors],
              ['Courses awaiting review', data.data!.courses.pending],
              ['Published courses', data.data!.courses.published],
              ['Active enrollments', data.data!.enrollments.active],
              ['Completed enrollments', data.data!.enrollments.completed],
            ].map(([label, value]) => (
              <article className="panel" key={label}>
                <span className="muted">{label}</span>
                <h2>{value}</h2>
              </article>
            ))}
          </div>
          <section className="panel">
            <h2>Review workflow</h2>
            <p>
              Instructors submit courses for review. Approved courses appear in the catalog;
              requested changes include feedback for the instructor.
            </p>
            <Link className="button primary" to="/admin/courses?status=PENDING_REVIEW">
              Review courses
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
      <PageTitle eyebrow="ADMINISTRATION" title="Course reviews" />
      <AdminNavigation />
      <WorkspaceQueue />
      <div className="form-grid">
        <Field label="Filter by course status">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {['PENDING_REVIEW', 'PUBLISHED', 'DRAFT', 'ARCHIVED'].map((s) => (
              <option key={s} value={s}>
                {
                  {
                    PENDING_REVIEW: 'Awaiting approval',
                    PUBLISHED: 'Published',
                    DRAFT: 'Draft',
                    ARCHIVED: 'Archived',
                  }[s]
                }
              </option>
            ))}
          </select>
        </Field>
        <Field label="Search courses">
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
                  Review content
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
                      Approve
                    </button>
                    <button
                      className="button secondary"
                      onClick={() => {
                        decision.reset();
                        setSelected({ course: c, action: 'REJECT' });
                      }}
                    >
                      Request changes
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
                    Archive
                  </button>
                )}
              </div>
            </article>
          ))}
          <Pagination page={page} pages={courses.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="No courses with this status"
          text="Change the filter to review other courses."
        />
      )}
      {selected && (
        <Modal
          title={
            selected.action === 'APPROVE'
              ? 'Approve course'
              : selected.action === 'REJECT'
                ? 'Request changes'
                : 'Archive course'
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
            <Field label="Reason for decision">
              <textarea name="reason" required={selected.action !== 'APPROVE'} maxLength={2000} />
            </Field>
            {decision.error && <ErrorBox error={decision.error} />}
            <Submit busy={decision.isPending}>Save decision</Submit>
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
      <PageTitle eyebrow="ADMINISTRATION" title="Category management" />
      <AdminNavigation />
      <section className="panel">
        <h2>{editing ? 'Edit category' : 'Add category'}</h2>
        <form
          key={editing?.id || String(save.isSuccess)}
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(Object.fromEntries(new FormData(e.currentTarget)));
          }}
        >
          <Field label="Category name">
            <input name="name" required maxLength={100} defaultValue={editing?.name} />
          </Field>
          <Field label="Category description">
            <textarea name="description" maxLength={5000} defaultValue={editing?.description} />
          </Field>
          {save.error && <ErrorBox error={save.error} />}
          <Submit busy={save.isPending}>Save category</Submit>
          {editing && (
            <button type="button" className="text-button" onClick={() => setEditing(undefined)}>
              Cancel
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
                Edit category
              </button>
              <button
                className="text-button danger"
                disabled={remove.isPending}
                onClick={() => {
                  if (
                    window.confirm(
                      'Delete this category? Categories containing courses cannot be deleted.',
                    )
                  )
                    remove.mutate(c.id);
                }}
              >
                Delete category
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
        eyebrow="ADMINISTRATION"
        title="Course enrollments"
        description="Manage free administrative enrollments and learner progress."
      />
      <AdminNavigation />
      <section className="panel">
        <Field label="Select a course to view enrollments">
          <select value={courseId} onChange={(e) => setCourseId(e.target.value)}>
            <option value="">Select a course</option>
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
            <h2>Add a learner to the course</h2>
            <Field label="Search learners">
              <input value={search} onChange={(e) => setSearch(e.target.value)} />
            </Field>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                grant.mutate({ ...Object.fromEntries(new FormData(e.currentTarget)), courseId });
              }}
            >
              <Field label="Learner to enroll">
                <select name="studentId" required>
                  <option value="">Select a learner</option>
                  {users.data?.content.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.firstName} {u.lastName} · {u.email}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Reason for enrollment">
                <textarea name="reason" required maxLength={1000} />
              </Field>
              {users.error && <ErrorBox error={users.error} />}{' '}
              {grant.error && <ErrorBox error={grant.error} />}{' '}
              {grant.isSuccess && <Success>The learner has been enrolled.</Success>}
              <Submit busy={grant.isPending}>Enroll in course</Submit>
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
      <PageTitle eyebrow="ADMINISTRATION" title="Administrator audit log" />
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
                  <th>Date</th>
                  <th>Action</th>
                  <th>Reason</th>
                  <th>Administrator / User</th>
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
              Previous
            </button>
            <button
              className="button secondary"
              disabled={(audit.data?.length || 0) < 25}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </>
  );
}

function WorkspaceQueue() {
  const [selected, setSelected] = useState<string>();
  const q = useQuery({
    queryKey: ['workspace-queue'],
    queryFn: () =>
      api<{ course_id: string; title: string; instructor_name: string; revision: number }[]>(
        '/courses/admin/workspaces',
      ),
  });
  return (
    <section className="panel">
      <h2>Publication drafts</h2>
      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} />
      ) : q.data?.length ? (
        q.data.map((w) => (
          <article className="lesson-row" key={w.course_id}>
            <span>
              {w.title} · {w.instructor_name} · Draft {w.revision}
            </span>
            <button className="button secondary" onClick={() => setSelected(w.course_id)}>
              Review draft
            </button>
          </article>
        ))
      ) : (
        <p>No publication drafts are awaiting review.</p>
      )}
      {selected && (
        <Modal title="Publication draft review" onClose={() => setSelected(undefined)}>
          <CourseWorkspace courseId={selected} review />
        </Modal>
      )}
    </section>
  );
}
