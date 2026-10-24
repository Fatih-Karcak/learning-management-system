import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowRight,
  ArrowUpRight,
  Award,
  BookOpen,
  CheckCheck,
  Compass,
  Play,
  Search,
  Sparkles,
  TrendingUp,
} from 'lucide-react';
import { useAuth } from '../auth';
import { api, levels } from '../api';
import type { Category, Course, Enrollment, Page } from '../types';
import { CourseCard, Empty, ErrorBox, Loading, PageTitle, Pagination } from '../ui';

export function InstructorCourses() {
  const { instructorId } = useParams();
  const [page, setPage] = useState(0);
  const courses = useQuery({
    queryKey: ['instructor-courses', instructorId, page],
    queryFn: () => api<Page<Course>>(`/courses/instructor/${instructorId}?page=${page}&size=9`),
  });
  return (
    <>
      <PageTitle
        eyebrow="LEARN FROM EXPERIENCED INSTRUCTORS"
        title={courses.data?.content[0]?.instructorName || 'Instructor courses'}
        description="Explore the courses published by this instructor."
      />
      {courses.isPending ? (
        <Loading />
      ) : courses.error ? (
        <ErrorBox error={courses.error} retry={() => void courses.refetch()} />
      ) : courses.data?.content.length ? (
        <>
          <div className="course-grid">
            {courses.data.content.map((c) => (
              <CourseCard key={c.id} course={c} />
            ))}
          </div>
          <Pagination page={page} pages={courses.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="No published courses yet"
          text="New courses from this instructor will appear here."
        />
      )}
    </>
  );
}
export function Dashboard() {
  const { session } = useAuth();
  return session?.user.role === 'ADMIN' ? <Navigate to="/admin" replace /> : <LearningDashboard />;
}
function LearningDashboard() {
  const { session } = useAuth();
  const courses = useQuery({
    queryKey: ['popular'],
    queryFn: () => api<Page<Course>>('/courses/popular?size=3'),
  });
  const enrollments = useQuery({
    queryKey: ['dashboard-enrollments'],
    queryFn: () => api<Page<Enrollment>>('/enrollments/my?size=100'),
    enabled: !!session,
  });
  const items = enrollments.data?.content || [];
  const active = items.find((e) => e.status === 'ACTIVE');
  return (
    <>
      <PageTitle
        eyebrow="A GOOD DAY TO LEARN"
        title={session ? `Hello, ${session.user.firstName} 👋` : 'Your next step starts here.'}
        description="Explore your interests and learn something new."
      >
        <span className="date-chip">
          {new Intl.DateTimeFormat('en-US', {
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          }).format(new Date())}
        </span>
      </PageTitle>
      <section className="hero">
        <div className="hero-copy">
          <span className="hero-tag">
            <span /> YOUR FUTURE, YOUR PACE
          </span>
          <h2>
            What you learn today
            <br />
            can shape <em>your tomorrow.</em>
          </h2>
          <p>
            Move closer to your goals. Learn from experts,
            <br className="desktop-only" /> and put your knowledge into practice.
          </p>
          <Link className="button lime" to={active ? `/learn/${active.id}` : '/catalog'}>
            {active ? 'Keep learning' : 'Explore courses'} <ArrowUpRight size={18} />
          </Link>
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="art-book">
            <BookOpen size={98} strokeWidth={1} />
          </div>
          <span className="art-spark">✦</span>
          <div className="art-note">
            <span className="round-icon">
              <TrendingUp size={20} />
            </span>
            <div>
              <b>A little better every day</b>
              <small>Continue your journey.</small>
            </div>
          </div>
          <span className="art-dot" />
        </div>
      </section>
      {session && (
        <div className="stats-grid">
          {[
            {
              label: 'Enrolled courses',
              value: enrollments.data?.totalElements,
              icon: BookOpen,
            },
            {
              label: 'Completed courses',
              value: enrollments.data
                ? items.filter((x) => x.status === 'COMPLETED').length
                : undefined,
              icon: CheckCheck,
            },
            {
              label: 'Courses in progress',
              value: enrollments.data
                ? items.filter((x) => x.status === 'ACTIVE').length
                : undefined,
              icon: TrendingUp,
            },
            {
              label: 'Courses ready for certification',
              value: enrollments.data
                ? items.filter((x) => x.status === 'COMPLETED').length
                : undefined,
              icon: Award,
            },
          ].map(({ label, value, icon: Icon }) => (
            <div className="stat-card" key={label}>
              <span className="stat-icon">
                <Icon size={21} />
              </span>
              <div>
                <strong>{value ?? '—'}</strong>
                <p>{label}</p>
              </div>
            </div>
          ))}
        </div>
      )}
      {enrollments.error && (
        <ErrorBox error={enrollments.error} retry={() => void enrollments.refetch()} />
      )}
      {active && (
        <section className="continue-banner">
          <div className="continue-icon">
            <Play size={24} />
          </div>
          <div>
            <span className="eyebrow">PICK UP WHERE YOU LEFT OFF</span>
            <h3>{active.courseTitle}</h3>
            <p>
              {active.completedLessons} / {active.totalLessons} lessons completed
            </p>
          </div>
          <div className="continue-progress">
            <span>{Math.round(active.progressPercentage || 0)}%</span>
            <progress max="100" value={active.progressPercentage || 0} />
          </div>
          <Link className="button secondary" to={`/learn/${active.id}`}>
            Continue <ArrowRight size={17} />
          </Link>
        </section>
      )}
      <section>
        <div className="section-heading">
          <div>
            <span className="eyebrow">DISCOVER SOMETHING NEW</span>
            <h2>Find your next interest here.</h2>
          </div>
          <Link className="inline-link" to="/catalog">
            All courses <ArrowRight size={17} />
          </Link>
        </div>
        {courses.isPending ? (
          <Loading />
        ) : courses.error ? (
          <ErrorBox error={courses.error} retry={() => void courses.refetch()} />
        ) : courses.data?.content.length ? (
          <div className="course-grid">
            {courses.data.content.map((course) => (
              <CourseCard key={course.id} course={course} />
            ))}
          </div>
        ) : (
          <Empty
            title="Make room for new knowledge."
            text="Published courses will appear here. Instructors can share their first course."
          >
            <Link
              className="button secondary"
              to={session && session.user.role !== 'STUDENT' ? '/instructor/new' : '/catalog'}
            >
              <Compass size={17} />{' '}
              {session && session.user.role !== 'STUDENT' ? 'Create course' : 'Browse the catalog'}
            </Link>
          </Empty>
        )}
      </section>
      <div className="journey-note">
        <Sparkles size={24} />
        <div>
          <strong>Learning is a journey.</strong>
          <p>One lesson, one idea, one new beginning.</p>
        </div>
        <span>One step forward ↗</span>
      </div>
    </>
  );
}
export function Catalog() {
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
  });
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState(params.get('q') || '');
  const q = params.get('q') || '';
  const category = params.get('category') || '';
  const sort = params.get('sort') || 'new';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const level = params.get('level') || '',
    language = params.get('language') || '',
    free = params.get('free') === 'true',
    rating = params.get('rating') || '';
  const advanced = !!(level || language || free || rating || sort === 'rating');
  const endpoint = advanced
    ? `/courses/discover?query=${encodeURIComponent(q)}${category ? `&category=${category}` : ''}${level ? `&level=${level}` : ''}${language ? `&language=${encodeURIComponent(language)}` : ''}${free ? '&free=true' : ''}${rating ? `&rating=${rating}` : ''}&order=${sort}`
    : q
      ? `/courses/search?query=${encodeURIComponent(q)}`
      : category
        ? `/courses/category/${encodeURIComponent(category)}?`
        : sort === 'popular'
          ? '/courses/popular?'
          : '/courses?';
  const courses = useQuery({
    queryKey: ['catalog', q, category, sort, page, level, language, free, rating],
    queryFn: () =>
      api<Page<Course>>(
        `${endpoint}${endpoint.endsWith('?') ? '' : '&'}page=${page}&size=9${sort === 'new' ? '&sort=createdAt,desc' : ''}`,
      ),
  });
  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    next.set(key, value);
    if (key !== 'page') next.delete('page');

    setParams(next);
  }
  function search(e: FormEvent) {
    e.preventDefault();
    update('q', query);
  }
  return (
    <>
      <PageTitle
        eyebrow="BE CURIOUS. EXPLORE. LEARN."
        title="What will you learn next?"
        description="Make room for a new skill and a new perspective."
      />
      <div className="catalog-toolbar">
        <form className="search-form" onSubmit={search}>
          <Search size={19} />
          <input
            aria-label="Search courses"
            placeholder="Search for something you want to learn…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button className="button primary small">Search</button>
        </form>
        <select
          aria-label="Sort courses"
          value={sort}
          onChange={(e) => update('sort', e.target.value)}
        >
          <option value="new">Newest courses</option>
          <option value="popular">Most popular</option>
          <option value="rating">Highest rated</option>
        </select>
        <select
          aria-label="Category"
          value={category}
          onChange={(e) => update('category', e.target.value)}
        >
          <option value="">All categories</option>
          {categories.data?.map((c) => (
            <option value={c.id} key={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-grid">
        <label>
          Level
          <select value={level} onChange={(e) => update('level', e.target.value)}>
            <option value="">All levels</option>
            {Object.entries(levels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Language
          <select value={language} onChange={(e) => update('language', e.target.value)}>
            <option value="">All languages</option>
            <option value="Turkish">Turkish</option>
            <option value="English">English</option>
          </select>
        </label>
        <label>
          Minimum rating
          <select value={rating} onChange={(e) => update('rating', e.target.value)}>
            <option value="">All ratings</option>
            <option value="4">4 and above</option>
            <option value="4.5">4.5 and above</option>
          </select>
        </label>
        <label className="checkbox-field">
          <input
            type="checkbox"
            checked={free}
            onChange={(e) => update('free', String(e.target.checked))}
          />{' '}
          Free courses
        </label>
      </div>
      {categories.error && (
        <ErrorBox error={categories.error} retry={() => void categories.refetch()} />
      )}
      <div className="results-heading">
        <span>
          {courses.data ? `${courses.data.totalElements} courses` : 'Courses'} {q && `· “${q}”`}
          {category &&
            ` · ${categories.data?.find((c) => String(c.id) === category)?.name || 'Selected category'}`}
        </span>
        {(q || category) && (
          <button
            className="text-button"
            onClick={() => {
              setParams({});
              setQuery('');
            }}
          >
            Clear filters
          </button>
        )}
      </div>
      {courses.isPending ? (
        <Loading />
      ) : courses.error ? (
        <ErrorBox error={courses.error} retry={() => void courses.refetch()} />
      ) : courses.data?.content.length ? (
        <>
          <div className="course-grid">
            {courses.data.content.map((course) => (
              <CourseCard key={course.id} course={course} />
            ))}
          </div>
          <Pagination
            page={page}
            pages={courses.data.totalPages}
            onChange={(p) => update('page', String(p))}
          />
        </>
      ) : (
        <Empty
          title="No courses found"
          text={
            q || category
              ? 'Try another search or clear the filters.'
              : 'New courses will appear here when they are published.'
          }
        />
      )}
    </>
  );
}
