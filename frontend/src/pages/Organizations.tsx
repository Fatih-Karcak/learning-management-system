import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api, body, authenticatedFetch, date } from '../api';
import { useAuth } from '../auth';
import { parseEmailImport } from '../csv';
import type { Course, Page } from '../types';
import { ErrorBox, Field, Loading, PageTitle, Pagination, Success } from '../ui';
const base = '/enrollments/organizations';
interface Organization {
  id: string;
  name: string;
}
interface Group {
  id: string;
  name: string;
  member_count: number;
}
interface Invitation {
  group_id: string;
  status: string;
  group_name: string;
  organization_name: string;
}
interface Member {
  student_id: string;
  display_name: string;
  email: string;
  status: string;
}
export default function Organizations() {
  const { session } = useAuth();
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const manage =
    session?.user.role === 'INSTRUCTOR' ||
    (session?.user.role === 'ADMIN' &&
      (!session.user.adminScope || session.user.adminScope === 'FULL'));
  const [organizationPage, setOrganizationPage] = useState(0);
  const org = params.get('organization'),
    group = params.get('group');
  const organizations = useQuery({
    queryKey: ['organizations', organizationPage],
    queryFn: () =>
      api<{ content: Organization[]; total: number }>(`${base}?page=${organizationPage}`),
    enabled: manage,
  });
  const invitations = useQuery({
    queryKey: ['group-invitations'],
    queryFn: () => api<Invitation[]>(`${base}/invitations`),
  });
  const detail = useQuery({
    queryKey: ['organization', org],
    queryFn: () => api<{ organization: Organization; groups: Group[] }>(`${base}/${org}`),
    enabled: manage && !!org,
  });
  const action = useMutation({
    mutationFn: ({ path, data }: { path: string; data: unknown }) =>
      api(path, { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['organizations'] });
      void client.invalidateQueries({ queryKey: ['organization'] });
      void client.invalidateQueries({ queryKey: ['group-invitations'] });
      void client.invalidateQueries({ queryKey: ['enrollments'] });
    },
  });
  return (
    <>
      <PageTitle
        title="Organizations and training groups"
        description="Manage group training and track assigned courses. Learners choose whether to accept an invitation."
      />
      {action.error && <ErrorBox error={action.error} />}
      {action.isSuccess && <Success>Changes saved.</Success>}
      <section className="panel">
        <h2>Your training groups</h2>
        {invitations.error && <ErrorBox error={invitations.error} />}
        {invitations.data?.map((invite) => (
          <article key={invite.group_id}>
            <h3>
              {invite.organization_name} · {invite.group_name}
            </h3>
            <p>{invite.status}</p>
            {invite.status === 'INVITED' && (
              <>
                <p>
                  Accepting shares your progress for courses assigned to this group with the
                  organization manager.
                </p>
                <button
                  className="button primary"
                  disabled={action.isPending}
                  onClick={() =>
                    action.mutate({
                      path: `${base}/groups/${invite.group_id}/reply`,
                      data: { accept: true },
                    })
                  }
                >
                  Accept invitation
                </button>
                <button
                  className="button secondary"
                  disabled={action.isPending}
                  onClick={() =>
                    action.mutate({
                      path: `${base}/groups/${invite.group_id}/reply`,
                      data: { accept: false },
                    })
                  }
                >
                  Decline
                </button>
              </>
            )}
          </article>
        ))}
        {!invitations.data?.length && <p>You have no training group invitations.</p>}
      </section>
      {manage && (
        <>
          <section className="panel">
            <h2>Organizations you manage</h2>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                action.mutate({
                  path: base,
                  data: { name: String(new FormData(e.currentTarget).get('name')) },
                });
              }}
            >
              <Field label="Organization name">
                <input name="name" required maxLength={200} />
              </Field>
              <button className="button primary" disabled={action.isPending}>
                Create organization
              </button>
            </form>
            {organizations.error && <ErrorBox error={organizations.error} />}
            {organizations.data?.content.map((item) => (
              <button
                className="button secondary"
                key={item.id}
                onClick={() => setParams({ organization: item.id })}
              >
                {item.name}
              </button>
            ))}
            <Pagination
              page={organizationPage}
              pages={Math.ceil((organizations.data?.total || 0) / 12)}
              onChange={setOrganizationPage}
            />
          </section>
          {detail.isFetching && <Loading />}
          {detail.error && <ErrorBox error={detail.error} />}
          {detail.data && (
            <section className="panel">
              <h2>{detail.data.organization.name}</h2>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  action.mutate({
                    path: `${base}/${org}/groups`,
                    data: { name: String(new FormData(e.currentTarget).get('name')) },
                  });
                }}
              >
                <Field label="Group name">
                  <input name="name" required maxLength={200} />
                </Field>
                <button className="button primary" disabled={action.isPending}>
                  Create group
                </button>
              </form>
              {detail.data.groups.map((item) => (
                <button
                  className="button secondary"
                  key={item.id}
                  onClick={() => setParams({ organization: org!, group: item.id })}
                >
                  {item.name} · {item.member_count} learners
                </button>
              ))}
            </section>
          )}
          {group && <GroupManager key={group} group={group} />}
        </>
      )}
    </>
  );
}
function GroupManager({ group }: { group: string }) {
  const client = useQueryClient();
  const [page, setPage] = useState(0),
    [reportPage, setReportPage] = useState(0),
    [coursePage, setCoursePage] = useState(0);
  const [emails, setEmails] = useState(''),
    [search, setSearch] = useState(''),
    [selected, setSelected] = useState(''),
    [error, setError] = useState<unknown>();
  const members = useQuery({
    queryKey: ['group-members', group, page],
    queryFn: () =>
      api<{ content: Member[]; total: number }>(`${base}/groups/${group}/members?page=${page}`),
  });
  const courses = useQuery({
    queryKey: ['group-courses', search, coursePage],
    queryFn: () =>
      api<Page<Course>>(
        `/courses/discover?free=true&query=${encodeURIComponent(search)}&page=${coursePage}&size=8`,
      ),
  });
  const report = useQuery({
    queryKey: ['group-report', group, reportPage],
    refetchInterval: 30000,
    queryFn: () =>
      api<{
        content: {
          display_name: string;
          course_title: string;
          status: string;
          progress_percentage: number;
          due_at?: string;
          overdue: boolean;
        }[];
        total: number;
      }>(`${base}/groups/${group}/report?page=${reportPage}`),
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
    }) =>
      api(`${base}/groups/${group}${path}`, {
        method,
        body: data === undefined ? undefined : body(data),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['group-members', group] });
      void client.invalidateQueries({ queryKey: ['group-report', group] });
    },
  });
  async function exportReport() {
    try {
      const response = await authenticatedFetch(`${base}/groups/${group}/report.csv`);
      if (!response.ok) throw new Error('The report could not be exported.');
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a');
      link.href = url;
      link.download = 'training-report.csv';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      setError(error);
    }
  }
  return (
    <section className="panel">
      <h2>Group learners and assignments</h2>
      {!!error && <ErrorBox error={error} />} {action.error && <ErrorBox error={action.error} />}{' '}
      {action.isSuccess && <Success>Group updated.</Success>}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          try {
            setError(undefined);
            action.mutate({ path: '/members', data: { emails: parseEmailImport(emails) } });
          } catch (error) {
            setError(error);
          }
        }}
      >
        <Field label="Learner email addresses">
          <textarea value={emails} rows={5} onChange={(e) => setEmails(e.target.value)} />
        </Field>
        <Field label="Import learners from CSV">
          <input
            type="file"
            accept=".csv,text/csv,text/plain"
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              try {
                if (file.size > 1024 * 1024)
                  throw new Error('The CSV file must be smaller than 1 MiB.');
                const content = await file.text();
                parseEmailImport(content);
                setEmails(content);
                setError(undefined);
              } catch (error) {
                setError(error);
              }
            }}
          />
        </Field>
        <p>
          Use one email per line or a CSV file with an email column. Invite up to 200 verified
          accounts at a time.
        </p>
        <button className="button primary" disabled={action.isPending}>
          Invite learners
        </button>
      </form>
      {members.error && <ErrorBox error={members.error} />}
      <ul>
        {members.data?.content?.map((member) => (
          <li key={member.student_id}>
            {member.display_name} · {member.email} · {member.status}{' '}
            <button
              className="text-button"
              disabled={action.isPending}
              onClick={() =>
                action.mutate({ path: `/members/${member.student_id}`, method: 'DELETE' })
              }
            >
              Remove from group
            </button>
          </li>
        ))}
      </ul>
      <Pagination
        page={page}
        pages={Math.ceil((members.data?.total || 0) / 50)}
        onChange={setPage}
      />
      <h3>Assign a course</h3>
      <Field label="Find free courses">
        <input
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setCoursePage(0);
          }}
        />
      </Field>
      {courses.data?.content?.map((course) => (
        <label key={course.id}>
          <input
            name="group-course"
            type="radio"
            checked={selected === course.id}
            onChange={() => setSelected(course.id)}
          />
          {course.title}
        </label>
      ))}
      <Pagination
        page={coursePage}
        pages={courses.data?.totalPages || 0}
        onChange={setCoursePage}
      />
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const due = String(new FormData(e.currentTarget).get('due') || '');
          action.mutate({
            path: '/courses',
            data: { courseId: selected, dueAt: due ? new Date(due).toISOString() : undefined },
          });
        }}
      >
        <Field label="Target completion date">
          <input type="datetime-local" name="due" />
        </Field>
        <button className="button primary" disabled={!selected || action.isPending}>
          Assign to accepted learners
        </button>
      </form>
      <h3>Training progress</h3>
      <button className="button secondary" onClick={() => void report.refetch()}>
        Refresh progress report
      </button>
      <button className="button secondary" onClick={() => void exportReport()}>
        Export report as CSV
      </button>
      {report.error && <ErrorBox error={report.error} />}
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Learner</th>
              <th>Course</th>
              <th>Progress</th>
              <th>Status</th>
              <th>Target date</th>
            </tr>
          </thead>
          <tbody>
            {report.data?.content?.map((row, index) => (
              <tr key={index}>
                <td>{row.display_name}</td>
                <td>{row.course_title}</td>
                <td>{Math.round(row.progress_percentage || 0)}%</td>
                <td>{row.overdue ? 'Overdue' : row.status}</td>
                <td>{row.due_at ? date(row.due_at) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        page={reportPage}
        pages={Math.ceil((report.data?.total || 0) / 50)}
        onChange={setReportPage}
      />
    </section>
  );
}
