import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, body } from '../api';
import { useAuth } from '../auth';
import type { Category, Course } from '../types';
import { ErrorBox, Field, Loading, Modal, Submit, Success } from '../ui';

type Invitation = { course_id: string; title: string; instructor_name: string };
type Team = {
  ownerId: string;
  ownerName: string;
  canManage: boolean;
  members: { user_id: string; display_name: string; status: 'INVITED' | 'ACCEPTED' }[];
};
type Template = { id: string; name: string; builtIn: boolean };

export function CourseInvitations() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['course-invitations'],
    queryFn: () => api<Invitation[]>('/courses/invitations'),
  });
  const reply = useMutation({
    mutationFn: ({ course, accept }: { course: string; accept: boolean }) =>
      api(`/courses/${course}/team/reply`, { method: 'POST', body: body({ accept }) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course-invitations'] });
      void client.invalidateQueries({ queryKey: ['own-courses'] });
    },
  });
  if (query.error) return <ErrorBox error={query.error} retry={() => void query.refetch()} />;
  if (!query.data?.length) return null;
  return (
    <section className="panel">
      <h2>Course team invitations</h2>
      <p>
        Prepare drafts together. Enrollment management and publication submissions remain with the
        course owner.
      </p>
      {reply.error && <ErrorBox error={reply.error} />}
      {query.data.map((invitation) => (
        <div className="authoring-row" key={invitation.course_id}>
          <div>
            <strong>{invitation.title}</strong>
            <p>{invitation.instructor_name} invited you to collaborate.</p>
          </div>
          <button
            className="button primary small"
            disabled={reply.isPending}
            onClick={() => reply.mutate({ course: invitation.course_id, accept: true })}
          >
            Accept invitation
          </button>
          <button
            className="button secondary small"
            disabled={reply.isPending}
            onClick={() => reply.mutate({ course: invitation.course_id, accept: false })}
          >
            Decline invitation
          </button>
        </div>
      ))}
    </section>
  );
}

export function CourseTeam({ courseId }: { courseId: string }) {
  const client = useQueryClient(),
    navigate = useNavigate();
  const { session } = useAuth();
  const query = useQuery({
    queryKey: ['course-team', courseId],
    queryFn: () => api<Team>(`/courses/${courseId}/team`),
  });
  const [email, setEmail] = useState('');
  const invite = useMutation({
    mutationFn: () => api(`/courses/${courseId}/team`, { method: 'POST', body: body({ email }) }),
    onSuccess: () => {
      setEmail('');
      void query.refetch();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/courses/${courseId}/team/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      void client.invalidateQueries({ queryKey: ['own-courses'] });
      if (id === session?.user.id) navigate('/instructor');
      else void query.refetch();
    },
  });
  if (query.isPending) return <Loading />;
  if (query.error) return <ErrorBox error={query.error} retry={() => void query.refetch()} />;
  const team = query.data!;
  return (
    <section className="panel">
      <h2>Course team</h2>
      <p>
        <strong>Course owner:</strong> {team.ownerName}
      </p>
      <p>
        Co-instructors can edit draft text, lessons, questions and resources. The owner manages
        publication, learner data and team access. Access starts after an invitation is accepted.
      </p>
      {invite.error && <ErrorBox error={invite.error} />}
      {remove.error && <ErrorBox error={remove.error} />}
      {invite.isSuccess && (
        <Success>Invitation saved. The instructor can accept it from their studio.</Success>
      )}
      {team.canManage && (
        <form
          className="form-actions"
          onSubmit={(e) => {
            e.preventDefault();
            invite.mutate();
          }}
        >
          <Field
            label="Instructor's registered email address"
            hint="Choose an instructor with a verified email address."
          >
            <input
              type="email"
              required
              maxLength={254}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Submit busy={invite.isPending}>Invite instructor</Submit>
        </form>
      )}
      {team.members.length === 0 && <p className="muted">No co-instructors yet.</p>}
      {team.members.map((m) => (
        <div className="authoring-row" key={m.user_id}>
          <span>
            {m.display_name} · {m.status === 'ACCEPTED' ? 'Co-instructor' : 'Awaiting acceptance'}
          </span>
          {(team.canManage || m.user_id === session?.user.id) && (
            <button
              className="text-button danger"
              disabled={remove.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    m.user_id === session?.user.id
                      ? 'Leave this course team?'
                      : "Remove this instructor's course access?",
                  )
                )
                  remove.mutate(m.user_id);
              }}
            >
              {m.user_id === session?.user.id ? 'Leave team' : 'Remove access'}
            </button>
          )}
        </div>
      ))}
    </section>
  );
}

export function TemplateLauncher() {
  const [open, setOpen] = useState(false),
    navigate = useNavigate();
  const query = useQuery({
    queryKey: ['course-templates'],
    queryFn: () => api<Template[]>('/courses/templates'),
    enabled: open,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
    enabled: open,
  });
  const create = useMutation({
    mutationFn: (input: { template: string; title: string; categoryId: number }) =>
      api<Course>(`/courses/templates/${input.template}/create`, {
        method: 'POST',
        body: body({ title: input.title, categoryId: input.categoryId }),
      }),
    onSuccess: (course) => {
      navigate(`/instructor/${course.id}`);
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/courses/templates/${id}`, { method: 'DELETE' }),
    onSuccess: () => void query.refetch(),
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    create.mutate({
      template: String(f.get('template')),
      title: String(f.get('title')),
      categoryId: Number(f.get('categoryId')),
    });
  }
  return (
    <>
      <button className="button secondary" onClick={() => setOpen(true)}>
        Create course from template
      </button>
      {open && (
        <Modal title="Create course from template" onClose={() => setOpen(false)}>
          <p>
            Text, lesson structure and questions are copied with new identifiers. Learners, progress
            and files are not copied. Upload videos and protected resources to the new course.
          </p>
          {query.error && <ErrorBox error={query.error} />}{' '}
          {categories.error && <ErrorBox error={categories.error} />}{' '}
          {create.error && <ErrorBox error={create.error} />}{' '}
          {remove.error && <ErrorBox error={remove.error} />}
          {query.isPending || categories.isPending ? (
            <Loading />
          ) : (
            query.data &&
            categories.data && (
              <form onSubmit={submit} className="workspace-fields">
                <Field label="Course template">
                  <select name="template" required>
                    {query.data.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                        {t.builtIn ? ' · Built-in template' : ' · Personal template'}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="New course title">
                  <input name="title" required minLength={10} maxLength={200} />
                </Field>
                <Field label="New course category">
                  <select name="categoryId" required>
                    {categories.data.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Submit busy={create.isPending}>Create draft</Submit>
              </form>
            )
          )}
          {!!query.data?.some((t) => !t.builtIn) && (
            <div>
              <h3>My personal templates</h3>
              {query.data
                .filter((t) => !t.builtIn)
                .map((t) => (
                  <div className="authoring-row" key={t.id}>
                    <span>{t.name}</span>
                    <button
                      className="text-button danger"
                      disabled={remove.isPending}
                      onClick={() => {
                        if (
                          window.confirm(
                            'Delete this personal template? Existing courses will not be affected.',
                          )
                        )
                          remove.mutate(t.id);
                      }}
                    >
                      Delete template
                    </button>
                  </div>
                ))}
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
