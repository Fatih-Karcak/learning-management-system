import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router-dom';
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
import { api } from '../api';
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
        eyebrow="BİLGİSİYLE İLHAM VERENLER"
        title={courses.data?.content[0]?.instructorName || 'Eğitmenin kursları'}
        description="Bu eğitmenin yayınladığı kursları keşfet."
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
          title="Henüz yayınlanmış bir kurs yok"
          text="Eğitmen yeni bir kurs yayınladığında burada görünecek."
        />
      )}
    </>
  );
}
export function Dashboard() {
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
        eyebrow="ÖĞRENMEK İÇİN GÜZEL BİR GÜN"
        title={session ? `Merhaba, ${session.user.firstName} 👋` : 'Bir sonraki adımın, ileri.'}
        description="Merak ettiğin konuları keşfet, kendine yeni bir şey kat."
      >
        <span className="date-chip">
          {new Intl.DateTimeFormat('tr-TR', {
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          }).format(new Date())}
        </span>
      </PageTitle>
      <section className="hero">
        <div className="hero-copy">
          <span className="hero-tag">
            <span /> SENİN GELECEĞİN, SENİN HIZIN
          </span>
          <h2>
            Bugün öğrendiğin,
            <br />
            yarınını <em>değiştirsin.</em>
          </h2>
          <p>
            Hedeflerine bir adım daha yaklaş. Uzmanlardan öğren,
            <br className="desktop-only" /> öğrendiklerini hayata geçir.
          </p>
          <Link className="button lime" to={active ? `/learn/${active.id}` : '/catalog'}>
            {active ? 'Öğrenmeye devam et' : 'Kursları keşfet'} <ArrowUpRight size={18} />
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
              <b>Her gün biraz daha iyi</b>
              <small>Yolculuğuna devam et.</small>
            </div>
          </div>
          <span className="art-dot" />
        </div>
      </section>
      {session && (
        <div className="stats-grid">
          {[
            {
              label: 'Kayıtlı olduğun kurs',
              value: enrollments.data?.totalElements,
              icon: BookOpen,
            },
            {
              label: 'Tamamlanan kurs',
              value: enrollments.data
                ? items.filter((x) => x.status === 'COMPLETED').length
                : undefined,
              icon: CheckCheck,
            },
            {
              label: 'Devam eden kurs',
              value: enrollments.data
                ? items.filter((x) => x.status === 'ACTIVE').length
                : undefined,
              icon: TrendingUp,
            },
            {
              label: 'Sertifikaya hazır kurs',
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
            <span className="eyebrow">KALDIĞIN YERDEN DEVAM ET</span>
            <h3>{active.courseTitle}</h3>
            <p>
              {active.completedLessons} / {active.totalLessons} ders tamamlandı
            </p>
          </div>
          <div className="continue-progress">
            <span>%{Math.round(active.progressPercentage || 0)}</span>
            <progress max="100" value={active.progressPercentage || 0} />
          </div>
          <Link className="button secondary" to={`/learn/${active.id}`}>
            Devam et <ArrowRight size={17} />
          </Link>
        </section>
      )}
      <section>
        <div className="section-heading">
          <div>
            <span className="eyebrow">BİR ŞEYLER KEŞFET</span>
            <h2>Yeni ilgi alanın burada olabilir.</h2>
          </div>
          <Link className="inline-link" to="/catalog">
            Tüm kurslar <ArrowRight size={17} />
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
            title="Yeni bilgiler için yer aç."
            text="Yayınlanan kurslar burada görünecek. Eğitmensen ilk kursunu paylaşabilirsin."
          >
            <Link
              className="button secondary"
              to={session && session.user.role !== 'STUDENT' ? '/instructor/new' : '/catalog'}
            >
              <Compass size={17} />{' '}
              {session && session.user.role !== 'STUDENT' ? 'Kurs oluştur' : 'Kataloğa göz at'}
            </Link>
          </Empty>
        )}
      </section>
      <div className="journey-note">
        <Sparkles size={24} />
        <div>
          <strong>Öğrenmek bir varış noktası değil, yolculuktur.</strong>
          <p>Bir ders, bir fikir, bir yeni başlangıç.</p>
        </div>
        <span>Bir adım ileri ↗</span>
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
  const endpoint = q
    ? `/courses/search?query=${encodeURIComponent(q)}`
    : category
      ? `/courses/category/${encodeURIComponent(category)}?`
      : sort === 'popular'
        ? '/courses/popular?'
        : '/courses?';
  const courses = useQuery({
    queryKey: ['catalog', q, category, sort, page],
    queryFn: () =>
      api<Page<Course>>(
        `${endpoint}${endpoint.endsWith('?') ? '' : '&'}page=${page}&size=9${sort === 'new' ? '&sort=createdAt,desc' : ''}`,
      ),
  });
  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    next.set(key, value);
    if (key !== 'page') next.delete('page');
    if (key === 'q') next.delete('category');
    if (key === 'category') next.delete('q');
    setParams(next);
  }
  function search(e: FormEvent) {
    e.preventDefault();
    update('q', query);
  }
  return (
    <>
      <PageTitle
        eyebrow="MERAK ET. KEŞFET. ÖĞREN."
        title="Sırada ne öğrenmek var?"
        description="Yeni bir beceriye, yeni bir bakış açısına yer aç."
      />
      <div className="catalog-toolbar">
        <form className="search-form" onSubmit={search}>
          <Search size={19} />
          <input
            aria-label="Kurs ara"
            placeholder="Öğrenmek istediğin konuyu ara…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button className="button primary small">Ara</button>
        </form>
        <select
          aria-label="Kursları sırala"
          value={sort}
          onChange={(e) => update('sort', e.target.value)}
        >
          <option value="new">En yeni kurslar</option>
          <option value="popular">En popüler kurslar</option>
        </select>
        <select
          aria-label="Kategori"
          value={category}
          onChange={(e) => update('category', e.target.value)}
        >
          <option value="">Tüm kategoriler</option>
          {categories.data?.map((c) => (
            <option value={c.id} key={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      {categories.error && (
        <ErrorBox error={categories.error} retry={() => void categories.refetch()} />
      )}
      <div className="results-heading">
        <span>
          {courses.data ? `${courses.data.totalElements} kurs` : 'Kurslar'} {q && `· “${q}”`}
          {category &&
            ` · ${categories.data?.find((c) => String(c.id) === category)?.name || 'Seçili kategori'}`}
        </span>
        {(q || category) && (
          <button
            className="text-button"
            onClick={() => {
              setParams({});
              setQuery('');
            }}
          >
            Filtreleri temizle
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
          title="Henüz bir kurs bulunamadı"
          text={
            q || category
              ? 'Başka bir arama yapabilir veya filtreleri temizleyebilirsin.'
              : 'Yeni kurslar yayınlandığında burada görebilirsin.'
          }
        />
      )}
    </>
  );
}
