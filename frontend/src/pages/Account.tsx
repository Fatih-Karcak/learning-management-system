import { Inbox } from './Platform';
import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bell, CreditCard, ExternalLink, Mail, Search, Shield, UserRound } from 'lucide-react';
import { api, body, date, money, safeUrl } from '../api';
import { useAuth } from '../auth';
import { AdminNavigation } from './Admin';
import type { Notification, Page, Payment, Refund, User } from '../types';
import {
  Avatar,
  Badge,
  Empty,
  ErrorBox,
  Field,
  Loading,
  Modal,
  PageTitle,
  Pagination,
  Submit,
  Success,
} from '../ui';

export function Profile() {
  const { session, updateUser, signOut } = useAuth();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const user = session!.user;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSaved(false);
    const data: Record<string, FormDataEntryValue> = Object.fromEntries(
      new FormData(e.currentTarget),
    );
    if (!data.newPassword) {
      delete data.newPassword;
      delete data.currentPassword;
    } else if (!data.currentPassword) {
      setError(new Error('Şifrenizi değiştirmek için mevcut şifrenizi yazın.'));
      setBusy(false);
      return;
    }
    try {
      const result = await api<User>('/users/me', { method: 'PUT', body: body(data) });
      updateUser(result);
      setSaved(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function deactivate() {
    if (
      !window.confirm(
        'Hesabınız devre dışı bırakılacak ve oturumunuz kapatılacak. Devam edilsin mi?',
      )
    )
      return;
    setDeactivating(true);
    setError(null);
    try {
      await api('/users/me', { method: 'DELETE' });
      signOut();
    } catch (e) {
      setError(e);
    } finally {
      setDeactivating(false);
    }
  }
  return (
    <>
      <PageTitle
        eyebrow="SANA AİT BİR ALAN"
        title="Hesap ayarları"
        description="Profilini güncelle, hesabını güvenle yönet."
      />
      <div className="profile-layout">
        <aside className="panel profile-summary">
          <Avatar name={`${user.firstName} ${user.lastName}`} src={user.avatarUrl} large />
          <h2>
            {user.firstName} {user.lastName}
          </h2>
          <p>{user.email}</p>
          <Badge value={user.status} />
          <p>
            {user.role === 'STUDENT'
              ? 'Öğrenci'
              : user.role === 'INSTRUCTOR'
                ? 'Eğitmen'
                : 'Yönetici'}{' '}
            · {date(user.createdAt)}
          </p>
          <div className="notice">
            <Mail size={18} />
            {user.emailVerified
              ? 'E-posta adresin doğrulandı.'
              : 'E-postandaki bağlantıdan adresini doğrula.'}
          </div>
        </aside>
        <div>
          <form className="panel" onSubmit={submit}>
            <h2>Profil bilgilerin</h2>
            <div className="form-grid">
              <Field label="Ad">
                <input
                  name="firstName"
                  required
                  defaultValue={user.firstName}
                  autoComplete="given-name"
                />
              </Field>
              <Field label="Soyad">
                <input
                  name="lastName"
                  required
                  defaultValue={user.lastName}
                  autoComplete="family-name"
                />
              </Field>
            </div>
            <Field label="Telefon">
              <input
                name="phoneNumber"
                type="tel"
                defaultValue={user.phoneNumber}
                autoComplete="tel"
              />
            </Field>
            <Field label="Hakkında">
              <textarea name="bio" rows={4} defaultValue={user.bio} />
            </Field>
            <Field label="Profil görseli adresi">
              <input
                name="avatarUrl"
                type="url"
                defaultValue={user.avatarUrl}
                placeholder="https://…"
              />
            </Field>
            <hr />
            <h3>Şifre değiştir</h3>
            <p className="muted">Şifreni değiştirmek istemiyorsan bu alanları boş bırak.</p>
            <div className="form-grid">
              <Field label="Mevcut şifre">
                <input name="currentPassword" type="password" autoComplete="current-password" />
              </Field>
              <Field label="Yeni şifre">
                <input
                  name="newPassword"
                  type="password"
                  minLength={8}
                  autoComplete="new-password"
                />
              </Field>
            </div>
            {!!error && <ErrorBox error={error} />}{' '}
            {saved && <Success>Profilin güncellendi.</Success>}
            <Submit busy={busy}>Değişiklikleri kaydet</Submit>
          </form>
          <section className="panel danger-zone">
            <h3>Hesabı devre dışı bırak</h3>
            <p>Hesabına erişimin kapanır ve oturumun sonlandırılır.</p>
            <button
              className="button danger-button"
              disabled={deactivating}
              onClick={() => void deactivate()}
            >
              Hesabımı devre dışı bırak
            </button>
          </section>
        </div>
      </div>
    </>
  );
}
export function Payments() {
  const [page, setPage] = useState(0);
  const [refundPayment, setRefundPayment] = useState<Payment>();
  const [selected, setSelected] = useState<string>();
  const payments = useQuery({
    queryKey: ['payments', page],
    queryFn: () => api<Page<Payment>>(`/payments/my?page=${page}&size=10&sort=createdAt,desc`),
  });
  const details = useQuery({
    queryKey: ['payment', selected],
    queryFn: () => api<Payment>(`/payments/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      <PageTitle
        eyebrow="ÖĞRENMEYE YAPTIĞIN YATIRIM"
        title="Ödemelerim"
        description="İşlemlerini incele, makbuzlarına ulaş ve iade talebi oluştur."
      />
      {payments.isPending ? (
        <Loading />
      ) : payments.error ? (
        <ErrorBox error={payments.error} retry={() => void payments.refetch()} />
      ) : payments.data?.content.length ? (
        <>
          <div className="panel table-panel">
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Kurs / İşlem</th>
                    <th>Tarih</th>
                    <th>Tutar</th>
                    <th>Durum</th>
                    <th>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.data.content.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <button className="text-button" onClick={() => setSelected(p.id)}>
                          {p.courseTitle}
                        </button>
                        <small className="table-sub">{p.id.slice(0, 8).toUpperCase()}</small>
                      </td>
                      <td>{date(p.createdAt)}</td>
                      <td>
                        <strong>{money(p.amount, p.currency)}</strong>
                      </td>
                      <td>
                        <Badge value={p.status} />
                      </td>
                      <td>
                        <div className="form-actions">
                          {safeUrl(p.receiptUrl) && (
                            <a
                              className="text-button"
                              href={safeUrl(p.receiptUrl)}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              Makbuz <ExternalLink size={14} />
                            </a>
                          )}
                          {p.status === 'COMPLETED' && (
                            <button className="text-button" onClick={() => setRefundPayment(p)}>
                              İade iste
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pagination page={page} pages={payments.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Henüz bir ödeme kaydın yok"
          text="Kurslar için yaptığın ödemeleri ve işlem durumlarını burada görebilirsin."
        >
          <Link to="/catalog" className="button primary">
            <CreditCard size={17} /> Kursları keşfet
          </Link>
        </Empty>
      )}
      {refundPayment && (
        <Modal title="İade talebi oluştur" onClose={() => setRefundPayment(undefined)}>
          <RefundForm payment={refundPayment} />
        </Modal>
      )}
      {selected && (
        <Modal title="Ödeme ayrıntısı" onClose={() => setSelected(undefined)}>
          {details.isPending ? (
            <Loading />
          ) : details.error ? (
            <ErrorBox error={details.error} />
          ) : (
            details.data && (
              <>
                <h3>{details.data.courseTitle}</h3>
                <p className="amount">{money(details.data.amount, details.data.currency)}</p>
                <Badge value={details.data.status} />
                <p>{date(details.data.createdAt)}</p>
                <Link className="button secondary" to={`/courses/${details.data.courseId}`}>
                  Kursu görüntüle
                </Link>
              </>
            )
          )}
        </Modal>
      )}
    </>
  );
}
function RefundForm({ payment }: { payment: Payment }) {
  const client = useQueryClient();
  const refund = useMutation({
    mutationFn: (reason: string) =>
      api<Refund>('/payments/refund', {
        method: 'POST',
        body: body({ paymentId: payment.id, reason }),
      }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['payments'] }),
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    refund.mutate(String(new FormData(e.currentTarget).get('reason')).trim());
  }
  return refund.isSuccess ? (
    <>
      <Success>İade talebin alındı.</Success>
      <Badge value={refund.data.status} />
      <p>{money(refund.data.amount, payment.currency)}</p>
      <p>İşlem sonucunu ödemeler sayfasından takip edebilirsin.</p>
    </>
  ) : (
    <form onSubmit={submit}>
      <h3>{payment.courseTitle}</h3>
      <p>İşlem tutarı: {money(payment.amount, payment.currency)}</p>
      <Field label="İade nedeni">
        <textarea name="reason" required minLength={3} rows={4} />
      </Field>
      <p className="muted">
        İade, sağlayıcının koşullarına göre değerlendirilir. İade edilen kurslara erişim sona erer.
      </p>
      {refund.error && <ErrorBox error={refund.error} />}
      <Submit busy={refund.isPending}>İade talebini gönder</Submit>
    </form>
  );
}
export function Notifications() {
  const [page, setPage] = useState(0);
  const items = useQuery({
    queryKey: ['notifications', page],
    queryFn: () =>
      api<Page<Notification>>(`/notifications/my?page=${page}&size=12&sort=createdAt,desc`),
  });
  return (
    <>
      <PageTitle
        eyebrow="YOLCULUĞUNDAN HABERLER"
        title="Bildirimler"
        description="Eğitimlerin, ödemelerin ve hesabınla ilgili e-posta bildirimleri."
      />
      <Inbox />
      {items.isPending ? (
        <Loading />
      ) : items.error ? (
        <ErrorBox error={items.error} retry={() => void items.refetch()} />
      ) : items.data?.content.length ? (
        <>
          <div className="panel notification-list">
            {items.data.content.map((n) => (
              <article className="notification" key={n.id}>
                <span className="stat-icon">
                  <Bell size={20} />
                </span>
                <div>
                  <h3>{n.subject}</h3>
                  <p>{date(n.createdAt)}</p>
                </div>
                <Badge value={n.status} />
              </article>
            ))}
          </div>
          <Pagination page={page} pages={items.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Şimdilik her şey sakin."
          text="Yeni bildirimlerin burada görünecek. Öğrenmeye devam et."
        />
      )}
    </>
  );
}
export function AdminUsers() {
  const { session } = useAuth();
  const client = useQueryClient();
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string>();
  const users = useQuery({
    queryKey: ['users', search, page],
    queryFn: () =>
      api<Page<User>>(`/users/search?query=${encodeURIComponent(search)}&page=${page}&size=12`),
  });
  const details = useQuery({
    queryKey: ['user', selected],
    queryFn: () => api<User>(`/users/${selected}`),
    enabled: !!selected,
  });
  const update = useMutation({
    mutationFn: async (data: unknown) => {
      const input = data as { role: string; scope?: string; reason: string };
      const { scope, ...account } = input;
      await api(`/users/admin/${selected}`, { method: 'PUT', body: body(account) });
      if (input.role === 'ADMIN' && input.scope)
        await api(`/users/admin/${selected}/scope`, {
          method: 'PUT',
          body: body({ scope: input.scope, reason: input.reason }),
        });
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['users'] });
      void client.invalidateQueries({ queryKey: ['user', selected] });
      void client.invalidateQueries({ queryKey: ['admin-summary'] });
    },
  });
  return (
    <>
      <PageTitle
        eyebrow="YÖNETİCİ ALANI"
        title="Kullanıcılar"
        description="Kullanıcıları ara; rol, erişim durumu ve yönetici işlemlerini yönet."
      />
      <AdminNavigation />
      <form
        className="search-form admin-search"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(query);
          setPage(0);
        }}
      >
        <Search size={19} />
        <input
          aria-label="Kullanıcı ara"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ad veya e-posta ile ara"
        />
        <button className="button primary small">Ara</button>
      </form>
      {users.isPending ? (
        <Loading />
      ) : users.error ? (
        <ErrorBox error={users.error} />
      ) : users.data?.content.length ? (
        <>
          <div className="panel table-panel">
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Kullanıcı</th>
                    <th>Rol</th>
                    <th>Durum</th>
                    <th>Kayıt tarihi</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <strong>
                          {u.firstName} {u.lastName}
                        </strong>
                        <small className="table-sub">{u.email}</small>
                      </td>
                      <td>
                        {u.role === 'STUDENT'
                          ? 'Öğrenci'
                          : u.role === 'INSTRUCTOR'
                            ? 'Eğitmen'
                            : 'Yönetici'}
                      </td>
                      <td>
                        <Badge value={u.status} />
                      </td>
                      <td>{date(u.createdAt)}</td>
                      <td>
                        <button className="text-button" onClick={() => setSelected(u.id)}>
                          İncele
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pagination page={page} pages={users.data.totalPages} onChange={setPage} />
        </>
      ) : (
        <Empty
          title="Kullanıcı bulunamadı"
          text="Farklı bir ad veya e-posta ile tekrar arayabilirsin."
        />
      )}
      {selected && (
        <Modal title="Kullanıcı bilgileri" onClose={() => setSelected(undefined)}>
          {details.isPending ? (
            <Loading />
          ) : details.error ? (
            <ErrorBox error={details.error} />
          ) : (
            details.data && (
              <div className="user-details">
                <UserRound size={38} />
                <h3>
                  {details.data.firstName} {details.data.lastName}
                </h3>
                <p>{details.data.email}</p>
                <Badge value={details.data.status} />
                <p>{details.data.bio || 'Profil açıklaması bulunmuyor.'}</p>
                <p>{details.data.phoneNumber}</p>
                <p>
                  <Shield size={16} />{' '}
                  {details.data.emailVerified ? 'E-posta doğrulandı' : 'E-posta doğrulanmadı'}
                </p>
                {selected !== session?.user.id && (
                  <form
                    key={`${selected}-${details.data.role}-${details.data.status}`}
                    onSubmit={(e) => {
                      e.preventDefault();
                      if (window.confirm('Kullanıcının rolü ve erişim durumu güncellensin mi?'))
                        update.mutate(Object.fromEntries(new FormData(e.currentTarget)));
                    }}
                  >
                    <Field label="Kullanıcı rolü">
                      <select name="role" defaultValue={details.data.role}>
                        <option value="STUDENT">Öğrenci</option>
                        <option value="INSTRUCTOR">Eğitmen</option>
                        <option value="ADMIN">Yönetici</option>
                      </select>
                    </Field>
                    <Field label="Yönetici kapsamı (yalnızca yönetici rolünde uygulanır)">
                      <select name="scope" defaultValue={details.data.adminScope || 'FULL'}>
                        <option value="FULL">Tam yönetim</option>
                        <option value="MODERATION">Kurs ve içerik moderasyonu</option>
                        <option value="SUPPORT">Destek ve kayıt işlemleri</option>
                      </select>
                    </Field>
                    <Field label="Hesap durumu">
                      <select name="status" defaultValue={details.data.status}>
                        <option value="ACTIVE">Aktif</option>
                        <option value="INACTIVE">Pasif</option>
                        <option value="BANNED">Yasaklandı</option>
                        <option value="PENDING_VERIFICATION">E-posta doğrulaması bekliyor</option>
                      </select>
                    </Field>
                    <Field label="Kullanıcı değişikliği gerekçesi">
                      <textarea name="reason" minLength={3} maxLength={1000} required />
                    </Field>
                    {update.error && <ErrorBox error={update.error} />}{' '}
                    {update.isSuccess && (
                      <Success>
                        Kullanıcı güncellendi. Eski yetkiler sonraki istekte geçersizleşir.
                      </Success>
                    )}
                    <Submit busy={update.isPending}>Kullanıcı yetkilerini kaydet</Submit>
                  </form>
                )}
              </div>
            )
          )}
        </Modal>
      )}
    </>
  );
}
