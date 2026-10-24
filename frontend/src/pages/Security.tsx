import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, body, date } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Field, Loading, PageTitle, Submit, Success } from '../ui';
interface Preferences {
  learningEmails: boolean;
  announcements: boolean;
  weeklyTargetMinutes: number;
}
export default function Security() {
  const client = useQueryClient();
  const { session, signOut } = useAuth();
  const [setup, setSetup] = useState<{ secret: string; uri: string }>();
  const [exporting, setExporting] = useState(false),
    [exportError, setExportError] = useState<unknown>();
  const preferences = useQuery({
    queryKey: ['preferences'],
    queryFn: () => api<Preferences>('/users/me/preferences'),
  });
  const sessions = useQuery({
    queryKey: ['sessions'],
    queryFn: () =>
      api<{ id: string; created_at: string; last_seen_at: string; expires_at: string }[]>(
        '/users/me/sessions',
      ),
  });
  const mfa = useQuery({
    queryKey: ['mfa'],
    queryFn: () => api<{ enabled: boolean }>('/users/me/mfa'),
  });
  const change = useMutation({
    mutationFn: ({
      path,
      data,
      method = 'POST',
    }: {
      path: string;
      data?: unknown;
      method?: string;
    }) => api(path, { method, body: data === undefined ? undefined : body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['preferences'] });
      void client.invalidateQueries({ queryKey: ['mfa'] });
      void client.invalidateQueries({ queryKey: ['sessions'] });
      setSetup(undefined);
    },
  });
  const enroll = useMutation({
    mutationFn: (password: string) =>
      api<{ secret: string; uri: string }>('/users/me/mfa/setup', {
        method: 'POST',
        body: body({ password }),
      }),
    onSuccess: setSetup,
  });
  async function download() {
    setExporting(true);
    setExportError(null);
    try {
      const [account, learning, courses, notifications] = await Promise.all([
        api('/users/me/export'),
        api('/enrollments/learning/export'),
        api('/courses/learning/export'),
        api('/notifications/inbox'),
      ]);
      const blob = new Blob(
        [
          JSON.stringify(
            { exportedAt: new Date().toISOString(), account, learning, courses, notifications },
            null,
            2,
          ),
        ],
        { type: 'application/json' },
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'personal-data.json';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (e) {
      setExportError(e);
    } finally {
      setExporting(false);
    }
  }
  return (
    <>
      <PageTitle
        title="Security and preferences"
        description="Manage your sessions, verification and personal data."
      />
      {change.error && <ErrorBox error={change.error} />}{' '}
      {change.isSuccess && <Success>Changes saved.</Success>}
      <section className="panel">
        <h2>Learning and notification preferences</h2>
        {preferences.isPending ? (
          <Loading />
        ) : preferences.error ? (
          <ErrorBox error={preferences.error} />
        ) : (
          <form
            key={JSON.stringify(preferences.data)}
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              change.mutate({
                path: '/users/me/preferences',
                method: 'PUT',
                data: {
                  learningEmails: f.has('learningEmails'),
                  announcements: f.has('announcements'),
                  weeklyTargetMinutes: Number(f.get('target')),
                },
              });
            }}
          >
            <Field label="Weekly video study target (minutes)">
              <input
                name="target"
                type="number"
                required
                min={10}
                max={3000}
                defaultValue={preferences.data?.weeklyTargetMinutes}
              />
            </Field>
            <label className="checkbox-field">
              <input
                name="learningEmails"
                type="checkbox"
                defaultChecked={preferences.data?.learningEmails}
              />{' '}
              Learning emails
            </label>
            <label className="checkbox-field">
              <input
                name="announcements"
                type="checkbox"
                defaultChecked={preferences.data?.announcements}
              />{' '}
              Receive course announcements by email
            </label>
            <p className="muted">Security emails are sent independently of these preferences.</p>
            <Submit busy={change.isPending}>Save preferences</Submit>
          </form>
        )}
      </section>
      <section className="panel">
        <h2>Two-factor authentication</h2>
        <p>Enter a one-time code from your authenticator app when signing in.</p>
        {mfa.error && <ErrorBox error={mfa.error} />}{' '}
        {mfa.data?.enabled ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              change.mutate(
                { path: '/users/me/mfa', method: 'DELETE', data: { code: f.get('code') } },
                { onSuccess: signOut },
              );
            }}
          >
            <p className="notice success">Two-factor authentication is enabled.</p>
            <Field label="Code from your authenticator app">
              <input
                name="code"
                required
                pattern="[0-9]{6}"
                inputMode="numeric"
                autoComplete="one-time-code"
              />
            </Field>
            <Submit busy={change.isPending}>Disable two-factor authentication and sign out</Submit>
          </form>
        ) : setup ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              change.mutate({ path: '/users/me/mfa/confirm', data: { code: f.get('code') } });
            }}
          >
            <p>Add the key below to your authenticator app. Keep a secure copy of the key.</p>
            <code className="mfa-secret">{setup.secret}</code>
            <Field label="Code generated by your app">
              <input
                name="code"
                required
                pattern="[0-9]{6}"
                inputMode="numeric"
                autoComplete="one-time-code"
              />
            </Field>
            <Submit busy={change.isPending}>Enable two-factor authentication</Submit>
          </form>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              enroll.mutate(String(f.get('password')));
            }}
          >
            <Field label="Current password">
              <input name="password" type="password" autoComplete="current-password" required />
            </Field>
            <Submit busy={enroll.isPending}>Set up two-factor authentication</Submit>
          </form>
        )}
        {enroll.error && <ErrorBox error={enroll.error} />}
      </section>
      <section className="panel">
        <h2>Active sessions</h2>
        {sessions.error && <ErrorBox error={sessions.error} />}{' '}
        {sessions.data?.map((s) => (
          <article key={s.id} className="lesson-row">
            <span>
              {date(s.created_at)} started · Last refreshed {date(s.last_seen_at)}
            </span>
            <button
              className="button secondary small"
              disabled={change.isPending}
              onClick={() =>
                change.mutate({ path: `/users/me/sessions/${s.id}`, method: 'DELETE' })
              }
            >
              End session
            </button>
          </article>
        ))}
        <button
          className="text-button danger"
          onClick={() =>
            change.mutate({ path: '/users/me/sessions', method: 'DELETE' }, { onSuccess: signOut })
          }
        >
          Sign out of all sessions
        </button>
      </section>
      {!session?.user.emailVerified && (
        <section className="panel">
          <h2>Email verification</h2>
          <button
            className="button secondary"
            onClick={() =>
              change.mutate({
                path: '/auth/resend-verification',
                data: { email: session?.user.email },
              })
            }
          >
            Resend verification link
          </button>
        </section>
      )}
      <section className="panel">
        <h2>Personal data</h2>
        <button className="button secondary" disabled={exporting} onClick={() => void download()}>
          {exporting ? 'Preparing your data…' : 'Download my data as JSON'}
        </button>
        {!!exportError && <ErrorBox error={exportError} />}
        <p>
          An erasure request immediately closes your account. The support team reviews retention
          requirements and course ownership before processing personal data erasure.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            if (
              window.confirm(
                'Your account will close and active sessions will end. Submit an erasure request?',
              )
            )
              change.mutate(
                { path: '/users/me/erasure', data: { password: f.get('password') } },
                { onSuccess: signOut },
              );
          }}
        >
          <Field label="Current password to request erasure">
            <input name="password" required type="password" autoComplete="current-password" />
          </Field>
          <Submit busy={change.isPending}>Close my account and request erasure</Submit>
        </form>
        <Link to="/support">Contact support</Link>
      </section>
    </>
  );
}
