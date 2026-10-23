import { useAuth } from '../auth';
import { VideoOperations } from './Platform';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, body, date } from '../api';
import { ErrorBox, Field, Loading, PageTitle, Submit } from '../ui';
import { AdminNavigation } from './Admin';
interface Ticket {
  id: string;
  subject: string;
  category: string;
  content: string;
  status: string;
  created_at: string;
  email?: string;
}
export default function Support({ admin = false }: { admin?: boolean }) {
  const client = useQueryClient();
  const [selected, setSelected] = useState<Ticket>();
  const q = useQuery({
    queryKey: ['support', admin],
    queryFn: () => api<Ticket[]>(admin ? '/users/admin/tickets' : '/users/me/tickets'),
  });
  const action = useMutation({
    mutationFn: (data: unknown) => api('/users/me/tickets', { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['support'] });
    },
  });
  return (
    <>
      <PageTitle
        title={admin ? 'Destek ve içerik şikâyetleri' : 'Destek taleplerim'}
        description="Teknik sorunları ve içerik şikâyetlerini buradan takip et."
      />
      {admin && <AdminNavigation />}
      {!admin && (
        <form
          className="panel"
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            action.mutate({
              subject: f.get('subject'),
              category: f.get('category'),
              content: f.get('content'),
              courseId: f.get('courseId') || null,
            });
          }}
        >
          <Field label="Konu">
            <input name="subject" required maxLength={200} />
          </Field>
          <Field label="Kategori">
            <select name="category">
              <option value="TECHNICAL">Teknik sorun</option>
              <option value="COURSE">Kurs hakkında</option>
              <option value="CONTENT_REPORT">İçerik şikâyeti</option>
              <option value="ACCOUNT">Hesap ve kişisel veriler</option>
            </select>
          </Field>
          <Field label="Kurs kimliği (varsa)">
            <input name="courseId" pattern="[0-9a-fA-F-]{36}" />
          </Field>
          <Field label="Açıklama">
            <textarea name="content" required maxLength={10000} rows={5} />
          </Field>
          <Submit busy={action.isPending}>Destek talebi aç</Submit>
          {action.error && <ErrorBox error={action.error} />}
        </form>
      )}
      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} />
      ) : (
        q.data?.map((t) => (
          <article className="panel" key={t.id}>
            <h2>
              <button className="text-button" onClick={() => setSelected(t)}>
                {t.subject}
              </button>
            </h2>
            <p>
              {t.status === 'OPEN' ? 'Açık' : 'Yanıtlandı / kapalı'} · {date(t.created_at)}{' '}
              {t.email}
            </p>
            {selected?.id === t.id && <TicketThread ticket={t} admin={admin} />}
          </article>
        ))
      )}
    </>
  );
}
function TicketThread({ ticket, admin }: { ticket: Ticket; admin: boolean }) {
  const client = useQueryClient();
  const path = `/users/me/tickets/${ticket.id}/replies`;
  const q = useQuery({
    queryKey: ['ticket', ticket.id],
    queryFn: () => api<{ id: string; staff: boolean; content: string; created_at: string }[]>(path),
  });
  const send = useMutation({
    mutationFn: (data: unknown) => api(path, { method: 'POST', body: body(data) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['ticket', ticket.id] });
      void client.invalidateQueries({ queryKey: ['support'] });
    },
  });
  return (
    <>
      <p className="pre-wrap">{ticket.content}</p>
      {q.error && <ErrorBox error={q.error} />}{' '}
      {q.data?.map((r) => (
        <article className="notice" key={r.id}>
          <b>{r.staff ? 'Destek ekibi' : 'Kullanıcı'}</b>
          <p className="pre-wrap">{r.content}</p>
          <small>{date(r.created_at)}</small>
        </article>
      ))}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          send.mutate({ content: f.get('content'), close: f.has('close') });
        }}
      >
        <Field label="Yanıt">
          <textarea name="content" required maxLength={10000} />
        </Field>
        {admin && (
          <label>
            <input type="checkbox" name="close" /> Talebi yanıtlayıp kapat
          </label>
        )}
        <Submit busy={send.isPending}>Yanıt gönder</Submit>
        {send.error && <ErrorBox error={send.error} />}
      </form>
    </>
  );
}
export function Operations() {
  const scope = useAuth().session?.user.adminScope || 'FULL';
  const client = useQueryClient();
  const failures = useQuery({
    queryKey: ['delivery-failures'],
    queryFn: () =>
      api<{ id: string; status: string; attempts: number; last_error: string }[]>(
        '/notifications/admin/failures',
      ),
  });
  const privacy = useQuery({
    queryKey: ['privacy-requests'],
    queryFn: () =>
      api<{ id: string; email: string; status: string; created_at: string; reason?: string }[]>(
        '/users/admin/privacy',
      ),
  });
  const retry = useMutation({
    mutationFn: (id: string) => api(`/notifications/admin/${id}/retry`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['delivery-failures'] });
    },
  });
  const review = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api(`/users/admin/privacy/${id}/review`, { method: 'POST', body: body({ reason }) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['privacy-requests'] });
    },
  });
  return (
    <>
      <PageTitle title="İşletim ve veri talepleri" />
      <AdminNavigation />
      {scope === 'FULL' && <VideoOperations />}
      <section className="panel">
        <h2>Gönderilemeyen bildirimler</h2>
        {failures.error && <ErrorBox error={failures.error} />}{' '}
        {failures.data?.map((f) => (
          <article key={f.id} className="notice">
            <p>
              {f.status} · {f.attempts} deneme · {f.last_error}
            </p>
            {f.status === 'FAILED' && (
              <button className="button secondary" onClick={() => retry.mutate(f.id)}>
                Yeniden dene
              </button>
            )}
          </article>
        ))}
        {retry.error && <ErrorBox error={retry.error} />}
      </section>
      <section className="panel">
        <h2>Hesap silme talepleri</h2>
        <p>
          İncelemeyi kaydetmek verilerin silindiği anlamına gelmez. Saklama ve silme prosedürünü
          uygulayıp yapılan işlemleri gerekçeye yazın.
        </p>
        {privacy.error && <ErrorBox error={privacy.error} />}{' '}
        {privacy.data?.map((p) => (
          <article className="notice" key={p.id}>
            <p>
              {p.email} · {p.status} · {date(p.created_at)}
            </p>
            {p.reason && <p>{p.reason}</p>}
            {p.status === 'OPEN' && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const f = new FormData(e.currentTarget);
                  review.mutate({ id: p.id, reason: String(f.get('reason')) });
                }}
              >
                <Field label="İşlem ve saklama gerekçesi">
                  <textarea name="reason" required maxLength={2000} />
                </Field>
                <Submit busy={review.isPending}>İncelemeyi kaydet</Submit>
              </form>
            )}
          </article>
        ))}
        {review.error && <ErrorBox error={review.error} />}
      </section>
    </>
  );
}
