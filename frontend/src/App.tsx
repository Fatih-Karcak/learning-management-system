import { Component, lazy, Suspense, useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import {
  ArrowUpRight,
  Award,
  Bell,
  BookOpen,
  ChevronRight,
  Compass,
  CreditCard,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  PanelTop,
  Search,
  Settings,
  Shield,
  Sprout,
  Upload,
  X,
} from 'lucide-react';
import { useAuth } from './auth';
import { Avatar, Empty, ErrorBox, Loading } from './ui';
import type { Role } from './types';
import { AuthPage, VerifyEmail, RecoverPassword } from './pages/Auth';
import { Catalog, Dashboard, InstructorCourses } from './pages/Catalog';
const CourseDetail = lazy(() => import('./pages/CourseDetail'));
const MyLearning = lazy(() => import('./pages/Learning').then((m) => ({ default: m.MyLearning })));
const Learning = lazy(() => import('./pages/Learning').then((m) => ({ default: m.Learning })));
const Certificates = lazy(() =>
  import('./pages/Learning').then((m) => ({ default: m.Certificates })),
);
const Editor = lazy(() => import('./pages/Instructor').then((m) => ({ default: m.Editor })));
const Instructor = lazy(() =>
  import('./pages/Instructor').then((m) => ({ default: m.Instructor })),
);
const MediaLibrary = lazy(() =>
  import('./pages/Instructor').then((m) => ({ default: m.MediaLibrary })),
);
const AdminUsers = lazy(() => import('./pages/Account').then((m) => ({ default: m.AdminUsers })));
const AdminDashboard = lazy(() =>
  import('./pages/Admin').then((m) => ({ default: m.AdminDashboard })),
);
const AdminCourses = lazy(() => import('./pages/Admin').then((m) => ({ default: m.AdminCourses })));
const AdminCategories = lazy(() =>
  import('./pages/Admin').then((m) => ({ default: m.AdminCategories })),
);
const AdminEnrollments = lazy(() =>
  import('./pages/Admin').then((m) => ({ default: m.AdminEnrollments })),
);
const AdminAudit = lazy(() => import('./pages/Admin').then((m) => ({ default: m.AdminAudit })));
const Notifications = lazy(() =>
  import('./pages/Account').then((m) => ({ default: m.Notifications })),
);
const Payments = lazy(() => import('./pages/Account').then((m) => ({ default: m.Payments })));
const Profile = lazy(() => import('./pages/Account').then((m) => ({ default: m.Profile })));
const Checkout = lazy(() => import('./pages/Checkout'));

const Security = lazy(() => import('./pages/Security'));
const Support = lazy(() => import('./pages/Support'));
const Operations = lazy(() => import('./pages/Support').then((m) => ({ default: m.Operations })));
const StudyPlan = lazy(() => import('./pages/Platform').then((m) => ({ default: m.StudyPlan })));
const Wishlist = lazy(() => import('./pages/Platform').then((m) => ({ default: m.Wishlist })));
class Boundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  render() {
    return this.state.error ? (
      <main className="fatal">
        <ErrorBox error={new Error('Sayfa gösterilemiyor. Lütfen sayfayı yenileyin.')} />
        <button className="button primary" onClick={() => window.location.reload()}>
          Sayfayı yenile
        </button>
      </main>
    ) : (
      this.props.children
    );
  }
}
function Guard({ roles }: { roles?: Role[] }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session)
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  if (roles && !roles.includes(session.user.role))
    return (
      <Empty
        title="Bu sayfaya erişiminiz yok"
        text="Hesabınızın erişebildiği sayfalara menüden ulaşabilirsiniz."
      >
        <Link className="button primary" to="/">
          Ana sayfaya dön
        </Link>
      </Empty>
    );
  return <Outlet />;
}
function Layout() {
  const { session, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => {
    setOpen(false);
    window.scrollTo(0, 0);
  }, [location.pathname]);
  const user = session?.user;
  const teacher =
    user?.role === 'INSTRUCTOR' || (user?.role === 'ADMIN' && user.adminScope !== 'SUPPORT');
  const links = [
    ...(user
      ? [
          { to: '/', label: 'Genel bakış', icon: LayoutDashboard },
          { to: '/my-learning', label: 'Öğrenim alanım', icon: BookOpen },
          { to: '/study-plan', label: 'Çalışma planım', icon: LayoutDashboard },
          { to: '/wishlist', label: 'İstek listem', icon: Compass },
        ]
      : []),
    { to: '/catalog', label: 'Kursları keşfet', icon: Compass },
    { to: '/certificates', label: 'Sertifikalar', icon: Award },
  ];
  return (
    <div className="app-shell">
      {open && (
        <button
          className="sidebar-overlay"
          aria-label="Menüyü kapat"
          onClick={() => setOpen(false)}
        />
      )}
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <Link to="/" className="brand">
          <span className="brand-mark">
            <GraduationCap size={25} />
          </span>
          ileri<span className="brand-dot">.</span>
        </Link>
        <div className="sidebar-label">ÖĞRENME YOLCULUĞUN</div>
        <nav aria-label="Ana menü">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              end={to === '/'}
              key={to}
              to={to}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <Icon size={20} />
              <span>{label}</span>
              {to === '/catalog' && <span className="nav-new">KEŞFET</span>}
            </NavLink>
          ))}
        </nav>
        {teacher && (
          <>
            <div className="sidebar-label">EĞİTMEN STÜDYOSU</div>
            <nav aria-label="Eğitmen menüsü">
              <NavLink className="nav-link" to="/instructor">
                <PanelTop size={20} />
                Kurslarım
              </NavLink>
              <NavLink className="nav-link" to="/media">
                <Upload size={20} />
                Medya kitaplığı
              </NavLink>
            </nav>
          </>
        )}
        {user && (
          <>
            <div className="sidebar-label">HESABIM</div>
            <nav aria-label="Hesap menüsü">
              <NavLink className="nav-link" to="/payments">
                <CreditCard size={20} />
                Ödemeler
              </NavLink>
              <NavLink className="nav-link" to="/notifications">
                <Bell size={20} />
                Bildirimler
              </NavLink>
              <NavLink className="nav-link" to="/profile">
                <Settings size={20} />
                Hesap ayarları
              </NavLink>
              <NavLink className="nav-link" to="/security">
                <Shield size={20} />
                Güvenlik ve tercihler
              </NavLink>
              <NavLink className="nav-link" to="/support">
                <Bell size={20} />
                Destek
              </NavLink>
              {user.role === 'ADMIN' && (
                <NavLink className="nav-link" to="/admin">
                  <Shield size={20} />
                  Yönetici paneli
                </NavLink>
              )}
            </nav>
          </>
        )}
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <Sprout size={26} />
            <strong>
              Küçük adımlar.
              <br />
              Büyük değişimler.
            </strong>
            <p>Bugün kendine yeni bir şey kat.</p>
            <Link to="/catalog">
              Öğrenmeye başla <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="sidebar-footer">
            İleri Akademi <span>© {new Date().getFullYear()}</span>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="topbar-start">
            <button
              className="icon-button mobile-menu"
              onClick={() => setOpen(!open)}
              aria-label={open ? 'Menüyü kapat' : 'Menüyü aç'}
            >
              {open ? <X /> : <Menu />}
            </button>
            <span className="breadcrumb">
              İleri Akademi <ChevronRight size={14} />
              <b>
                {location.pathname.startsWith('/instructor')
                  ? 'Eğitmen stüdyosu'
                  : 'Öğrenme alanın'}
              </b>
            </span>
          </div>
          <div className="topbar-actions">
            <Link className="icon-button" to="/catalog" aria-label="Kurs ara">
              <Search size={20} />
            </Link>
            {user ? (
              <>
                <Link className="icon-button" to="/notifications" aria-label="Bildirimler">
                  <Bell size={20} />
                </Link>
                <span className="topbar-divider" />
                <Link className="user-menu" to="/profile">
                  <Avatar name={`${user.firstName} ${user.lastName}`} src={user.avatarUrl} />
                  <span>
                    <b>
                      {user.firstName} {user.lastName}
                    </b>
                    <small>
                      {user.role === 'STUDENT'
                        ? 'Öğrenci'
                        : user.role === 'ADMIN'
                          ? 'Yönetici'
                          : 'Eğitmen'}
                    </small>
                  </span>
                </Link>
                <button className="icon-button" onClick={signOut} aria-label="Çıkış yap">
                  <LogOut size={18} />
                </button>
              </>
            ) : (
              <>
                <Link className="text-button" to="/login">
                  Giriş yap
                </Link>
                <Link className="button primary small" to="/register">
                  Ücretsiz katıl <ArrowUpRight size={15} />
                </Link>
              </>
            )}
          </div>
        </header>
        <main id="main" className="page-content">
          {user && !user.emailVerified && (
            <div className="notice warning">
              E-posta adresinizi doğrulamak için gelen kutunuzdaki bağlantıyı kullanın.
            </div>
          )}
          <Suspense fallback={<Loading />}>
            <Outlet />
          </Suspense>
        </main>
        <footer className="main-footer">
          <span>Her gün bir adım ileri.</span>
          <Link to="/certificates">
            Sertifika doğrula <ArrowUpRight size={14} />
          </Link>
        </footer>
      </div>
    </div>
  );
}
export default function App() {
  return (
    <Boundary>
      <a className="skip-link" href="#main">
        İçeriğe geç
      </a>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="catalog" element={<Catalog />} />
          <Route path="courses/:courseId" element={<CourseDetail />} />
          <Route path="instructors/:instructorId" element={<InstructorCourses />} />
          <Route path="login" element={<AuthPage />} />
          <Route path="register" element={<AuthPage register />} />
          <Route path="forgot-password" element={<RecoverPassword />} />
          <Route path="reset-password" element={<RecoverPassword reset />} />
          <Route path="verify-email" element={<VerifyEmail />} />
          <Route path="certificates" element={<Certificates />} />
          <Route path="certificates/verify/:code" element={<Certificates />} />
          <Route element={<Guard />}>
            <Route path="my-learning" element={<MyLearning />} />
            <Route path="learn/:enrollmentId" element={<Learning />} />
            <Route path="checkout/:courseId" element={<Checkout />} />
            <Route path="payments" element={<Payments />} />
            <Route path="notifications" element={<Notifications />} />
            <Route path="profile" element={<Profile />} />
            <Route path="security" element={<Security />} />
            <Route path="support" element={<Support />} />
            <Route path="study-plan" element={<StudyPlan />} />
            <Route path="wishlist" element={<Wishlist />} />
          </Route>
          <Route element={<Guard roles={['INSTRUCTOR', 'ADMIN']} />}>
            <Route path="instructor" element={<Instructor />} />
            <Route path="instructor/new" element={<Editor />} />
            <Route path="instructor/:courseId" element={<Editor />} />
            <Route path="media" element={<MediaLibrary />} />
          </Route>
          <Route element={<Guard roles={['ADMIN']} />}>
            <Route path="admin" element={<AdminDashboard />} />
            <Route path="admin/users" element={<AdminUsers />} />
            <Route path="admin/courses" element={<AdminCourses />} />
            <Route path="admin/categories" element={<AdminCategories />} />
            <Route path="admin/enrollments" element={<AdminEnrollments />} />
            <Route path="admin/support" element={<Support admin />} />
            <Route path="admin/operations" element={<Operations />} />
            <Route path="admin/audit" element={<AdminAudit />} />
          </Route>
          <Route
            path="*"
            element={
              <Empty
                title="Bu sayfa bulunamadı"
                text="Bağlantı değişmiş olabilir. Öğrenme yolculuğuna ana sayfadan devam edebilirsin."
              >
                <Link className="button primary" to="/">
                  Ana sayfaya dön
                </Link>
              </Empty>
            }
          />
        </Route>
      </Routes>
    </Boundary>
  );
}
