import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowRight, BookOpen, MailCheck, Sparkles } from 'lucide-react';
import { api, body } from '../api';
import { useAuth } from '../auth';
import type { Auth, User } from '../types';
import { ErrorBox, Field, Loading, Submit, Success } from '../ui';

export function AuthPage({ register = false }: { register?: boolean }) {
  const { session, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const requested = location.state?.from;
  const next =
    typeof requested === 'string' && requested.startsWith('/') && !requested.startsWith('//')
      ? requested
      : '/';
  if (session) return <Navigate to={next} replace />;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = Object.fromEntries(new FormData(e.currentTarget));
    if (form.otp === '') delete form.otp;
    try {
      const auth = await api<Auth>(`/auth/${register ? 'register' : 'login'}`, {
        method: 'POST',
        body: body(form),
      });
      signIn(auth);
      navigate(next, { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout">
      <div className="auth-story">
        <span className="eyebrow">
          <Sparkles size={15} /> MERAKININ PEŞİNDEN GİT
        </span>
        <h1>
          Bir sonraki
          <br />
          adımın, <em>ileri.</em>
        </h1>
        <p>Yeni beceriler, yeni bakış açıları. Kendi hızında öğren, hedeflerine yaklaş.</p>
        <div className="auth-art">
          <BookOpen size={100} strokeWidth={1} />
          <div className="floating-tag">Her gün yeni bir başlangıç ↗</div>
        </div>
        <div className="story-footer">
          <span>01 — Keşfet</span>
          <span>02 — Öğren</span>
          <span>03 — Geliş</span>
        </div>
      </div>
      <section className="auth-form panel">
        <span className="eyebrow">İLERİ AKADEMİ</span>
        <h2>{register ? 'Yeni bir başlangıç yap.' : 'Yeniden hoş geldin.'}</h2>
        <p>
          {register
            ? 'Öğrenme yolculuğun burada başlıyor.'
            : 'Kaldığın yerden devam etmeye hazır mısın?'}
        </p>
        {error ? <ErrorBox error={error} /> : null}
        <form onSubmit={submit} key={String(register)}>
          {register && (
            <div className="form-grid">
              <Field label="Ad">
                <input name="firstName" required autoComplete="given-name" />
              </Field>
              <Field label="Soyad">
                <input name="lastName" required autoComplete="family-name" />
              </Field>
            </div>
          )}
          <Field label="E-posta adresi">
            <input
              name="email"
              type="email"
              required
              autoComplete="email"
              placeholder="sen@ornek.com"
            />
          </Field>
          <Field label="Şifre" hint={register ? 'En az 12 karakter kullanın.' : undefined}>
            <input
              name="password"
              type="password"
              minLength={register ? 12 : undefined}
              required
              autoComplete={register ? 'new-password' : 'current-password'}
            />
          </Field>
          {!register && (
            <>
              <Field label="Doğrulama kodu (etkinleştirdiysen)">
                <input
                  name="otp"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength={6}
                />
              </Field>
              <Link to="/forgot-password">Parolamı unuttum</Link>
            </>
          )}
          {register && (
            <Field label="Nasıl katılmak istersin?">
              <select name="role">
                <option value="STUDENT">Öğrenci — yeni şeyler öğrenmek istiyorum</option>
                <option value="INSTRUCTOR">Eğitmen — bilgimi paylaşmak istiyorum</option>
              </select>
            </Field>
          )}
          <Submit busy={busy}>{register ? 'Hesabımı oluştur' : 'Giriş yap'}</Submit>
        </form>
        <div className="auth-switch">
          {register ? 'Zaten hesabın var mı?' : 'Henüz hesabın yok mu?'}{' '}
          <Link to={register ? '/login' : '/register'} state={location.state}>
            {register ? 'Giriş yap' : 'Ücretsiz katıl'} <ArrowRight size={14} />
          </Link>
        </div>
      </section>
    </div>
  );
}
export function VerifyEmail() {
  const { session, updateUser } = useAuth();
  const [params] = useSearchParams();
  const token = params.get('token');
  const [error, setError] = useState<unknown>();
  const [done, setDone] = useState(false);
  const started = useRef<string | null>(null);
  useEffect(() => {
    if (token && started.current !== token) {
      started.current = token;
      setDone(false);
      setError(null);
      api(`/auth/verify-email?token=${encodeURIComponent(token)}`)
        .then(() => {
          setDone(true);
          if (session)
            void api<User>('/users/me')
              .then(updateUser)
              .catch(() => {});
        })
        .catch(setError);
    }
  }, [token, session, updateUser]);
  return (
    <section className="panel narrow centered">
      <MailCheck size={48} />
      <h1>E-posta doğrulama</h1>
      {!token ? (
        <ErrorBox
          error={new Error('Doğrulama bağlantısı eksik. E-postanızdaki bağlantıyı kullanın.')}
        />
      ) : error ? (
        <ErrorBox error={error} />
      ) : done ? (
        <Success>E-posta adresiniz doğrulandı. Hesabınıza giriş yapabilirsiniz.</Success>
      ) : (
        <Loading />
      )}
      <Link className="button primary" to="/login">
        Giriş yap
      </Link>
    </section>
  );
}

export function RecoverPassword({ reset = false }: { reset?: boolean }) {
  const [params] = useSearchParams();
  const [busy, setBusy] = useState(false),
    [done, setDone] = useState(false),
    [error, setError] = useState<unknown>();
  return (
    <section className="panel narrow">
      <h1>{reset ? 'Yeni parola belirle' : 'Parolamı unuttum'}</h1>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError(null);
          const data = new FormData(e.currentTarget);
          try {
            await api(reset ? '/auth/reset-password' : '/auth/forgot-password', {
              method: 'POST',
              body: body(
                reset
                  ? { token: params.get('token'), password: data.get('password') }
                  : { email: data.get('email') },
              ),
            });
            setDone(true);
          } catch (e) {
            setError(e);
          } finally {
            setBusy(false);
          }
        }}
      >
        {reset ? (
          <Field label="Yeni parola — en az 12 karakter">
            <input
              name="password"
              type="password"
              required
              minLength={12}
              maxLength={72}
              autoComplete="new-password"
            />
          </Field>
        ) : (
          <Field label="E-posta adresi">
            <input name="email" type="email" required autoComplete="email" />
          </Field>
        )}
        <Submit busy={busy}>{reset ? 'Parolayı yenile' : 'Sıfırlama bağlantısı gönder'}</Submit>
      </form>
      {done && (
        <Success>
          {reset
            ? 'Parolan yenilendi. Tekrar giriş yapabilirsin.'
            : 'Adres uygunsa sıfırlama bağlantısı e-posta ile gönderilecek.'}
        </Success>
      )}
      {!!error && <ErrorBox error={error} />}
      <Link to="/login">Giriş yap</Link>
    </section>
  );
}
