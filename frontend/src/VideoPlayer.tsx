import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js/light';
import { useQuery } from '@tanstack/react-query';
import { api, apiUrl, getAccessToken, safeUrl, fetchResource, refreshAuthentication } from './api';
import type { Media, Material } from './types';
import { ErrorBox, Loading } from './ui';

export default function VideoPlayer({
  src,
  initialTime = 0,
  onSave,
  onPlayback,
  onPosition,
  seekTo,
  captions = [],
}: {
  captions?: Material[];
  src: string;
  initialTime?: number;
  onSave?: (seconds: number) => void;
  onPlayback?: (seconds: number, playing: boolean) => void;
  onPosition?: (seconds: number) => void;
  seekTo?: { seconds: number; nonce: number };
}) {
  const ref = useRef<HTMLVideoElement>(null);
  const [tracks, setTracks] = useState<{ src: string; label: string; language: string }[]>([]);
  const [captionError, setCaptionError] = useState(false);
  const captionKey = JSON.stringify(captions);
  useEffect(() => {
    let live = true;
    const urls: string[] = [];
    setCaptionError(false);
    setTracks([]);
    void Promise.all(
      captions.map(async (m) => {
        let blob: Blob;
        if (m.url.startsWith('/api/v1/media/resources/')) blob = await fetchResource(m.url);
        else {
          const response = await fetch(safeUrl(m.url) || '', { credentials: 'omit' });
          if (!response.ok) throw new Error('Subtitles could not be loaded');
          blob = await response.blob();
        }
        if (blob.size > 1024 * 1024) throw new Error('The subtitle file is too large');
        const url = URL.createObjectURL(new Blob([blob], { type: 'text/vtt' }));
        urls.push(url);
        if (!live) {
          URL.revokeObjectURL(url);
          return null;
        }
        return { src: url, label: m.title, language: m.language || 'en' };
      }),
    )
      .then((items) => {
        if (live) setTracks(items.filter((x): x is NonNullable<typeof x> => x !== null));
      })
      .catch(() => {
        if (live) setCaptionError(true);
      });
    return () => {
      live = false;
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
    // Each caption change reloads tracks, independently of progress polling.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [captionKey]);
  const [error, setError] = useState<Error | null>(null);
  const engine = useRef<Hls | undefined>(undefined);
  const [qualities, setQualities] = useState<{ height: number; index: number }[]>([]);
  const [quality, setQuality] = useState(-1);
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
  const playback = useRef(onPlayback);
  playback.current = onPlayback;
  const position = useRef(onPosition);
  position.current = onPosition;
  const lastReport = useRef(0);
  const lastPosition = useRef(-1);
  function report(video: HTMLVideoElement, playing: boolean) {
    lastReport.current = performance.now();
    playback.current?.(video.currentTime, playing);
  }
  useEffect(() => {
    if (seekTo && ref.current && Number.isFinite(ref.current.duration))
      ref.current.currentTime = Math.min(Math.max(0, seekTo.seconds), ref.current.duration);
  }, [seekTo]);
  useEffect(() => {
    const video = ref.current;
    if (!video) return;
    setError(null);
    setQualities([]);
    setQuality(-1);
    engine.current = undefined;
    if (!ready) return;
    const url = src.startsWith('/api/') ? apiUrl(src) : safeUrl(src);
    if (!url) {
      setError(new Error('A valid video URL was not found.'));
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
    let refreshing = false;
    if (/\.m3u8(?:\?|$)/i.test(url)) {
      if (
        protectedMedia &&
        window.MediaSource &&
        !MediaSource.isTypeSupported('video/mp4; codecs="avc1.42E01E"')
      ) {
        setError(
          new Error(
            'Your browser does not support H.264 video. Try an up-to-date Chrome or Edge browser.',
          ),
        );
        video.removeEventListener('loadedmetadata', restore);
        return;
      }
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
        engine.current = hls;
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          setQualities(hls!.levels.map((level, index) => ({ height: level.height, index })));
          setQuality(-1);
        });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal && data.response?.code === 401 && !refreshing) {
            refreshing = true;
            void refreshAuthentication()
              .then((ok) => {
                if (ok) {
                  hls?.startLoad();
                } else {
                  window.dispatchEvent(new Event('lms:unauthorized'));
                  setError(new Error('Your video session has expired. Sign in again.'));
                }
              })
              .catch(() =>
                setError(new Error('Your session could not be refreshed. Check your connection.')),
              )
              .finally(() => {
                refreshing = false;
              });
            return;
          }
          if (data.fatal)
            setError(
              new Error(
                data.response?.code === 403
                  ? 'Enroll in this course to watch this video.'
                  : data.response?.code === 401
                    ? 'Your video session has expired. Sign in again.'
                    : 'The video could not be loaded. Try again or contact your instructor.',
              ),
            );
        });
      } else if (!protectedMedia && video.canPlayType('application/vnd.apple.mpegurl'))
        video.src = url;
      else
        setError(
          new Error(
            'Your browser does not support protected video playback. Use an up-to-date Chrome, Firefox or Edge browser.',
          ),
        );
    } else video.src = url;
    return () => {
      engine.current = undefined;
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
      ? new Error('The video could not be processed. Ask your instructor to upload it again.')
      : null;
  const visibleError = (mediaId ? media.error : null) || failure || error;
  return (
    <div className="video-player">
      {qualities.length > 1 && (
        <label>
          Video quality
          <select
            aria-label="Video quality"
            value={quality}
            onChange={(event) => {
              const next = Number(event.target.value);
              setQuality(next);
              if (engine.current) engine.current.currentLevel = next;
            }}
          >
            <option value={-1}>Auto</option>
            {qualities.map((level) => (
              <option key={level.index} value={level.index}>
                {level.height}p
              </option>
            ))}
          </select>
        </label>
      )}
      {waiting &&
        (media.data?.status === 'PROCESSING' ? (
          <p role="status" className="notice">
            Your video is being processed. It will appear here when it is ready.
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
      {captionError && (
        <p role="status" className="notice warning">
          Subtitles could not be loaded. Check the lesson resources.
        </p>
      )}
      <video
        ref={ref}
        hidden={waiting || !!visibleError}
        controls
        playsInline
        preload="metadata"
        onPlay={(e) => report(e.currentTarget, true)}
        onPause={(e) => {
          report(e.currentTarget, false);
          save.current?.(Math.floor(e.currentTarget.currentTime));
        }}
        onEnded={(e) => {
          report(e.currentTarget, false);
          save.current?.(Math.floor(e.currentTarget.currentTime));
        }}
        onSeeking={(e) => report(e.currentTarget, false)}
        onSeeked={(e) => report(e.currentTarget, !e.currentTarget.paused)}
        onTimeUpdate={(e) => {
          const video = e.currentTarget;
          const current = Math.floor(video.currentTime);
          if (current !== lastPosition.current) {
            lastPosition.current = current;
            position.current?.(current);
          }
          if (!video.paused && !video.seeking && performance.now() - lastReport.current >= 5000)
            report(video, true);
        }}
        onError={() =>
          setError(
            new Error(
              'The video could not be played. Check the file URL and your access permissions.',
            ),
          )
        }
      >
        {tracks.map((t, i) => (
          <track
            key={t.src}
            kind="captions"
            label={t.label}
            srcLang={t.language}
            src={t.src}
            default={i === 0}
          />
        ))}
      </video>
    </div>
  );
}
