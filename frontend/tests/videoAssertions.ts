import { expect, type Locator } from '@playwright/test';

export async function startDecodedVideo(video: Locator) {
  await expect(video).toBeVisible({ timeout: 15000 });
  await expect
    .poll(
      () =>
        video.evaluate((element) => {
          const media = element as HTMLVideoElement;
          return {
            buffered: media.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA,
            hasFrame: media.videoWidth > 0,
            error: media.error?.message || null,
          };
        }),
      {
        message: 'The browser must decode and buffer the HLS video before playback',
        timeout: 10000,
      },
    )
    .toMatchObject({ buffered: true, hasFrame: true, error: null });
  await video.evaluate((element) => {
    const media = element as HTMLVideoElement;
    media.muted = true;
    void media.play().catch((error: Error) => {
      media.dataset.playbackError = error.message;
    });
  });
  await expect
    .poll(
      () =>
        video.evaluate((element) => {
          const media = element as HTMLVideoElement;
          return {
            advancing: media.currentTime > 0.3,
            frames: media.getVideoPlaybackQuality().totalVideoFrames > 0,
            error: media.error?.message || media.dataset.playbackError || null,
          };
        }),
      { message: 'Playback must advance with decoded frames and no media error', timeout: 10000 },
    )
    .toEqual({ advancing: true, frames: true, error: null });
}
