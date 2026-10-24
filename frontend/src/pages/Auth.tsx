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
          <Sparkles size={15} /> FOLLOW YOUR CURIOSITY
        </span>
        <h1>
          Your next
          <br />
          step <em>starts here.</em>
        </h1>
        <p>New skills and new perspectives. Learn at your own pace and move toward your goals.</p>
        <div className="auth-art">
          <BookOpen size={100} strokeWidth={1} />
          <div className="floating-tag">Every day is a new beginning ↗</div>
        </div>
        <div className="story-footer">
          <span>01 — Explore</span>
          <span>02 — Learn</span>
          <span>03 — Grow</span>
        </div>
      </div>
      <section className="auth-form panel">
        <span className="eyebrow">ILERI ACADEMY</span>
        <h2>{register ? 'Make a new beginning.' : 'Welcome back.'}</h2>
        <p>
          {register ? 'Your learning journey starts here.' : 'Ready to pick up where you left off?'}
        </p>
        {error ? <ErrorBox error={error} /> : null}
        <form onSubmit={submit} key={String(register)}>
          {register && (
            <div className="form-grid">
              <Field label="First name">
                <input name="firstName" required autoComplete="given-name" />
              </Field>
              <Field label="Last name">
                <input name="lastName" required autoComplete="family-name" />
              </Field>
            </div>
          )}
          <Field label="Email address">
            <input
              name="email"
              type="email"
              required
              autoComplete="email"
              placeholder="you@example.com"
            />
          </Field>
          <Field label="Password" hint={register ? 'Use at least 12 characters.' : undefined}>
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
              <Field label="Verification code (if enabled)">
                <input
                  name="otp"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength={6}
                />
              </Field>
              <Link to="/forgot-password">Forgot password</Link>
            </>
          )}
          {register && (
            <Field label="How would you like to join?">
              <select name="role">
                <option value="STUDENT">Learner — I want to learn something new</option>
                <option value="INSTRUCTOR">Instructor — I want to share my knowledge</option>
              </select>
            </Field>
          )}
          <Submit busy={busy}>{register ? 'Create my account' : 'Sign in'}</Submit>
        </form>
        <div className="auth-switch">
          {register ? 'Already have an account?' : 'New here?'}{' '}
          <Link to={register ? '/login' : '/register'} state={location.state}>
            {register ? 'Sign in' : 'Join for free'} <ArrowRight size={14} />
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
      <h1>Email verification</h1>
      {!token ? (
        <ErrorBox
          error={new Error('The verification link is missing. Use the link in your email.')}
        />
      ) : error ? (
        <ErrorBox error={error} />
      ) : done ? (
        <Success>Your email address has been verified. You can sign in.</Success>
      ) : (
        <Loading />
      )}
      <Link className="button primary" to="/login">
        Sign in
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
      <h1>{reset ? 'Set a new password' : 'Forgot password'}</h1>
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
          <Field label="New password — at least 12 characters">
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
          <Field label="Email address">
            <input name="email" type="email" required autoComplete="email" />
          </Field>
        )}
        <Submit busy={busy}>{reset ? 'Reset password' : 'Send reset link'}</Submit>
      </form>
      {done && (
        <Success>
          {reset
            ? 'Your password has been reset. You can sign in again.'
            : 'If the address is eligible, a reset link will be sent by email.'}
        </Success>
      )}
      {!!error && <ErrorBox error={error} />}
      <Link to="/login">Sign in</Link>
    </section>
  );
}
