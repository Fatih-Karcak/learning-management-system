const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
let token: string | null = null;
let refresher: (() => Promise<boolean>) | null = null;
let refreshFlight: Promise<boolean> | null = null;
export function setTokenRefresher(handler: (() => Promise<boolean>) | null) {
  refresher = handler;
}
export async function refreshAuthentication() {
  if (!refresher) return false;
  if (!refreshFlight)
    refreshFlight = refresher().finally(() => {
      refreshFlight = null;
    });
  return refreshFlight;
}
export async function authenticatedFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  let sentToken = token;
  const send = async () => {
    sentToken = token;
    const headers = new Headers(options.headers);
    if (sentToken) headers.set('Authorization', `Bearer ${sentToken}`);
    else headers.delete('Authorization');
    return fetch(apiUrl(path), { ...options, headers });
  };
  let response = await send();
  if (response.status === 401 && !/^(?:\/api\/v1)?\/auth\//.test(path)) {
    if (token && sentToken !== token) response = await send();
    else if (await refreshAuthentication()) response = await send();
  }
  if (response.status === 401 && sentToken === token)
    window.dispatchEvent(new Event('lms:unauthorized'));
  return response;
}

export async function fetchResource(path: string): Promise<Blob> {
  if (!/^\/api\/v1\/media\/resources\/[0-9a-f-]{36}$/i.test(path))
    throw new ApiError('The file URL is invalid.', 400);
  const response = await authenticatedFetch(path);
  if (!response.ok)
    throw new ApiError(
      'The file could not be downloaded. Check your access permissions.',
      response.status,
    );
  return response.blob();
}
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
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json');
  let response: Response;
  try {
    response = await authenticatedFetch(path, { ...options, headers });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError('The server could not be reached. Check your connection and try again.', 0);
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    const message =
      payload?.message ||
      (
        {
          401: 'Your session may have expired. Please sign in again.',
          403: 'You do not have permission to perform this action.',
          404: 'The requested record was not found.',
          413: 'The file exceeds the size limit.',
          429: 'Too many requests. Please wait and try again.',
          502: 'This service is currently unreachable.',
          503: 'The service is temporarily unavailable.',
        } as Record<number, string>
      )[response.status] ||
      'The action could not be completed. Please try again.';
    throw new ApiError(message, response.status);
  }
  return payload?.data as T;
}
export const body = (data: unknown) => JSON.stringify(data);
export const money = (value: number, currency = 'TRY') =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(
    value || 0,
  );
export const date = (value: string) =>
  new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(value));
export const levels = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  ALL_LEVELS: 'All levels',
};
export const lessonTypes = {
  VIDEO: 'Video',
  ARTICLE: 'Article',
  QUIZ: 'Test',
  ASSIGNMENT: 'Assignment',
};
export const statusLabels: Record<string, string> = {
  PARTIALLY_REFUNDED: 'Partially refunded',
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
  REFUNDED: 'Refunded',
  CANCELLED: 'Cancelled',
  PENDING: 'Pending',
  SUCCESS: 'Successful',
  SUCCEEDED: 'Successful',
  FAILED: 'Failed',
  PROCESSING: 'Processing',
  READY: 'Ready',
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  PENDING_REVIEW: 'Under review',
  ARCHIVED: 'Archived',
  SENT: 'Sent',
  IN_PROGRESS: 'In progress',
  NOT_STARTED: 'Not started',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUBMITTED: 'Awaiting review',
  ACCEPTED: 'Accepted',
  CHANGES_REQUESTED: 'Changes requested',
  BANNED: 'Banned',
  INACTIVE: 'Inactive',
  PENDING_VERIFICATION: 'Awaiting email verification',
};
