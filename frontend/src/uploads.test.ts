import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { uploadVideo } from './uploads';
vi.mock('./api', async () => {
  const actual = await vi.importActual<typeof import('./api')>('./api');
  return { ...actual, api: vi.fn() };
});
const request = vi.mocked(api);
beforeEach(() => {
  request.mockReset();
  const values = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) || null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  });
});
describe('resumable video uploads', () => {
  it('resumes from the acknowledged offset and never resends a completed chunk', async () => {
    const file = new File([new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8])], 'lesson.mp4', {
      type: 'video/mp4',
      lastModified: 1,
    });
    const checksum = Array.from(
      new Uint8Array(await crypto.subtle.digest('SHA-256', new Uint8Array([1, 2, 3, 4]))),
    )
      .map((v) => v.toString(16).padStart(2, '0'))
      .join('');
    let sent = 0;
    request.mockImplementation(async (path, options) => {
      if (path === '/media/uploads')
        return {
          id: 'upload-1',
          status: 'OPEN',
          totalBytes: 8,
          receivedBytes: 0,
          chunkBytes: 4,
          parts: [],
        };
      if (path === '/media/uploads/upload-1')
        return {
          id: 'upload-1',
          status: 'OPEN',
          totalBytes: 8,
          receivedBytes: 4,
          chunkBytes: 4,
          parts: [{ index: 0, size: 4, sha256: checksum }],
        };
      if (path.endsWith('/parts/0')) {
        sent++;
        return {};
      }
      if (path.endsWith('/parts/1')) {
        sent++;
        expect(options?.body).toBeInstanceOf(ArrayBuffer);
        return {};
      }
      if (path.endsWith('/complete')) return { id: 'video-1', status: 'PROCESSING' };
      throw new Error(`Unexpected request ${path}`);
    });
    const pause = new AbortController();
    await expect(
      uploadVideo(
        file,
        'course-1',
        'teacher-1',
        (sent) => {
          if (sent === 4) pause.abort();
        },
        pause.signal,
      ),
    ).rejects.toBeDefined();
    const result = await uploadVideo(
      file,
      'course-1',
      'teacher-1',
      () => {},
      new AbortController().signal,
    );
    expect(result.id).toBe('video-1');
    expect(sent).toBe(2);
    expect(request.mock.calls.filter(([path]) => path === '/media/uploads')).toHaveLength(1);
  });
  it('does not retry permission failures', async () => {
    request.mockRejectedValue(new ApiError('Forbidden', 403));
    await expect(
      uploadVideo(
        new File(['x'], 'lesson.mp4', { type: 'video/mp4' }),
        undefined,
        'teacher',
        () => {},
        new AbortController().signal,
      ),
    ).rejects.toMatchObject({ status: 403 });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
