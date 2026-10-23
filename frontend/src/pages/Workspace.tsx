import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { api, body, levels, lessonTypes } from '../api';
import type { Course, Category, Lesson, Quiz, Media, Page, Material, Workspace } from '../types';
import { Badge, ErrorBox, Field, Loading, Modal, PageTitle, Success } from '../ui';
import VideoPlayer from '../VideoPlayer';
import { AssignmentGrading, CourseStudents, CourseDiscussion } from './LearningFeatures';
import { CourseAnalytics, Announcements } from './Platform';
import RichTextEditor from '../RichTextEditor';
import { duplicateLesson } from '../courseAuthoring';
import { useAuth } from '../auth';
import { CourseTeam } from './CourseAuthoring';
const blankQuiz = (): Quiz => ({ passingScore: 70, questions: [] });
const blankQuestion = () => ({
  id: crypto.randomUUID(),
  prompt: '',
  options: ['', ''],
  correctOption: 0,
  explanation: '',
});
export default function CourseWorkspace({
  courseId,
  review = false,
}: {
  courseId: string;
  review?: boolean;
}) {
  const client = useQueryClient();
  const { session } = useAuth();
  const query = useQuery({
    queryKey: ['workspace', courseId],
    queryFn: () => api<Workspace>(`/courses/${courseId}/workspace`),
  });
  const live = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api<Course>(`/courses/${courseId}`),
    enabled: !review,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api<Category[]>('/courses/categories'),
  });
  const media = useQuery({
    queryKey: ['workspace-media', courseId],
    queryFn: () => api<Page<Media>>(`/media/my?courseId=${courseId}&size=100&sort=createdAt,desc`),
    enabled: !review,
    refetchInterval: (q) =>
      q.state.data?.content.some((m) => m.status === 'PROCESSING') ? 4000 : false,
  });
  const [workspace, setWorkspace] = useState<Workspace>();
  const [dirty, setDirty] = useState(false);
  const [preview, setPreview] = useState(false);
  const [selected, setSelected] = useState<string>();
  const [tool, setTool] = useState<string>();
  const [uploadError, setUploadError] = useState<unknown>();
  const [uploading, setUploading] = useState(false);
  useEffect(() => {
    if (query.data && !dirty) setWorkspace(structuredClone(query.data));
  }, [query.data, dirty]);
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);
  const path = `/courses/${courseId}/workspace`;
  const archive = useMutation({
    mutationFn: () => api(`/courses/${courseId}/archive`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course', courseId] });
      void client.invalidateQueries({ queryKey: ['workspace', courseId] });
      void client.invalidateQueries({ queryKey: ['own-courses'] });
    },
  });
  const saveTemplate = useMutation({
    mutationFn: (name: string) =>
      api(`/courses/${courseId}/templates`, {
        method: 'POST',
        body: body({ name, revision: workspace!.revision }),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['course-templates'] });
    },
  });
  const action = useMutation({
    mutationFn: async (kind: string) => {
      if (kind === 'save')
        return api<Workspace>(path, {
          method: 'PUT',
          body: body({ revision: workspace!.revision, document: workspace!.document }),
        });
      if (kind === 'submit')
        return api<Workspace>(`${path}/submit?revision=${workspace!.revision}`, { method: 'POST' });
      if (kind === 'withdraw') return api<Workspace>(`${path}/withdraw`, { method: 'POST' });
      const reason = kind === 'reject' ? window.prompt('Düzeltme gerekçesi') : null;
      if (kind === 'reject' && !reason) throw new Error('Gerekçe gerekli.');
      return api<Workspace>(
        `/courses/admin/${courseId}/workspace/decision?revision=${workspace!.revision}`,
        {
          method: 'POST',
          body: body({ action: kind === 'approve' ? 'APPROVE' : 'REJECT', reason }),
        },
      );
    },
    onSuccess: (w) => {
      setDirty(false);
      setWorkspace(structuredClone(w));
      client.setQueryData(['workspace', courseId], w);
      void client.invalidateQueries({ queryKey: ['own-courses'] });
      void client.invalidateQueries({ queryKey: ['workspace-queue'] });
      void client.invalidateQueries({ queryKey: ['course', courseId] });
    },
  });
  function change(update: (w: Workspace) => void) {
    setWorkspace((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      update(next);
      return next;
    });
    setDirty(true);
    action.reset();
  }
  if (query.isPending || !workspace)
    return query.error ? <ErrorBox error={query.error} /> : <Loading />;
  const w = workspace,
    c = w.document.course;
  const canManage = session?.user.id === c.instructorId || session?.user.role === 'ADMIN';
  const locked = review || w.status === 'PENDING_REVIEW';
  const publishedLessonIds = new Set(
    live.data?.publishedAt ? live.data.sections?.flatMap((s) => s.lessons.map((l) => l.id)) : [],
  );
  const lessons = c.sections?.flatMap((s) => s.lessons) || [];
  const lesson = lessons.find((l) => l.id === selected);
  function editLesson(update: (l: Lesson) => void) {
    change((next) => {
      const l = next.document.course
        .sections!.flatMap((s) => s.lessons)
        .find((x) => x.id === selected);
      if (l) update(l);
    });
  }
  function moveSection(index: number, direction: number) {
    change((next) => {
      const rows = next.document.course.sections!;
      const destination = index + direction;
      if (destination >= 0 && destination < rows.length)
        [rows[index], rows[destination]] = [rows[destination], rows[index]];
    });
  }
  function moveLesson(sectionId: string, index: number, direction: number) {
    change((next) => {
      const rows = next.document.course.sections!.find((s) => s.id === sectionId)!.lessons;
      const dest = index + direction;
      if (dest >= 0 && dest < rows.length) [rows[index], rows[dest]] = [rows[dest], rows[index]];
    });
  }
  function setQuiz(update: (q: Quiz) => void) {
    change((next) => {
      const q = next.document.quizzes[selected!] || blankQuiz();
      update(q);
      next.document.quizzes[selected!] = q;
    });
  }
  async function uploadMaterial(file: File, type: 'RESOURCE' | 'SUBTITLE') {
    setUploading(true);
    setUploadError(null);
    try {
      const form = new FormData();
      form.set('file', file);
      form.set('courseId', courseId);
      form.set('purpose', 'MATERIAL');
      const result = await api<{ name: string; url: string }>('/media/resources', {
        method: 'POST',
        body: form,
      });
      change((next) => {
        const rows = next.document.materials[selected!] || [];
        rows.push({
          title: result.name,
          url: result.url,
          type,
          language: type === 'SUBTITLE' ? 'tr' : undefined,
        });
        next.document.materials[selected!] = rows;
      });
    } catch (e) {
      setUploadError(e);
    } finally {
      setUploading(false);
    }
  }
  return (
    <>
      <Link className="back-link" to={review ? '/admin/courses' : '/instructor'}>
        ← {review ? 'İncelemeler' : 'Kurslarım'}
      </Link>
      <PageTitle
        eyebrow={review ? 'KURS İNCELEMESİ' : 'EĞİTMEN STÜDYOSU'}
        title={c.title}
        description="Değişiklikler onaylandığında öğrencilere yayımlanır."
      />
      <div className="panel workspace-toolbar">
        <Badge value={w.status} />
        <span>
          Sürüm taslağı {w.revision}
          {dirty ? ' · Kaydedilmemiş değişiklikler' : ''}
        </span>
        <div className="form-actions">
          <button className="button secondary" onClick={() => setPreview(!preview)}>
            {preview ? 'Düzenlemeye dön' : 'Öğrenci görünümünde ön izle'}
          </button>
          {!review && !locked && (
            <>
              <button
                className="button primary"
                disabled={!dirty || action.isPending || uploading}
                onClick={() => action.mutate('save')}
              >
                Taslağı kaydet
              </button>
              {canManage && (
                <button
                  className="button secondary"
                  disabled={dirty || w.revision === 0 || action.isPending || uploading}
                  onClick={() => action.mutate('submit')}
                >
                  İncelemeye gönder
                </button>
              )}
            </>
          )}
          {!review && canManage && locked && (
            <button
              className="button secondary"
              disabled={action.isPending}
              onClick={() => action.mutate('withdraw')}
            >
              Başvuruyu geri çek
            </button>
          )}
          {review && w.status === 'PENDING_REVIEW' && (
            <>
              <button
                className="button primary"
                disabled={action.isPending}
                onClick={() => action.mutate('approve')}
              >
                Taslağı onayla ve yayımla
              </button>
              <button
                className="button secondary"
                disabled={action.isPending}
                onClick={() => action.mutate('reject')}
              >
                Düzeltme iste
              </button>
            </>
          )}
          {!review && (
            <button
              className="button secondary"
              disabled={dirty || saveTemplate.isPending}
              onClick={() => {
                const name = window.prompt('Kişisel şablon adı');
                if (name?.trim()) saveTemplate.mutate(name.trim());
              }}
            >
              Şablon olarak kaydet
            </button>
          )}
          {!review && canManage && live.data?.status !== 'ARCHIVED' && (
            <button
              className="text-button danger"
              disabled={dirty || archive.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    'Kurs yeni kayıtlara kapatılacak. Mevcut öğrenciler erişmeye devam eder. İnceleme başvurusu varsa geri çekilir.',
                  )
                )
                  archive.mutate();
              }}
            >
              Kursu arşivle
            </button>
          )}
        </div>
      </div>
      {!review && !canManage && (
        <p className="notice">
          Bu kursta ortak eğitmensiniz. Taslağı düzenleyebilirsiniz; yayın başvurusunu kurs sahibi
          gönderir.
        </p>
      )}
      {saveTemplate.error && <ErrorBox error={saveTemplate.error} />}
      {saveTemplate.isSuccess && (
        <Success>
          Kişisel şablon kaydedildi. Eğitmen stüdyosundaki şablon listesinden yeni kurs
          oluşturabilirsiniz.
        </Success>
      )}
      {archive.error && <ErrorBox error={archive.error} />}
      {archive.isSuccess && (
        <Success>Kurs arşivlendi. Mevcut öğrencilerin erişimi korunuyor.</Success>
      )}
      {w.feedback && <p className="notice warning">Yönetici geri bildirimi: {w.feedback}</p>}
      {action.error && <ErrorBox error={action.error} />}{' '}
      {action.isSuccess && <Success>İşlem kaydedildi.</Success>}
      {!!w.checklist.length && (
        <details className="panel">
          <summary>Yayın öncesi kontrol listesi ({w.checklist.length})</summary>
          <ul>
            {w.checklist.map((i) => (
              <li key={i}>{i}</li>
            ))}
          </ul>
        </details>
      )}
      {preview ? (
        <section className="panel">
          <h2>{c.title}</h2>
          <ReactMarkdown>{c.description || ''}</ReactMarkdown>
          <h3>Öğreneceklerin</h3>
          <ul>
            {c.outcomes?.map((o) => (
              <li key={o}>{o}</li>
            ))}
          </ul>
          {c.sections?.map((s) => (
            <div key={s.id}>
              <h3>{s.title}</h3>
              {s.lessons.map((l) => (
                <details key={l.id}>
                  <summary>
                    {l.title} · {lessonTypes[l.type]}
                  </summary>
                  {l.type === 'VIDEO' && l.videoUrl ? (
                    <VideoPlayer src={l.videoUrl} />
                  ) : (
                    <ReactMarkdown>{l.content || ''}</ReactMarkdown>
                  )}
                  {l.type === 'QUIZ' && (
                    <ol>
                      {w.document.quizzes[l.id]?.questions.map((q) => (
                        <li key={q.id}>
                          {q.prompt}
                          <ul>
                            {q.options.map((o, i) => (
                              <li key={i}>{o}</li>
                            ))}
                          </ul>
                        </li>
                      ))}
                    </ol>
                  )}
                </details>
              ))}
            </div>
          ))}
        </section>
      ) : (
        <>
          <fieldset className="panel workspace-fields" disabled={locked || action.isPending}>
            <legend>Kurs bilgileri</legend>
            <Field label="Kurs başlığı">
              <input
                value={c.title}
                maxLength={200}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.title = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Kısa açıklama">
              <textarea
                value={c.shortDescription || ''}
                maxLength={500}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.shortDescription = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Bu kurs kimler için?">
              <textarea
                value={c.targetAudience || ''}
                maxLength={2000}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.targetAudience = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Açıklama">
              <RichTextEditor
                rows={6}
                value={c.description || ''}
                onChange={(value) =>
                  change((n) => {
                    n.document.course.description = value;
                  })
                }
              />
            </Field>
            <div className="form-grid">
              <Field label="Kategori">
                <select
                  value={w.document.categoryId}
                  onChange={(e) =>
                    change((n) => {
                      n.document.categoryId = Number(e.target.value);
                    })
                  }
                >
                  {categories.data?.map((cat) => (
                    <option value={cat.id} key={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Seviye">
                <select
                  value={c.level}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.level = e.target.value as Course['level'];
                    })
                  }
                >
                  {Object.entries(levels).map(([v, label]) => (
                    <option key={v} value={v}>
                      {label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Dil">
                <input
                  value={c.language || ''}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.language = e.target.value;
                    })
                  }
                />
              </Field>
              <Field label="Fiyat">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={c.price}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.price = Number(e.target.value);
                    })
                  }
                />
              </Field>
              <Field label="İndirimli fiyat">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={c.discountPrice ?? ''}
                  onChange={(e) =>
                    change((n) => {
                      n.document.course.discountPrice =
                        e.target.value === '' ? null : Number(e.target.value);
                    })
                  }
                />
              </Field>
            </div>
            <Field label="Kapak görseli adresi">
              <input
                value={c.thumbnailUrl || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.thumbnailUrl = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Ön koşullar — her satıra bir madde">
              <textarea
                value={c.requirements?.join('\n') || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.requirements = e.target.value.split('\n');
                  })
                }
              />
            </Field>
            <Field label="Kazanımlar — her satıra bir madde">
              <textarea
                value={c.outcomes?.join('\n') || ''}
                onChange={(e) =>
                  change((n) => {
                    n.document.course.outcomes = e.target.value.split('\n');
                  })
                }
              />
            </Field>
          </fieldset>
          <section className="panel">
            <h2>Müfredat</h2>
            <p className="muted">
              Yayımlanmış derslerin kimliği korunur. İçerik ve sıra değişebilir; ders kaldırmak veya
              türünü değiştirmek için yeni bir kurs hazırlayın.
            </p>
            {c.sections?.map((s, si) => (
              <article key={s.id} className="editor-section">
                <div className="form-actions">
                  <input
                    aria-label={`Bölüm ${si + 1} başlığı`}
                    disabled={locked}
                    value={s.title}
                    onChange={(e) =>
                      change((n) => {
                        n.document.course.sections![si].title = e.target.value;
                      })
                    }
                  />
                  <button
                    className="button secondary small"
                    disabled={locked || si === 0}
                    aria-label={`${s.title} bölümünü yukarı taşı`}
                    onClick={() => moveSection(si, -1)}
                  >
                    ↑
                  </button>
                  <button
                    className="button secondary small"
                    disabled={locked || si === c.sections!.length - 1}
                    aria-label={`${s.title} bölümünü aşağı taşı`}
                    onClick={() => moveSection(si, 1)}
                  >
                    ↓
                  </button>
                  <button
                    className="text-button danger"
                    disabled={locked || s.lessons.some((l) => publishedLessonIds.has(l.id))}
                    onClick={() => {
                      if (
                        window.confirm(
                          'Bölüm taslaktan kaldırılsın mı? Yayımlanmış dersler kaldırılamaz.',
                        )
                      )
                        change((n) => {
                          n.document.course.sections!.splice(si, 1);
                        });
                    }}
                  >
                    Bölümü kaldır
                  </button>
                </div>
                {s.lessons.map((l, li) => (
                  <div className="lesson-row" key={l.id}>
                    <button className="text-button" onClick={() => setSelected(l.id)}>
                      {l.title} · {lessonTypes[l.type]}
                    </button>
                    <button
                      className="icon-button"
                      disabled={locked || li === 0}
                      aria-label={`${l.title} dersini yukarı taşı`}
                      onClick={() => moveLesson(s.id, li, -1)}
                    >
                      ↑
                    </button>
                    <button
                      className="icon-button"
                      disabled={locked || li === s.lessons.length - 1}
                      aria-label={`${l.title} dersini aşağı taşı`}
                      onClick={() => moveLesson(s.id, li, 1)}
                    >
                      ↓
                    </button>
                    <button
                      className="button secondary small"
                      disabled={locked}
                      aria-label={`${l.title} dersini kopyala`}
                      onClick={() =>
                        change((next) => {
                          duplicateLesson(next, l.id);
                        })
                      }
                    >
                      Dersi kopyala
                    </button>
                  </div>
                ))}
                {!locked && (
                  <button
                    className="button secondary small"
                    onClick={() => {
                      const id = crypto.randomUUID();
                      change((n) => {
                        n.document.course.sections![si].lessons.push({
                          id,
                          title: 'Yeni ders',
                          type: 'ARTICLE',
                          orderIndex: s.lessons.length,
                          previewAllowed: false,
                          content: '',
                        });
                      });
                      setSelected(id);
                    }}
                  >
                    Ders ekle
                  </button>
                )}
              </article>
            ))}
            {!locked && (
              <div className="form-actions">
                <button
                  className="button secondary"
                  onClick={() =>
                    change((n) => {
                      n.document.course.sections ||= [];
                      n.document.course.sections.push({
                        id: crypto.randomUUID(),
                        title: 'Yeni bölüm',
                        orderIndex: n.document.course.sections.length,
                        lessons: [],
                      });
                    })
                  }
                >
                  Bölüm ekle
                </button>
                <Link className="button secondary" to={`/media?course=${courseId}`}>
                  Toplu video yükle
                </Link>
              </div>
            )}
          </section>
        </>
      )}
      {!review && (
        <>
          <div className="feature-tabs">
            {[
              ['students', 'Öğrenciler'],
              ['reports', 'Öğrenme raporu'],
              ['announcements', 'Duyurular'],
              ['questions', 'Soru-cevap'],
              ['versions', 'Yayın geçmişi'],
              ['team', 'Kurs ekibi'],
            ]
              .filter(([key]) => canManage || ['team', 'versions'].includes(key))
              .map(([key, label]) => (
                <button
                  className="button secondary"
                  key={key}
                  onClick={() => setTool(tool === key ? undefined : key)}
                >
                  {label}
                </button>
              ))}
          </div>
          {tool === 'students' && <CourseStudents courseId={courseId} />}{' '}
          {tool === 'reports' && <CourseAnalytics courseId={courseId} course={c} />}{' '}
          {tool === 'announcements' && <Announcements courseId={courseId} instructor />}
          {tool === 'questions' && (
            <CourseDiscussion courseId={courseId} instructorId={c.instructorId} />
          )}{' '}
          {tool === 'versions' && <VersionHistory courseId={courseId} />}
          {tool === 'team' && <CourseTeam courseId={courseId} />}
        </>
      )}
      {lesson && (
        <Modal title={lesson.title} onClose={() => setSelected(undefined)}>
          <fieldset disabled={locked || uploading} className="workspace-fields">
            <Field label="Ders başlığı">
              <input
                value={lesson.title}
                maxLength={200}
                onChange={(e) =>
                  editLesson((l) => {
                    l.title = e.target.value;
                  })
                }
              />
            </Field>
            <Field label="Ders türü">
              <select
                disabled={publishedLessonIds.has(lesson.id)}
                value={lesson.type}
                onChange={(e) =>
                  editLesson((l) => {
                    l.type = e.target.value as Lesson['type'];
                  })
                }
              >
                {Object.entries(lessonTypes).map(([v, label]) => (
                  <option key={v} value={v}>
                    {label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Bölüm">
              <select
                value={c.sections?.find((s) => s.lessons.some((l) => l.id === lesson.id))?.id}
                onChange={(e) =>
                  change((n) => {
                    const sections = n.document.course.sections!;
                    const source = sections.find((s) => s.lessons.some((l) => l.id === lesson.id))!;
                    source.lessons = source.lessons.filter((l) => l.id !== lesson.id);
                    sections.find((s) => s.id === e.target.value)!.lessons.push(lesson);
                  })
                }
              >
                {c.sections?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>
            </Field>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={lesson.previewAllowed}
                onChange={(e) =>
                  editLesson((l) => {
                    l.previewAllowed = e.target.checked;
                  })
                }
              />{' '}
              Bu ders herkese açık ön izlenebilir
            </label>
            <Field
              label={
                lesson.type === 'ASSIGNMENT'
                  ? 'Ödev yönergesi ve değerlendirme ölçütleri'
                  : 'Ders metni'
              }
            >
              <RichTextEditor
                rows={7}
                value={lesson.content || ''}
                onChange={(value) =>
                  editLesson((l) => {
                    l.content = value;
                  })
                }
              />
            </Field>
            {lesson.type === 'VIDEO' && (
              <>
                <Field label="Ders videosu">
                  <select
                    value={lesson.videoUrl || ''}
                    onChange={(e) =>
                      editLesson((l) => {
                        l.videoUrl = e.target.value;
                      })
                    }
                  >
                    <option value="">Video seç</option>
                    {lesson.videoUrl &&
                      !media.data?.content.some((m) => m.streamUrl === lesson.videoUrl) && (
                        <option value={lesson.videoUrl}>Mevcut video</option>
                      )}
                    {media.data?.content
                      .filter(
                        (m) =>
                          m.type === 'VIDEO' &&
                          m.status === 'READY' &&
                          (!m.courseId || m.courseId === courseId),
                      )
                      .map((m) => (
                        <option key={m.id} value={m.streamUrl}>
                          {m.originalFileName}
                        </option>
                      ))}
                  </select>
                </Field>
                <p className="muted">İşlenen videolar hazır olduğunda listede görünür.</p>
              </>
            )}
            {lesson.type === 'QUIZ' && (
              <>
                <h3>Test ve soru bankası</h3>
                <div className="form-grid">
                  <Field label="Geçme puanı">
                    <input
                      type="number"
                      min="1"
                      max="100"
                      value={w.document.quizzes[lesson.id]?.passingScore ?? 70}
                      onChange={(e) =>
                        setQuiz((q) => {
                          q.passingScore = Number(e.target.value);
                        })
                      }
                    />
                  </Field>
                  {[
                    ['maxAttempts', 'Deneme sınırı (boş: sınırsız)'],
                    ['timeLimitSeconds', 'Süre sınırı (saniye, boş: sınırsız)'],
                    ['questionCount', 'Bankadan seçilecek soru sayısı'],
                  ].map(([key, label]) => (
                    <Field key={key} label={label}>
                      <input
                        type="number"
                        min="1"
                        value={w.document.quizzes[lesson.id]?.[key as 'maxAttempts'] ?? ''}
                        onChange={(e) =>
                          setQuiz((q) => {
                            q[key as 'maxAttempts'] = e.target.value
                              ? Number(e.target.value)
                              : undefined;
                          })
                        }
                      />
                    </Field>
                  ))}
                </div>
                <label>
                  <input
                    type="checkbox"
                    checked={w.document.quizzes[lesson.id]?.randomize ?? false}
                    onChange={(e) =>
                      setQuiz((q) => {
                        q.randomize = e.target.checked;
                      })
                    }
                  />{' '}
                  Soruları rastgele seç ve sırala
                </label>
                {w.document.quizzes[lesson.id]?.questions.map((q, qi) => (
                  <article className="panel" key={q.id}>
                    <Field label={`Soru ${qi + 1}`}>
                      <textarea
                        value={q.prompt}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].prompt = e.target.value;
                          })
                        }
                      />
                    </Field>
                    {q.options.map((o, oi) => (
                      <Field key={oi} label={`Seçenek ${oi + 1}`}>
                        <input
                          value={o}
                          onChange={(e) =>
                            setQuiz((quiz) => {
                              quiz.questions[qi].options[oi] = e.target.value;
                            })
                          }
                        />
                      </Field>
                    ))}
                    <Field label="Doğru seçenek">
                      <select
                        value={q.correctOption ?? 0}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].correctOption = Number(e.target.value);
                          })
                        }
                      >
                        {q.options.map((_, i) => (
                          <option value={i} key={i}>
                            {i + 1}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Sonuç açıklaması">
                      <textarea
                        value={q.explanation || ''}
                        onChange={(e) =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].explanation = e.target.value;
                          })
                        }
                      />
                    </Field>
                    <div className="form-actions">
                      <button
                        className="button secondary small"
                        disabled={q.options.length >= 8}
                        onClick={() =>
                          setQuiz((quiz) => {
                            quiz.questions[qi].options.push('');
                          })
                        }
                      >
                        Seçenek ekle
                      </button>
                      <button
                        className="text-button danger"
                        onClick={() =>
                          setQuiz((quiz) => {
                            quiz.questions.splice(qi, 1);
                          })
                        }
                      >
                        Soruyu kaldır
                      </button>
                    </div>
                  </article>
                ))}
                <button
                  className="button secondary"
                  onClick={() =>
                    setQuiz((q) => {
                      q.questions.push(blankQuestion());
                    })
                  }
                >
                  Soru ekle
                </button>
              </>
            )}
            <h3>Kaynaklar ve altyazılar</h3>
            {(w.document.materials[lesson.id] || []).map((m, i) => (
              <div className="panel" key={i}>
                <Field label="Kaynak adı">
                  <input
                    value={m.title}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].title = e.target.value;
                      })
                    }
                  />
                </Field>
                <Field label="Adres">
                  <input
                    value={m.url}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].url = e.target.value;
                      })
                    }
                  />
                </Field>
                <Field label="Tür">
                  <select
                    value={m.type}
                    onChange={(e) =>
                      change((n) => {
                        n.document.materials[lesson.id][i].type = e.target
                          .value as Material['type'];
                      })
                    }
                  >
                    <option value="RESOURCE">İndirilebilir kaynak</option>
                    <option value="SUBTITLE">WebVTT altyazı</option>
                  </select>
                </Field>
                {m.type === 'SUBTITLE' && (
                  <Field label="Dil kodu">
                    <input
                      value={m.language || ''}
                      onChange={(e) =>
                        change((n) => {
                          n.document.materials[lesson.id][i].language = e.target.value;
                        })
                      }
                    />
                  </Field>
                )}
                <button
                  className="text-button danger"
                  onClick={() =>
                    change((n) => {
                      n.document.materials[lesson.id].splice(i, 1);
                    })
                  }
                >
                  Kaynağı kaldır
                </button>
              </div>
            ))}
            <div className="form-actions">
              <button
                className="button secondary"
                onClick={() =>
                  change((n) => {
                    n.document.materials[lesson.id] ||= [];
                    n.document.materials[lesson.id].push({
                      title: 'Yeni kaynak',
                      url: '',
                      type: 'RESOURCE',
                    });
                  })
                }
              >
                Bağlantı ekle
              </button>
              <label className="button secondary">
                Dosya yükle
                <input
                  className="sr-only"
                  type="file"
                  accept=".pdf,.txt,.vtt"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f)
                      void uploadMaterial(
                        f,
                        f.name.toLowerCase().endsWith('.vtt') ? 'SUBTITLE' : 'RESOURCE',
                      );
                    e.target.value = '';
                  }}
                />
              </label>
            </div>
            <button
              className="text-button danger"
              disabled={publishedLessonIds.has(lesson.id)}
              onClick={() => {
                if (window.confirm('Ders taslaktan kaldırılsın mı?')) {
                  change((n) => {
                    for (const s of n.document.course.sections!)
                      s.lessons = s.lessons.filter((l) => l.id !== lesson.id);
                    delete n.document.quizzes[lesson.id];
                    delete n.document.materials[lesson.id];
                  });
                  setSelected(undefined);
                }
              }}
            >
              Dersi taslaktan kaldır
            </button>
          </fieldset>
          {!!uploadError && <ErrorBox error={uploadError} />}{' '}
          {uploading && <p role="status">Dosya yükleniyor…</p>}
          {!review && canManage && lesson.type === 'ASSIGNMENT' && (
            <AssignmentGrading courseId={courseId} lessonId={lesson.id} />
          )}
          <p className="muted">
            Ders değişiklikleri ana ekrandaki “Taslağı kaydet” ile kaydedilir.
          </p>
          <button className="button primary" onClick={() => setSelected(undefined)}>
            Düzenlemeyi bitir
          </button>
        </Modal>
      )}
    </>
  );
}
function VersionHistory({ courseId }: { courseId: string }) {
  const q = useQuery({
    queryKey: ['versions', courseId],
    queryFn: () =>
      api<{ version_number: number; published_at: string }[]>(`/courses/${courseId}/versions`),
  });
  return (
    <section className="panel">
      <h2>Yayın geçmişi</h2>
      {q.error ? (
        <ErrorBox error={q.error} />
      ) : q.isPending ? (
        <Loading />
      ) : (
        <ul>
          {q.data?.map((v) => (
            <li key={v.version_number}>
              Sürüm {v.version_number} · {new Date(v.published_at).toLocaleString('tr-TR')}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
