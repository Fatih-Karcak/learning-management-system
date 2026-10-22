import {
  cloneElement,
  isValidElement,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { AlertCircle, ArrowLeft, ArrowRight, BookOpen, Check, LoaderCircle, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { money, levels, safeUrl, statusLabels } from './api';
import type { Course } from './types';

export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div className="notice error" role="alert">
      <AlertCircle size={19} />
      <div>
        <strong>İşlem tamamlanamadı</strong>
        <p>{error instanceof Error ? error.message : 'Beklenmeyen bir hata oluştu.'}</p>
        {retry && (
          <button className="text-button" type="button" onClick={retry}>
            Tekrar dene
          </button>
        )}
      </div>
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={24} /> Yükleniyor…
    </div>
  );
}
export function Empty({
  title,
  text,
  children,
}: {
  title: string;
  text: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <BookOpen size={29} />
      </span>
      <h3>{title}</h3>
      <p>{text}</p>
      {children}
    </div>
  );
}
export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`badge ${['COMPLETED', 'SUCCEEDED', 'SUCCESS', 'READY', 'PUBLISHED', 'SENT', 'ACTIVE'].includes(value) ? 'green' : ''}`}
    >
      {statusLabels[value] || value}
    </span>
  );
}
export function Avatar({
  name,
  src,
  large = false,
}: {
  name: string;
  src?: string;
  large?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  const initials = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('');
  return (
    <span className={`avatar ${large ? 'large' : ''}`}>
      {safeUrl(src) && !failed ? (
        <img src={safeUrl(src)} alt={name} onError={() => setFailed(true)} />
      ) : (
        initials
      )}
    </span>
  );
}
export function PageTitle({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {children}
    </div>
  );
}
export function Pagination({
  page,
  pages,
  onChange,
}: {
  page: number;
  pages: number;
  onChange: (n: number) => void;
}) {
  if (pages < 2) return null;
  return (
    <nav className="pagination" aria-label="Sayfalar">
      <button className="button secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <ArrowLeft size={16} /> Önceki
      </button>
      <span>
        {page + 1} / {pages}
      </span>
      <button
        className="button secondary"
        disabled={page + 1 >= pages}
        onClick={() => onChange(page + 1)}
      >
        Sonraki <ArrowRight size={16} />
      </button>
    </nav>
  );
}
export function Cover({
  course,
  className = '',
}: {
  course: Pick<Course, 'title' | 'thumbnailUrl' | 'categoryName'>;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  const src = safeUrl(course.thumbnailUrl);
  return (
    <div className={`course-cover ${className}`}>
      {src && !failed ? (
        <img src={src} alt={course.title} onError={() => setFailed(true)} loading="lazy" />
      ) : (
        <>
          <span className="cover-orbit" />
          <span className="cover-shape" />
          <BookOpen size={44} strokeWidth={1.2} />
          <span className="cover-label">{course.categoryName || 'Yeni bir bakış açısı'}</span>
        </>
      )}
    </div>
  );
}
export function CourseCard({ course }: { course: Course }) {
  return (
    <Link to={`/courses/${course.id}`} className="course-card">
      <Cover course={course} />
      <div className="course-card-body">
        <div className="card-meta">
          <span>{course.categoryName || 'Genel'}</span>
          <span>{levels[course.level]}</span>
        </div>
        <h3>{course.title}</h3>
        <p className="instructor">{course.instructorName}</p>
        <div className="card-bottom">
          <span className="rating">
            ★ <b>{(course.averageRating || 0).toFixed(1)}</b>
            <small>({course.totalReviews || 0})</small>
          </span>
          <strong>
            {(course.discountPrice ?? course.price) === 0
              ? 'Ücretsiz'
              : money(course.discountPrice ?? course.price)}
          </strong>
        </div>
      </div>
    </Link>
  );
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  const id = useId();
  const control = isValidElement(children)
    ? cloneElement(children as ReactElement<{ id?: string; 'aria-describedby'?: string }>, {
        id,
        'aria-describedby': hint ? `${id}-hint` : undefined,
      })
    : children;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {control}
      {hint && <small id={`${id}-hint`}>{hint}</small>}
    </div>
  );
}
export function Submit({ busy, children }: { busy: boolean; children: ReactNode }) {
  return (
    <button className="button primary" type="submit" disabled={busy}>
      {busy ? <LoaderCircle className="spin" size={17} /> : <Check size={17} />}{' '}
      {busy ? 'İşleniyor…' : children}
    </button>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-label={title}
      onClick={(e) => {
        if (e.target === ref.current) {
          const rect = ref.current.getBoundingClientRect();
          if (
            e.clientX < rect.left ||
            e.clientX > rect.right ||
            e.clientY < rect.top ||
            e.clientY > rect.bottom
          )
            onClose();
        }
      }}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="modal-heading">
        <h2>{title}</h2>
        <button className="icon-button" aria-label="Kapat" onClick={onClose}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Success({ children }: { children: ReactNode }) {
  return (
    <div className="notice success" role="status">
      <Check size={19} />
      <span>{children}</span>
    </div>
  );
}
