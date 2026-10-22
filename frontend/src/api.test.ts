import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, body, safeUrl, setAccessToken } from './api';

const dispatchEvent = vi.fn();
beforeEach(() => {
  vi.stubGlobal('window', { location: { origin: 'https://learn.example' }, dispatchEvent });
  setAccessToken(null);
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
