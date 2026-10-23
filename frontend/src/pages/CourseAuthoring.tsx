import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, body } from '../api';
import { useAuth } from '../auth';
import type { Category, Course } from '../types';
import { ErrorBox, Field, Loading, Modal, Submit, Success } from '../ui';

type Invitation = { course_id: string; title: string; instructor_name: string };
type Team = {
  ownerId: string;
  ownerName: string;
  canManage: boolean;
  members: { user_id: string; display_name: string; status: 'INVITED' | 'ACCEPTED' }[];
};
type Template = { id: string; name: string; builtIn: boolean };

export function CourseInvitations() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['course-invitations'],
    queryFn: () => api<Invitation[]>('/courses/invitations'),
  });
  const reply = useMutation({
    mutationFn: ({ course, accept }: { course: string; accept: boolean }) =>
      api(`/courses/${course}/team/reply`, { method: 'POST', body: body({ accept }) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course-invitations'] });
      void client.invalidateQueries({ queryKey: ['own-courses'] });
    },
  });
  if (query.error) return <ErrorBox error={query.error} retry={() => void query.refetch()} />;
  if (!query.data?.length) return null;
  return (
    <section className="panel">
      <h2>Kurs ekip davetleri</h2>
      <p>
        Taslakları birlikte hazırlayabilirsiniz. Öğrenci yönetimi ve yayın başvurusu kurs sahibinde
        kalır.
      </p>
      {reply.error && <ErrorBox error={reply.error} />}
      {query.data.map((invitation) => (
        <div className="authoring-row" key={invitation.course_id}>
          <div>
            <strong>{invitation.title}</strong>
            <p>{invitation.instructor_name} sizi ortak düzenlemeye davet etti.</p>
          </div>
          <button
            className="button primary small"
            disabled={reply.isPending}
            onClick={() => reply.mutate({ course: invitation.course_id, accept: true })}
          >
            Daveti kabul et
          </button>
          <button
            className="button secondary small"
            disabled={reply.isPending}
            onClick={() => reply.mutate({ course: invitation.course_id, accept: false })}
          >
            Daveti reddet
          </button>
        </div>
      ))}
    </section>
  );
}

export function CourseTeam({ courseId }: { courseId: string }) {
  const client = useQueryClient(),
    navigate = useNavigate();
  const { session } = useAuth();
  const query = useQuery({
    queryKey: ['course-team', courseId],
    queryFn: () => api<Team>(`/courses/${courseId}/team`),
  });
  const [email, setEmail] = useState('');
  const invite = useMutation({
    mutationFn: () => api(`/courses/${courseId}/team`, { method: 'POST', body: body({ email }) }),
    onSuccess: () => {
      setEmail('');
      void query.refetch();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/courses/${courseId}/team/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      void client.invalidateQueries({ queryKey: ['own-courses'] });
      if (id === session?.user.id) navigate('/instructor');
      else void query.refetch();
    },
  });
  if (query.isPending) return <Loading />;
  if (query.error) return <ErrorBox error={query.error} retry={() => void query.refetch()} />;
  const team = query.data!;
  return (
    <section className="panel">
      <h2>Kurs ekibi</h2>
      <p>
        <strong>Kurs sahibi:</strong> {team.ownerName}
      </p>
      <p>
        Ortak eğitmenler kurs metnini, dersleri, test sorularını ve kaynakları taslakta düzenler.
        Yayın başvurusu, öğrenci verileri ve ekip yönetimi kurs sahibinde kalır. Davet kabul
        edilmeden erişim verilmez.
      </p>
      {invite.error && <ErrorBox error={invite.error} />}
      {remove.error && <ErrorBox error={remove.error} />}
      {invite.isSuccess && (
        <Success>Davet kaydedildi. Eğitmen kendi stüdyosundan kabul edebilir.</Success>
      )}
      {team.canManage && (
        <form
          className="form-actions"
          onSubmit={(e) => {
            e.preventDefault();
            invite.mutate();
          }}
        >
          <Field
            label="Eğitmenin kayıtlı e-posta adresi"
            hint="E-posta adresini doğrulamış bir eğitmen hesabı kullanın."
          >
            <input
              type="email"
              required
              maxLength={254}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Submit busy={invite.isPending}>Eğitmen davet et</Submit>
        </form>
      )}
      {team.members.length === 0 && <p className="muted">Henüz ortak eğitmen yok.</p>}
      {team.members.map((m) => (
        <div className="authoring-row" key={m.user_id}>
          <span>
            {m.display_name} · {m.status === 'ACCEPTED' ? 'Ortak eğitmen' : 'Kabul bekleniyor'}
          </span>
          {(team.canManage || m.user_id === session?.user.id) && (
            <button
              className="text-button danger"
              disabled={remove.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    m.user_id === session?.user.id
                      ? 'Kurs ekibinden ayrılmak istiyor musunuz?'
                      : 'Bu eğitmenin kurs erişimi kaldırılsın mı?',
                  )
                )
                  remove.mutate(m.user_id);
              }}
            >
              {m.user_id === session?.user.id ? 'Ekipten ayrıl' : 'Erişimi kaldır'}
            </button>
          )}
        </div>
      ))}
    </section>
  );
}

export function TemplateLauncher() {
  const [open, setOpen] = useState(false),
    navigate = useNavigate();
  const query = useQuery({
    queryKey: ['course-templates'],
    queryFn: () => api<Template[]>('/courses/templates'),
    enabled: open,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
    enabled: open,
  });
  const create = useMutation({
    mutationFn: (input: { template: string; title: string; categoryId: number }) =>
      api<Course>(`/courses/templates/${input.template}/create`, {
        method: 'POST',
        body: body({ title: input.title, categoryId: input.categoryId }),
      }),
    onSuccess: (course) => {
      navigate(`/instructor/${course.id}`);
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/courses/templates/${id}`, { method: 'DELETE' }),
    onSuccess: () => void query.refetch(),
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    create.mutate({
      template: String(f.get('template')),
      title: String(f.get('title')),
      categoryId: Number(f.get('categoryId')),
    });
  }
  return (
    <>
      <button className="button secondary" onClick={() => setOpen(true)}>
        Şablondan kurs oluştur
      </button>
      {open && (
        <Modal title="Şablondan kurs oluştur" onClose={() => setOpen(false)}>
          <p>
            Metinler, ders yapısı ve test soruları yeni kimliklerle kopyalanır. Öğrenciler,
            ilerlemeler ve dosyalar taşınmaz. Videoları ve korunan kaynakları yeni kursa yeniden
            yükleyin.
          </p>
          {query.error && <ErrorBox error={query.error} />}{' '}
          {categories.error && <ErrorBox error={categories.error} />}{' '}
          {create.error && <ErrorBox error={create.error} />}{' '}
          {remove.error && <ErrorBox error={remove.error} />}
          {query.isPending || categories.isPending ? (
            <Loading />
          ) : (
            query.data &&
            categories.data && (
              <form onSubmit={submit} className="workspace-fields">
                <Field label="Kurs şablonu">
                  <select name="template" required>
                    {query.data.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                        {t.builtIn ? ' · Hazır şablon' : ' · Kişisel şablon'}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Yeni kursun başlığı">
                  <input name="title" required minLength={10} maxLength={200} />
                </Field>
                <Field label="Yeni kursun kategorisi">
                  <select name="categoryId" required>
                    {categories.data.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Submit busy={create.isPending}>Taslağı oluştur</Submit>
              </form>
            )
          )}
          {!!query.data?.some((t) => !t.builtIn) && (
            <div>
              <h3>Kişisel şablonlarım</h3>
              {query.data
                .filter((t) => !t.builtIn)
                .map((t) => (
                  <div className="authoring-row" key={t.id}>
                    <span>{t.name}</span>
                    <button
                      className="text-button danger"
                      disabled={remove.isPending}
                      onClick={() => {
                        if (
                          window.confirm('Kişisel şablon silinsin mi? Mevcut kurslar etkilenmez.')
                        )
                          remove.mutate(t.id);
                      }}
                    >
                      Şablonu sil
                    </button>
                  </div>
                ))}
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
