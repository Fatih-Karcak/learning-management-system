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
      link.download = 'ileri-kisisel-verilerim.json';
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
        title="Güvenlik ve tercihler"
        description="Oturumlarını, doğrulamanı ve kişisel verilerini yönet."
      />
      {change.error && <ErrorBox error={change.error} />}{' '}
      {change.isSuccess && <Success>İşlem kaydedildi.</Success>}
      <section className="panel">
        <h2>Çalışma ve bildirim tercihleri</h2>
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
            <Field label="Haftalık video çalışma hedefi (dakika)">
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
              Öğrenmeyle ilgili e-postalar
            </label>
            <label className="checkbox-field">
              <input
                name="announcements"
                type="checkbox"
                defaultChecked={preferences.data?.announcements}
              />{' '}
              Kurs duyurularını e-posta ile al
            </label>
            <p className="muted">Güvenlik e-postaları bu tercihlerden bağımsız gönderilir.</p>
            <Submit busy={change.isPending}>Tercihleri kaydet</Submit>
          </form>
        )}
      </section>
      <section className="panel">
        <h2>İki aşamalı doğrulama</h2>
        <p>Doğrulama uygulamandaki tek kullanımlık kod, girişte parolana ek olarak istenir.</p>
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
            <p className="notice success">İki aşamalı doğrulama açık.</p>
            <Field label="Uygulamadaki doğrulama kodu">
              <input
                name="code"
                required
                pattern="[0-9]{6}"
                inputMode="numeric"
                autoComplete="one-time-code"
              />
            </Field>
            <Submit busy={change.isPending}>Doğrulamayı kapat ve çıkış yap</Submit>
          </form>
        ) : setup ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              change.mutate({ path: '/users/me/mfa/confirm', data: { code: f.get('code') } });
            }}
          >
            <p>Doğrulama uygulamana aşağıdaki anahtarı ekle. Anahtarı güvenli bir yerde sakla.</p>
            <code className="mfa-secret">{setup.secret}</code>
            <Field label="Uygulamanın ürettiği kod">
              <input
                name="code"
                required
                pattern="[0-9]{6}"
                inputMode="numeric"
                autoComplete="one-time-code"
              />
            </Field>
            <Submit busy={change.isPending}>Doğrulamayı etkinleştir</Submit>
          </form>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              enroll.mutate(String(f.get('password')));
            }}
          >
            <Field label="Mevcut parolan">
              <input name="password" type="password" autoComplete="current-password" required />
            </Field>
            <Submit busy={enroll.isPending}>Doğrulama kurulumu başlat</Submit>
          </form>
        )}
        {enroll.error && <ErrorBox error={enroll.error} />}
      </section>
      <section className="panel">
        <h2>Açık oturumlar</h2>
        {sessions.error && <ErrorBox error={sessions.error} />}{' '}
        {sessions.data?.map((s) => (
          <article key={s.id} className="lesson-row">
            <span>
              {date(s.created_at)} tarihinde açıldı · Son yenileme {date(s.last_seen_at)}
            </span>
            <button
              className="button secondary small"
              disabled={change.isPending}
              onClick={() =>
                change.mutate({ path: `/users/me/sessions/${s.id}`, method: 'DELETE' })
              }
            >
              Oturumu kapat
            </button>
          </article>
        ))}
        <button
          className="text-button danger"
          onClick={() =>
            change.mutate({ path: '/users/me/sessions', method: 'DELETE' }, { onSuccess: signOut })
          }
        >
          Tüm oturumları kapat
        </button>
      </section>
      {!session?.user.emailVerified && (
        <section className="panel">
          <h2>E-posta doğrulama</h2>
          <button
            className="button secondary"
            onClick={() =>
              change.mutate({
                path: '/auth/resend-verification',
                data: { email: session?.user.email },
              })
            }
          >
            Doğrulama bağlantısını yeniden gönder
          </button>
        </section>
      )}
      <section className="panel">
        <h2>Kişisel veriler</h2>
        <button className="button secondary" disabled={exporting} onClick={() => void download()}>
          {exporting ? 'Veriler hazırlanıyor…' : 'Verilerimi JSON olarak indir'}
        </button>
        {!!exportError && <ErrorBox error={exportError} />}
        <p>
          Hesap silme talebi hesabını hemen kapatır. Destek ekibi, saklama zorunluluklarını ve kurs
          sahipliğini kontrol ederek kişisel verilerini silme sürecini yürütür.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            if (
              window.confirm(
                'Hesabın kapatılacak ve açık oturumların sonlandırılacak. Silme talebi gönderilsin mi?',
              )
            )
              change.mutate(
                { path: '/users/me/erasure', data: { password: f.get('password') } },
                { onSuccess: signOut },
              );
          }}
        >
          <Field label="Silme talebi için mevcut parolan">
            <input name="password" required type="password" autoComplete="current-password" />
          </Field>
          <Submit busy={change.isPending}>Hesabımı kapat ve silme talebi oluştur</Submit>
        </form>
        <Link to="/support">Destek ekibine ulaş</Link>
      </section>
    </>
  );
}
