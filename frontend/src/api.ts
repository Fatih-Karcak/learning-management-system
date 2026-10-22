const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
let token: string | null = null;
export function setAccessToken(value: string | null) {
  token = value;
}
export function getAccessToken() {
  return token;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
export function apiUrl(path: string) {
  return `${base}${path.startsWith('/api/') ? path : `/api/v1${path}`}`;
}
export function safeUrl(value?: string | null): string | undefined {
  if (!value) return undefined;
  try {
    const u = new URL(value, window.location.origin);
    return ['http:', 'https:'].includes(u.protocol) ? u.href : undefined;
  } catch {
    return undefined;
  }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  let response: Response;
  try {
    response = await fetch(apiUrl(path), { ...options, headers });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError('Sunucuya ulaşılamıyor. Bağlantınızı kontrol edip tekrar deneyin.', 0);
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    const message =
      payload?.message ||
      (
        {
          401: 'Oturumunuz sona ermiş olabilir. Lütfen yeniden giriş yapın.',
          403: 'Bu işlem için yetkiniz bulunmuyor.',
          404: 'Aradığınız kayıt bulunamadı.',
          413: 'Dosya boyutu izin verilen sınırı aşıyor.',
          429: 'Çok fazla istek gönderdiniz. Lütfen biraz bekleyin.',
          502: 'İlgili hizmete şu anda ulaşılamıyor.',
          503: 'Hizmet geçici olarak kullanılamıyor.',
        } as Record<number, string>
      )[response.status] ||
      'İşlem tamamlanamadı. Lütfen tekrar deneyin.';
    if (response.status === 401) window.dispatchEvent(new Event('lms:unauthorized'));
    throw new ApiError(message, response.status);
  }
  return payload?.data as T;
}
export const body = (data: unknown) => JSON.stringify(data);
export const money = (value: number, currency = 'TRY') =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency, maximumFractionDigits: 2 }).format(
    value || 0,
  );
export const date = (value: string) =>
  new Intl.DateTimeFormat('tr-TR', { dateStyle: 'medium' }).format(new Date(value));
export const levels = {
  BEGINNER: 'Başlangıç',
  INTERMEDIATE: 'Orta seviye',
  ADVANCED: 'İleri seviye',
  ALL_LEVELS: 'Tüm seviyeler',
};
export const lessonTypes = { VIDEO: 'Video', ARTICLE: 'Okuma', QUIZ: 'Test', ASSIGNMENT: 'Ödev' };
export const statusLabels: Record<string, string> = {
  PARTIALLY_REFUNDED: 'Kısmen iade edildi',
  ACTIVE: 'Aktif',
  COMPLETED: 'Tamamlandı',
  REFUNDED: 'İade edildi',
  CANCELLED: 'İptal edildi',
  PENDING: 'Bekliyor',
  SUCCESS: 'Başarılı',
  SUCCEEDED: 'Başarılı',
  FAILED: 'Başarısız',
  PROCESSING: 'İşleniyor',
  READY: 'Hazır',
  DRAFT: 'Taslak',
  PUBLISHED: 'Yayında',
  PENDING_REVIEW: 'İncelemede',
  ARCHIVED: 'Arşivlendi',
  SENT: 'Gönderildi',
  IN_PROGRESS: 'Devam ediyor',
  NOT_STARTED: 'Başlanmadı',
  APPROVED: 'Onaylandı',
  REJECTED: 'Reddedildi',
  PENDING_VERIFICATION: 'E-posta bekleniyor',
};
