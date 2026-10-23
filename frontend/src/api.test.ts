import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  api,
  ApiError,
  body,
  safeUrl,
  setAccessToken,
  setTokenRefresher,
  fetchResource,
} from './api';

const dispatchEvent = vi.fn();
beforeEach(() => {
  vi.stubGlobal('window', { location: { origin: 'https://learn.example' }, dispatchEvent });
  setAccessToken(null);
  setTokenRefresher(null);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
describe('API boundary', () => {
  it('attaches the access token as a header and unwraps responses', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(body({ success: true, data: { id: 'course' } }), { status: 200 }),
      );
    vi.stubGlobal('fetch', fetch);
    setAccessToken('access-token');
    expect(await api('/courses')).toEqual({ id: 'course' });
    const [url, options] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/courses');
    expect(options.headers.get('Authorization')).toBe('Bearer access-token');
    expect(url).not.toContain('access-token');
    expect(options.headers.has('X-User-Id')).toBe(false);
  });
  it('preserves multipart boundaries for media uploads', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(body({ success: true, data: {} })));
    vi.stubGlobal('fetch', fetch);
    const form = new FormData();
    form.set('file', new Blob(['image']), 'test.png');
    await api('/media/image', { method: 'POST', body: form });
    expect(fetch.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
  });
  it('preserves server validation messages and status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(body({ success: false, message: 'Bu kursa kayıtlı değilsiniz' }), {
          status: 403,
        }),
      ),
    );
    await expect(api('/progress/enrollment')).rejects.toMatchObject({
      message: 'Bu kursa kayıtlı değilsiniz',
      status: 403,
    });
  });
  it('expires client sessions when the gateway rejects authentication', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await expect(api('/users/me')).rejects.toBeInstanceOf(ApiError);
    expect(dispatchEvent.mock.calls[0][0].type).toBe('lms:unauthorized');
  });
  it('handles empty service error responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>upstream unavailable</html>', { status: 502 })),
    );
    await expect(api('/media/my')).rejects.toMatchObject({
      status: 502,
      message: 'İlgili hizmete şu anda ulaşılamıyor.',
    });
  });
  it('rejects application failures even with HTTP 200', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(new Response(body({ success: false, message: 'İşlem reddedildi' }))),
    );
    await expect(api('/courses')).rejects.toThrow('İşlem reddedildi');
  });
  it('allows only web links from API values', () => {
    expect(safeUrl('javascript:alert(1)')).toBeUndefined();
    expect(safeUrl('data:text/html,hello')).toBeUndefined();
    expect(safeUrl('/course')).toBe('https://learn.example/course');
    expect(safeUrl('https://storage.example/file')).toBe('https://storage.example/file');
  });
});

describe('Session renewal and protected resources', () => {
  it('shares one refresh across concurrent failures and retries with the new header', async () => {
    setAccessToken('expired');
    let refreshed = 0;
    setTokenRefresher(async () => {
      refreshed++;
      await new Promise((resolve) => setTimeout(resolve, 5));
      setAccessToken('fresh');
      return true;
    });
    const fetch = vi.fn(async (_url: string, options: RequestInit) =>
      new Headers(options.headers).get('Authorization') === 'Bearer fresh'
        ? new Response(body({ success: true, data: 'ok' }))
        : new Response(null, { status: 401 }),
    );
    vi.stubGlobal('fetch', fetch);
    expect(await Promise.all([api('/users/me'), api('/enrollments/my')])).toEqual(['ok', 'ok']);
    expect(refreshed).toBe(1);
    expect(fetch).toHaveBeenCalledTimes(4);
    expect(dispatchEvent).not.toHaveBeenCalled();
  });
  it('never retries a login endpoint with another session refresh', async () => {
    const refresh = vi.fn();
    setTokenRefresher(refresh);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await expect(api('/auth/login', { method: 'POST', body: body({}) })).rejects.toMatchObject({
      status: 401,
    });
    expect(refresh).not.toHaveBeenCalled();
  });
  it('rejects external resource addresses before attaching credentials', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    setAccessToken('private');
    await expect(fetchResource('https://outside.example/file')).rejects.toMatchObject({
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
  });
});

it('does not clear a newer session after an older in-flight request returns 401', async () => {
  let release: (value: Response) => void = () => {};
  const oldResponse = new Promise<Response>((resolve) => {
    release = resolve;
  });
  const fetch = vi
    .fn()
    .mockImplementationOnce(() => oldResponse)
    .mockResolvedValueOnce(new Response(body({ success: true, data: 'new-session' })));
  vi.stubGlobal('fetch', fetch);
  setAccessToken('old');
  const pending = api('/users/me');
  setAccessToken('new');
  release(new Response(null, { status: 401 }));
  expect(await pending).toBe('new-session');
  expect(dispatchEvent).not.toHaveBeenCalled();
  expect(fetch.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer new');
});
