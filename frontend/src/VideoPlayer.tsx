import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js/light';
import { apiUrl, getAccessToken, safeUrl } from './api';
import { ErrorBox } from './ui';

export default function VideoPlayer({
  src,
  initialTime = 0,
  onSave,
}: {
  src: string;
  initialTime?: number;
  onSave?: (seconds: number) => void;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<Error | null>(null);
  const save = useRef(onSave);
  save.current = onSave;
  useEffect(() => {
    const video = ref.current;
    if (!video) return;
    setError(null);
    const url = src.startsWith('/api/') ? apiUrl(src) : safeUrl(src);
    if (!url) {
      setError(new Error('Geçerli bir video adresi bulunamadı.'));
      return;
    }
    const absolute = new URL(url, window.location.origin);
    const apiOrigin = new URL(apiUrl('/'), window.location.origin).origin;
    const protectedMedia =
      absolute.origin === apiOrigin && absolute.pathname.startsWith('/api/v1/media/');
    const restore = () => {
      if (initialTime > 0)
        video.currentTime = Math.min(
          initialTime,
          Number.isFinite(video.duration) ? Math.max(0, video.duration - 1) : initialTime,
        );
    };
    video.addEventListener('loadedmetadata', restore, { once: true });
    let hls: Hls | undefined;
    if (/\.m3u8(?:\?|$)/i.test(url)) {
      if (Hls.isSupported()) {
        hls = new Hls({
          xhrSetup(xhr, requestUrl) {
            const target = new URL(requestUrl, window.location.origin);
            const token = getAccessToken();
            if (
              token &&
              target.origin === apiOrigin &&
              target.pathname.startsWith('/api/v1/media/')
            )
              xhr.setRequestHeader('Authorization', `Bearer ${token}`);
          },
        });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal)
            setError(
              new Error(
                'Video yüklenemedi. Erişim iznini ve videonun hazır olduğunu kontrol edin.',
              ),
            );
        });
      } else if (!protectedMedia && video.canPlayType('application/vnd.apple.mpegurl'))
        video.src = url;
      else
        setError(
          new Error(
            'Bu tarayıcı korumalı video akışını desteklemiyor. Güncel Chrome, Firefox veya Edge kullanın.',
          ),
        );
    } else video.src = url;
    return () => {
      hls?.destroy();
      video.removeEventListener('loadedmetadata', restore);
      video.pause();
      video.removeAttribute('src');
      video.load();
    };
    // A lesson change, not a progress query refresh, creates a new player.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src]);
  return (
    <div className="video-player">
      {error ? (
        <ErrorBox error={error} />
      ) : (
        <video
          ref={ref}
          controls
          playsInline
          preload="metadata"
          onPause={(e) => save.current?.(Math.floor(e.currentTarget.currentTime))}
          onEnded={(e) => save.current?.(Math.floor(e.currentTarget.currentTime))}
          onError={() =>
            setError(new Error('Video oynatılamadı. Dosya adresini ve erişim iznini kontrol edin.'))
          }
        >
          <track kind="captions" label="Altyazı" />
        </video>
      )}
    </div>
  );
}
