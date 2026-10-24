import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { api, body } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Field, Loading, PageTitle, Pagination, Success } from '../ui';
import type { Course, Page } from '../types';

interface Path {
  id: string;
  title: string;
  description: string;
  revision: number;
  owner_id: string;
  published: boolean;
  course_count: number;
}
interface Detail {
  path: Path;
  steps: {
    course_id: string;
    title: string;
    status: string;
    completed: boolean;
    enrolled: boolean;
  }[];
}
export default function LearningPaths() {
  const { session } = useAuth();
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(false);
  const paths = useQuery({
    queryKey: ['learning-paths', page],
    queryFn: () => api<{ content: Path[]; total: number }>(`/courses/paths?page=${page}`),
  });
  const id = params.get('path');
  const detail = useQuery({
    queryKey: ['learning-path', id, session?.user.id],
    queryFn: () => api<Detail>(`/courses/paths/${id}`),
    enabled: !!id,
  });
  const author = session?.user.role === 'INSTRUCTOR' || session?.user.role === 'ADMIN';
  return (
    <>
      <PageTitle
        title="Learning paths"
        description="Follow a curated sequence of courses and track your progress."
      />
      {author && (
        <button className="button primary" onClick={() => setEditing(true)}>
          Create learning path
        </button>
      )}
      {editing && (
        <PathEditor
          close={() => {
            setEditing(false);
            void paths.refetch();
          }}
        />
      )}
      {paths.isPending ? (
        <Loading />
      ) : paths.error ? (
        <ErrorBox error={paths.error} />
      ) : (
        <>
          <div className="course-grid">
            {paths.data?.content?.map((path) => (
              <article className="panel" key={path.id}>
                <h2>
                  <button className="text-button" onClick={() => setParams({ path: path.id })}>
                    {path.title}
                  </button>
                </h2>
                <p>{path.description}</p>
                <small>
                  {path.course_count} courses · {path.published ? 'Published' : 'Draft'}
                </small>
              </article>
            ))}
          </div>
          {!paths.data?.content.length && <p>No learning paths have been published yet.</p>}
          <Pagination
            page={page}
            pages={Math.ceil((paths.data?.total || 0) / 12)}
            onChange={setPage}
          />
        </>
      )}
      {detail.isFetching && <Loading />}
      {detail.error && <ErrorBox error={detail.error} />}
      {detail.data && (
        <section className="panel">
          <h2>{detail.data.path.title}</h2>
          <p>
            {detail.data.steps.filter((s) => s.completed).length} of {detail.data.steps.length}{' '}
            courses completed
          </p>
          <ol>
            {detail.data.steps.map((step) => (
              <li key={step.course_id}>
                <Link to={`/courses/${step.course_id}`}>{step.title}</Link> ·{' '}
                {step.completed ? 'Completed' : step.enrolled ? 'In progress' : 'Not started'}
              </li>
            ))}
          </ol>
          {(detail.data.path.owner_id === session?.user.id || session?.user.role === 'ADMIN') && (
            <PathEditor
              initial={detail.data}
              close={() => {
                void detail.refetch();
                void paths.refetch();
              }}
            />
          )}
        </section>
      )}
    </>
  );
}
function PathEditor({ initial, close }: { initial?: Detail; close: () => void }) {
  const [title, setTitle] = useState(initial?.path.title || '');
  const [description, setDescription] = useState(initial?.path.description || '');
  const [published, setPublished] = useState(initial?.path.published || false);
  const [selected, setSelected] = useState(
    initial?.steps.map((s) => ({ id: s.course_id, title: s.title })) || [],
  );
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const catalog = useQuery({
    queryKey: ['path-catalog', search, page],
    queryFn: () =>
      api<Page<Course>>(
        `/courses/discover?query=${encodeURIComponent(search)}&page=${page}&size=8`,
      ),
  });
  const save = useMutation({
    mutationFn: () =>
      api(`/courses/paths${initial ? `/${initial.path.id}` : ''}`, {
        method: initial ? 'PUT' : 'POST',
        body: body({
          revision: initial?.path.revision || 0,
          title,
          description,
          published,
          courses: selected.map((c) => c.id),
        }),
      }),
    onSuccess: close,
  });
  return (
    <section className="panel">
      <h2>{initial ? 'Edit learning path' : 'New learning path'}</h2>
      <Field label="Path title">
        <input value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <Field label="Description">
        <textarea
          value={description}
          maxLength={4000}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
      <ol>
        {selected.map((course, index) => (
          <li key={course.id}>
            {course.title}{' '}
            <button
              type="button"
              className="text-button"
              disabled={index === 0}
              onClick={() =>
                setSelected((current) => {
                  const next = [...current];
                  [next[index - 1], next[index]] = [next[index], next[index - 1]];
                  return next;
                })
              }
            >
              Move up
            </button>{' '}
            <button
              type="button"
              className="text-button"
              onClick={() => setSelected((current) => current.filter((c) => c.id !== course.id))}
            >
              Remove
            </button>
          </li>
        ))}
      </ol>
      <Field label="Find courses">
        <input
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </Field>
      {catalog.error && <ErrorBox error={catalog.error} />}
      {catalog.data?.content
        .filter((c) => !selected.some((s) => s.id === c.id))
        .map((c) => (
          <button
            key={c.id}
            type="button"
            className="button secondary"
            disabled={selected.length >= 50}
            onClick={() => setSelected([...selected, { id: c.id, title: c.title }])}
          >
            Add {c.title}
          </button>
        ))}
      <Pagination page={page} pages={catalog.data?.totalPages || 0} onChange={setPage} />
      <label>
        <input
          type="checkbox"
          checked={published}
          onChange={(e) => setPublished(e.target.checked)}
        />{' '}
        Publish this path
      </label>
      {save.error && <ErrorBox error={save.error} />}
      <button
        className="button primary"
        disabled={!title.trim() || !selected.length || save.isPending}
        onClick={() => save.mutate()}
      >
        Save learning path
      </button>
    </section>
  );
}

export function LearningRules({ courseId }: { courseId: string }) {
  const client = useQueryClient();
  const path = `/courses/${courseId}/learning-rules`;
  const query = useQuery({
    queryKey: ['learning-rules', courseId],
    queryFn: () =>
      api<{ revision: number; sequential: boolean; releaseDays: number; prerequisites: string[] }>(
        path,
      ),
  });
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<{ id: string; title: string }[]>([]);
  const catalog = useQuery({
    queryKey: ['prerequisite-catalog', search],
    queryFn: () =>
      api<Page<Course>>(`/courses/discover?query=${encodeURIComponent(search)}&size=12`),
  });
  const save = useMutation({
    mutationFn: (data: unknown) => api(path, { method: 'PUT', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['learning-rules', courseId] });
    },
  });
  if (query.isPending) return <Loading />;
  if (query.error) return <ErrorBox error={query.error} />;
  return (
    <section className="panel">
      <h2>Learning access rules</h2>
      <p>
        Rules apply to new enrollments. Existing learners keep their access. Public previews remain
        available.
      </p>
      <form
        key={query.data!.revision}
        onSubmit={(e) => {
          e.preventDefault();
          const form = new FormData(e.currentTarget);
          save.mutate({
            ...query.data,
            sequential: form.has('sequential'),
            releaseDays: Number(form.get('days')),
            prerequisites: Array.from(
              new Set([
                ...query.data!.prerequisites.filter((id) => !form.has(`remove-${id}`)),
                ...selected.map((c) => c.id),
              ]),
            ),
          });
        }}
      >
        <label>
          <input name="sequential" type="checkbox" defaultChecked={query.data!.sequential} />{' '}
          Require previous lessons to be completed
        </label>
        <Field label="Days between lesson releases">
          <input
            name="days"
            type="number"
            min={0}
            max={365}
            defaultValue={query.data!.releaseDays}
          />
        </Field>
        <h3>Prerequisite courses</h3>
        {query.data!.prerequisites.map((id) => (
          <Prerequisite key={id} id={id} />
        ))}
        <Field label="Find a prerequisite course">
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </Field>
        {catalog.data?.content
          .filter(
            (c) =>
              c.id !== courseId &&
              !query.data!.prerequisites.includes(c.id) &&
              !selected.some((s) => s.id === c.id),
          )
          .map((c) => (
            <button
              type="button"
              className="text-button"
              key={c.id}
              onClick={() => setSelected([...selected, { id: c.id, title: c.title }])}
            >
              Add {c.title}
            </button>
          ))}
        {selected.map((c) => (
          <p key={c.id}>
            {c.title}{' '}
            <button
              type="button"
              onClick={() => setSelected(selected.filter((s) => s.id !== c.id))}
            >
              Remove
            </button>
          </p>
        ))}
        {save.error && <ErrorBox error={save.error} />}
        <button className="button primary" disabled={save.isPending}>
          Save access rules
        </button>
        {save.isSuccess && <Success>Access rules saved.</Success>}
      </form>
    </section>
  );
}
function Prerequisite({ id }: { id: string }) {
  const course = useQuery({
    queryKey: ['course', id],
    queryFn: () => api<Course>(`/courses/${id}`),
  });
  return (
    <p>
      {course.data?.title || 'Prerequisite course'}{' '}
      <label>
        <input name={`remove-${id}`} type="checkbox" /> Remove prerequisite
      </label>
    </p>
  );
}
