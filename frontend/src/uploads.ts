import { api, body, ApiError } from './api';
import type { Media } from './types';

interface Upload {
  id: string;
  status: string;
  totalBytes: number;
  receivedBytes: number;
  chunkBytes: number;
  parts: { index: number; size: number; sha256: string }[];
}
async function digest(bytes: ArrayBuffer) {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}
export async function uploadVideo(
  file: File,
  courseId: string | undefined,
  userId: string,
  progress: (sent: number, total: number) => void,
  signal: AbortSignal,
): Promise<Media> {
  // Store resumable identifiers, never access tokens or file contents.
  const key = `lms.upload.${userId}.${courseId || 'library'}.${file.name}.${file.size}.${file.lastModified}`;
  let upload: Upload | undefined;
  const saved = localStorage.getItem(key);
  if (saved) {
    try {
      upload = await api<Upload>(`/media/uploads/${saved}`, { signal });
    } catch (error) {
      if (!(error instanceof ApiError) || ![404, 410].includes(error.status)) throw error;
      localStorage.removeItem(key);
    }
  }
  if (!upload || upload.status === 'CANCELLED') {
    upload = await api<Upload>('/media/uploads', {
      method: 'POST',
      signal,
      body: body({
        fileName: file.name,
        contentType: file.type || 'video/mp4',
        totalBytes: file.size,
        courseId,
      }),
    });
    localStorage.setItem(key, upload.id);
  }
  progress(upload.receivedBytes, file.size);
  if (upload.status !== 'COMPLETED') {
    // Check the resumed prefix locally so a different file cannot be spliced into the old upload.
    for (const part of upload.parts) {
      const bytes = await file
        .slice(part.index * upload.chunkBytes, part.index * upload.chunkBytes + part.size)
        .arrayBuffer();
      if ((await digest(bytes)) !== part.sha256)
        throw new Error('This is a different file. Select the original file to resume.');
      signal.throwIfAborted();
    }
    for (let offset = upload.receivedBytes; offset < file.size; offset += upload.chunkBytes) {
      signal.throwIfAborted();
      const bytes = await file
        .slice(offset, Math.min(offset + upload.chunkBytes, file.size))
        .arrayBuffer();
      const checksum = await digest(bytes);
      const path = `/media/uploads/${upload.id}/parts/${Math.floor(offset / upload.chunkBytes)}`;
      for (let attempt = 0; ; attempt++) {
        try {
          await api<Upload>(path, {
            method: 'PUT',
            signal,
            headers: {
              'Content-Type': 'application/octet-stream',
              'Upload-Checksum': checksum,
            },
            body: bytes,
          });
          break;
        } catch (error) {
          if (
            signal.aborted ||
            attempt >= 3 ||
            !(error instanceof ApiError) ||
            (error.status !== 0 && error.status !== 429 && error.status < 500)
          )
            throw error;
          await new Promise<void>((resolve, reject) => {
            const timer = setTimeout(
              () => {
                signal.removeEventListener('abort', abort);
                resolve();
              },
              500 * 2 ** attempt,
            );
            const abort = () => {
              clearTimeout(timer);
              reject(signal.reason);
            };
            signal.addEventListener('abort', abort, { once: true });
          });
        }
      }
      progress(Math.min(offset + bytes.byteLength, file.size), file.size);
    }
  }
  const result = await api<Media>(`/media/uploads/${upload.id}/complete`, {
    method: 'POST',
    signal,
  });
  localStorage.removeItem(key);
  return result;
}
