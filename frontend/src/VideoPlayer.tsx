import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js/light';
import { useQuery } from '@tanstack/react-query';
import { api, apiUrl, getAccessToken, safeUrl } from './api';
import type { Media } from './types';
import { ErrorBox, Loading } from './ui';

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
  const [attempt, setAttempt] = useState(0);
  const url = src.startsWith('/api/') ? apiUrl(src) : safeUrl(src);
  const absolute = url ? new URL(url, window.location.origin) : undefined;
  const apiOrigin = new URL(apiUrl('/'), window.location.origin).origin;
  const mediaId =
    absolute?.origin === apiOrigin
      ? absolute.pathname.match(/^\/api\/v1\/media\/([\w-]+)\/manifest\.m3u8$/)?.[1]
      : undefined;
  const media = useQuery({
    queryKey: ['playback-media', mediaId],
    queryFn: () => api<Media>(`/media/${mediaId}`),
    enabled: !!mediaId,
    refetchInterval: (q) => (q.state.data?.status === 'PROCESSING' ? 3000 : false),
  });
  const waiting = !!mediaId && (media.isPending || media.data?.status === 'PROCESSING');
  const ready = !mediaId || media.data?.status === 'READY';
  const save = useRef(onSave);
  save.current = onSave;
  useEffect(() => {
    const video = ref.current;
    if (!video) return;
    setError(null);
    if (!ready) return;
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
                data.response?.code === 403
                  ? 'Bu videoyu izlemek için ilgili kursa kayıtlı olmalısınız.'
                  : data.response?.code === 401
                    ? 'Video oturumunuz sona erdi. Yeniden giriş yapın.'
                    : 'Video yüklenemedi. Tekrar deneyin; sorun sürerse eğitmene bildirin.',
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
  }, [src, ready, attempt]);
  const failure =
    mediaId && media.data?.status === 'FAILED'
      ? new Error('Video işlenemedi. Eğitmenin videoyu yeniden yüklemesi gerekiyor.')
      : null;
  const visibleError = (mediaId ? media.error : null) || failure || error;
  return (
    <div className="video-player">
      {waiting &&
        (media.data?.status === 'PROCESSING' ? (
          <p role="status" className="notice">
            Video hazırlanıyor. İşleme tamamlandığında burada izleyebilirsiniz.
          </p>
        ) : (
          <Loading />
        ))}
      {visibleError && (
        <ErrorBox
          error={visibleError}
          retry={() => {
            setError(null);
            setAttempt((value) => value + 1);
            if (mediaId) void media.refetch();
          }}
        />
      )}
      <video
        ref={ref}
        hidden={waiting || !!visibleError}
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
    </div>
  );
}
