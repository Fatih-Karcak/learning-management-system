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
      setError(new Error('Enter your current password to change your password.'));
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
    if (!window.confirm('Your account will be deactivated and you will be signed out. Continue?'))
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
        eyebrow="YOUR PERSONAL SPACE"
        title="Account settings"
        description="Update your profile and manage your account securely."
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
              ? 'Learner'
              : user.role === 'INSTRUCTOR'
                ? 'Instructor'
                : 'Administrator'}{' '}
            · {date(user.createdAt)}
          </p>
          <div className="notice">
            <Mail size={18} />
            {user.emailVerified
              ? 'Your email address is verified.'
              : 'Verify your email address using the link in your inbox.'}
          </div>
        </aside>
        <div>
          <form className="panel" onSubmit={submit}>
            <h2>Your profile</h2>
            <div className="form-grid">
              <Field label="First name">
                <input
                  name="firstName"
                  required
                  defaultValue={user.firstName}
                  autoComplete="given-name"
                />
              </Field>
              <Field label="Last name">
                <input
                  name="lastName"
                  required
                  defaultValue={user.lastName}
                  autoComplete="family-name"
                />
              </Field>
            </div>
            <Field label="Phone number">
              <input
                name="phoneNumber"
                type="tel"
                defaultValue={user.phoneNumber}
                autoComplete="tel"
              />
            </Field>
            <Field label="About you">
              <textarea name="bio" rows={4} defaultValue={user.bio} />
            </Field>
            <Field label="Profile image URL">
              <input
                name="avatarUrl"
                type="url"
                defaultValue={user.avatarUrl}
                placeholder="https://…"
              />
            </Field>
            <hr />
            <h3>Change password</h3>
            <p className="muted">Leave these fields blank to keep your current password.</p>
            <div className="form-grid">
              <Field label="Current password">
                <input name="currentPassword" type="password" autoComplete="current-password" />
              </Field>
              <Field label="New password">
                <input
                  name="newPassword"
                  type="password"
                  minLength={8}
                  autoComplete="new-password"
                />
              </Field>
            </div>
            {!!error && <ErrorBox error={error} />}{' '}
            {saved && <Success>Your profile has been updated.</Success>}
            <Submit busy={busy}>Save changes</Submit>
          </form>
          <section className="panel danger-zone">
            <h3>Deactivate account</h3>
            <p>Your account access and active sessions will end.</p>
            <button
              className="button danger-button"
              disabled={deactivating}
              onClick={() => void deactivate()}
            >
              Deactivate my account
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
        eyebrow="YOUR INVESTMENT IN LEARNING"
        title="My payments"
        description="Review transactions, access receipts and request a refund."
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
                    <th>Course / Transaction</th>
                    <th>Date</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Actions</th>
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
                              Receipt <ExternalLink size={14} />
                            </a>
                          )}
                          {p.status === 'COMPLETED' && (
                            <button className="text-button" onClick={() => setRefundPayment(p)}>
                              Request refund
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
          title="You have no payments yet"
          text="Your course payments and transaction status will appear here."
        >
          <Link to="/catalog" className="button primary">
            <CreditCard size={17} /> Explore courses
          </Link>
        </Empty>
      )}
      {refundPayment && (
        <Modal title="Create refund request" onClose={() => setRefundPayment(undefined)}>
          <RefundForm payment={refundPayment} />
        </Modal>
      )}
      {selected && (
        <Modal title="Payment details" onClose={() => setSelected(undefined)}>
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
                  View course
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
      <Success>Your refund request has been received.</Success>
      <Badge value={refund.data.status} />
      <p>{money(refund.data.amount, payment.currency)}</p>
      <p>Track the result on the payments page.</p>
    </>
  ) : (
    <form onSubmit={submit}>
      <h3>{payment.courseTitle}</h3>
      <p>Transaction amount: {money(payment.amount, payment.currency)}</p>
      <Field label="Reason for refund">
        <textarea name="reason" required minLength={3} rows={4} />
      </Field>
      <p className="muted">
        Refunds are reviewed under the provider's terms. Access to refunded courses ends.
      </p>
      {refund.error && <ErrorBox error={refund.error} />}
      <Submit busy={refund.isPending}>Submit refund request</Submit>
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
        eyebrow="NEWS FROM YOUR LEARNING JOURNEY"
        title="Notifications"
        description="Email notifications about your courses, payments and account."
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
          title="You are all caught up."
          text="New notifications will appear here. Keep learning."
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
        eyebrow="ADMINISTRATION"
        title="Users"
        description="Search users and manage roles, access and administrative actions."
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
          aria-label="Search users"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name or email"
        />
        <button className="button primary small">Search</button>
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
                    <th>User</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Joined</th>
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
                          ? 'Learner'
                          : u.role === 'INSTRUCTOR'
                            ? 'Instructor'
                            : 'Administrator'}
                      </td>
                      <td>
                        <Badge value={u.status} />
                      </td>
                      <td>{date(u.createdAt)}</td>
                      <td>
                        <button className="text-button" onClick={() => setSelected(u.id)}>
                          Review
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
        <Empty title="No users found" text="Try another name or email address." />
      )}
      {selected && (
        <Modal title="User details" onClose={() => setSelected(undefined)}>
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
                <p>{details.data.bio || 'No profile description available.'}</p>
                <p>{details.data.phoneNumber}</p>
                <p>
                  <Shield size={16} />{' '}
                  {details.data.emailVerified ? 'Email verified' : 'Email not verified'}
                </p>
                {selected !== session?.user.id && (
                  <form
                    key={`${selected}-${details.data.role}-${details.data.status}`}
                    onSubmit={(e) => {
                      e.preventDefault();
                      if (window.confirm("Update this user's role and access status?"))
                        update.mutate(Object.fromEntries(new FormData(e.currentTarget)));
                    }}
                  >
                    <Field label="User role">
                      <select name="role" defaultValue={details.data.role}>
                        <option value="STUDENT">Learner</option>
                        <option value="INSTRUCTOR">Instructor</option>
                        <option value="ADMIN">Administrator</option>
                      </select>
                    </Field>
                    <Field label="Administrator scope (applies only to administrators)">
                      <select name="scope" defaultValue={details.data.adminScope || 'FULL'}>
                        <option value="FULL">Full administration</option>
                        <option value="MODERATION">Course and content moderation</option>
                        <option value="SUPPORT">Support and enrollment management</option>
                      </select>
                    </Field>
                    <Field label="Account status">
                      <select name="status" defaultValue={details.data.status}>
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                        <option value="BANNED">Banned</option>
                        <option value="PENDING_VERIFICATION">Awaiting email verification</option>
                      </select>
                    </Field>
                    <Field label="Reason for user change">
                      <textarea name="reason" minLength={3} maxLength={1000} required />
                    </Field>
                    {update.error && <ErrorBox error={update.error} />}{' '}
                    {update.isSuccess && (
                      <Success>
                        The user has been updated. Previous permissions expire on the next request.
                      </Success>
                    )}
                    <Submit busy={update.isPending}>Save user permissions</Submit>
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
