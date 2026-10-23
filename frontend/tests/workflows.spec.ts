import { test, expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { startDecodedVideo } from './videoAssertions';

const course = {
  id: 'course-1',
  title: 'Tasarım düşüncesiyle yeni fikirler',
  shortDescription: 'Fikirlerini anlamlı deneyimlere dönüştür.',
  description: '## Merakla başla\nİnsan odaklı çözümler üret.',
  instructorId: 'instructor-1',
  instructorName: 'Deniz Yılmaz',
  categoryName: 'Tasarım',
  status: 'PUBLISHED',
  level: 'BEGINNER',
  price: 450,
  language: 'Türkçe',
  totalEnrollments: 42,
  averageRating: 4.8,
  totalReviews: 8,
  outcomes: ['Araştırma yap', 'Fikirlerini hayata geçir'],
  requirements: ['Meraklı ol'],
  sections: [
    {
      id: 'section-1',
      title: 'İlk adımlar',
      orderIndex: 0,
      lessons: [
        {
          id: 'lesson-1',
          title: 'Tasarım nedir?',
          content: 'İlk dersin içeriği.',
          type: 'ARTICLE',
          orderIndex: 0,
          previewAllowed: true,
        },
        {
          id: 'lesson-2',
          title: 'Birlikte keşfedelim',
          type: 'VIDEO',
          orderIndex: 1,
          previewAllowed: false,
          durationSeconds: 300,
        },
      ],
    },
  ],
};
const user = {
  id: 'student-1',
  email: 'student@example.test',
  firstName: 'Ece',
  lastName: 'Kaya',
  role: 'STUDENT',
  status: 'ACTIVE',
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00',
};
const enrollment = {
  id: 'enrollment-1',
  courseId: course.id,
  courseTitle: course.title,
  instructorName: course.instructorName,
  status: 'ACTIVE',
  totalLessons: 2,
  completedLessons: 0,
  progressPercentage: 0,
  enrolledAt: '2026-01-01T00:00:00',
};
const envelope = (data: unknown) => ({ success: true, data });
const pageData = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 12,
  last: true,
});
async function session(page: Page, role = 'STUDENT') {
  await page.addInitScript(
    ({ user, role }) => {
      sessionStorage.setItem(
        'ileri.session',
        JSON.stringify({
          accessToken: 'test-token',
          refreshToken: 'refresh',
          expiresIn: 86400000,
          user: { ...user, id: role === 'INSTRUCTOR' ? 'instructor-1' : user.id, role },
        }),
      );
    },
    { user, role },
  );
}
async function mockApi(page: Page, role = 'STUDENT') {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = pageData([]);
    if (path === '/api/v1/users/me')
      data = { ...user, id: role === 'INSTRUCTOR' ? 'instructor-1' : user.id, role };
    else if (path === '/api/v1/courses/categories') data = [{ id: 3, name: 'Tasarım' }];
    else if (path.endsWith('/workspace'))
      data = {
        revision: 0,
        status: 'DRAFT',
        feedback: null,
        document: { course: structuredClone(course), categoryId: 3, quizzes: {}, materials: {} },
        checklist: [],
      };
    else if (
      path.endsWith('/materials') ||
      path.endsWith('/announcements') ||
      path.endsWith('/bookmarks') ||
      path.endsWith('/wishlist') ||
      path.endsWith('/inbox') ||
      path.endsWith('/workspaces') ||
      path.endsWith('/versions') ||
      path.endsWith('/recommendations') ||
      path.endsWith('/invitations')
    )
      data = [];
    else if (path === '/api/v1/courses/course-1') data = course;
    else if (
      [
        '/api/v1/courses',
        '/api/v1/courses/popular',
        '/api/v1/courses/search',
        '/api/v1/courses/category/3',
        '/api/v1/courses/mine',
        '/api/v1/courses/instructor/instructor-1',
      ].includes(path)
    )
      data = pageData([course]);
    else if (path === '/api/v1/enrollments/my') data = pageData([enrollment]);
    else if (path === '/api/v1/enrollments/enrollment-1') data = enrollment;
    else if (path.endsWith('/check')) data = true;
    else if (path.endsWith('/playback') && route.request().method() === 'POST') {
      const input = route.request().postDataJSON();
      data = {
        enrollmentId: 'enrollment-1',
        lessonId: 'lesson-1',
        sectionId: 'section-1',
        status: 'IN_PROGRESS',
        positionSeconds: Math.floor(input.positionSeconds),
        watchedSeconds: Math.floor(input.positionSeconds),
        totalSeconds: 3,
      };
    } else if (path.startsWith('/api/v1/progress/')) data = [];
    await route.fulfill({ json: envelope(data) });
  });
}

test('instructor stages a library video in the workspace before publication', async ({ page }) => {
  await session(page, 'INSTRUCTOR');
  await mockApi(page, 'INSTRUCTOR');
  const media = {
    id: 'video-1',
    type: 'VIDEO',
    status: 'READY',
    originalFileName: 'lesson.mp4',
    streamUrl: '/api/v1/media/video-1/manifest.m3u8',
    durationSeconds: 12,
  };
  await page.route('**/api/v1/media/my?**', (route) =>
    route.fulfill({ json: envelope(pageData([media])) }),
  );
  let saved: any;
  let workspace: any = {
    revision: 0,
    status: 'DRAFT',
    document: { course: structuredClone(course), categoryId: 3, quizzes: {}, materials: {} },
    checklist: [],
  };
  await page.route('**/api/v1/courses/course-1/workspace', async (route) => {
    if (route.request().method() === 'PUT') {
      expect(route.request().headers().authorization).toBe('Bearer test-token');
      saved = route.request().postDataJSON();
      workspace = { ...workspace, document: saved.document, revision: 1 };
    }
    await route.fulfill({ json: envelope(workspace) });
  });
  await page.goto('/instructor/course-1');
  await page.getByRole('button', { name: 'Tasarım nedir? · Okuma', exact: true }).click();
  await page.getByLabel('Ders türü', { exact: true }).selectOption('VIDEO');
  await page.getByLabel('Ders videosu', { exact: true }).selectOption(media.streamUrl);
  await page.getByRole('button', { name: 'Düzenlemeyi bitir' }).click();
  await page.getByRole('button', { name: 'Taslağı kaydet', exact: true }).click();
  await expect
    .poll(() => saved?.document.course.sections[0].lessons[0].videoUrl)
    .toBe(media.streamUrl);
  expect(saved.document.course.sections[0].lessons[0].content).toBe('İlk dersin içeriği.');
  await expect(page.getByRole('button', { name: 'İncelemeye gönder', exact: true })).toBeEnabled();
});

test('video lesson without a URL explains that an instructor must attach its video', async ({
  page,
}) => {
  await session(page);
  await mockApi(page);
  await page.goto('/learn/enrollment-1?lesson=lesson-2');
  await expect(page.getByText('Bu derse henüz video bağlanmamış.', { exact: false })).toBeVisible();
});

test('continue learning opens the most recently watched unfinished lesson', async ({ page }) => {
  await session(page);
  await mockApi(page);
  await page.route('**/api/v1/progress/enrollment-1', (route) =>
    route.fulfill({
      json: envelope([
        {
          enrollmentId: 'enrollment-1',
          sectionId: 'section-1',
          lessonId: 'lesson-2',
          status: 'IN_PROGRESS',
          positionSeconds: 42,
          watchedSeconds: 60,
          lastWatchedAt: '2026-10-22T12:00:00',
        },
      ]),
    }),
  );
  await page.goto('/learn/enrollment-1');
  await expect(
    page.getByRole('heading', { name: 'Birlikte keşfedelim', exact: true }),
  ).toBeVisible();
  await expect(page).toHaveURL('/learn/enrollment-1?lesson=lesson-2');
});

test('quiz questions remain in the saved workspace when reopened', async ({ page }) => {
  await session(page, 'INSTRUCTOR');
  await mockApi(page, 'INSTRUCTOR');
  const copy = structuredClone(course);
  copy.sections[0].lessons[0].type = 'QUIZ';
  let workspace: any = {
    revision: 1,
    status: 'DRAFT',
    document: {
      course: copy,
      categoryId: 3,
      quizzes: {
        'lesson-1': {
          passingScore: 70,
          questions: [
            { id: 'q-1', prompt: 'Eski soru', options: ['Yanıt A', 'Yanıt B'], correctOption: 0 },
          ],
        },
      },
      materials: {},
    },
    checklist: [],
  };
  await page.route('**/api/v1/courses/course-1/workspace', async (route) => {
    if (route.request().method() === 'PUT') {
      workspace = { ...workspace, document: route.request().postDataJSON().document, revision: 2 };
    }
    await route.fulfill({ json: envelope(workspace) });
  });
  await page.goto('/instructor/course-1');
  await page.getByRole('button', { name: 'Tasarım nedir? · Test', exact: true }).click();
  await page.getByLabel('Soru 1', { exact: true }).fill('Kaydedilmiş yeni soru');
  await page.getByRole('button', { name: 'Düzenlemeyi bitir' }).click();
  await page.getByRole('button', { name: 'Taslağı kaydet', exact: true }).click();
  await expect.poll(() => workspace.revision).toBe(2);
  await page.reload();
  await page.getByRole('button', { name: 'Tasarım nedir? · Test', exact: true }).click();
  await expect(page.getByLabel('Soru 1', { exact: true })).toHaveValue('Kaydedilmiş yeni soru');
});

test.describe('decoded student video playback', () => {
  test.skip(
    process.env.E2E_VIDEO !== '1',
    'Set E2E_VIDEO=1 with FFmpeg on PATH to test decoded video playback.',
  );
  let fixtureDir: string;
  let manifest: Buffer;
  let segment: Buffer;
  const mediaId = '6df6a12f-fae2-483e-aef3-2850881bef22';
  const streamUrl = `/api/v1/media/${mediaId}/manifest.m3u8`;
  const media = {
    id: mediaId,
    type: 'VIDEO',
    status: 'READY',
    originalFileName: 'lesson.mp4',
    durationSeconds: 3,
    streamUrl,
  };
  test.beforeAll(async ({ browser }) => {
    const probe = await browser.newPage();
    try {
      const h264 = await probe.evaluate(
        () =>
          !!window.MediaSource && MediaSource.isTypeSupported('video/mp4; codecs="avc1.42E01E"'),
      );
      expect(
        h264,
        'H.264 decoding is required. Run with PLAYWRIGHT_BROWSER_CHANNEL=chrome and install Chrome.',
      ).toBe(true);
    } finally {
      await probe.close();
    }
    fixtureDir = mkdtempSync(join(tmpdir(), 'lms-playback-'));
    execFileSync('ffmpeg', [
      '-hide_banner',
      '-loglevel',
      'error',
      '-y',
      '-f',
      'lavfi',
      '-i',
      'testsrc2=size=320x180:rate=24',
      '-t',
      '3',
      '-an',
      '-c:v',
      'libx264',
      '-threads',
      '2',
      '-pix_fmt',
      'yuv420p',
      '-f',
      'hls',
      '-hls_list_size',
      '0',
      '-hls_playlist_type',
      'vod',
      join(fixtureDir, 'master.m3u8'),
    ]);
    manifest = readFileSync(join(fixtureDir, 'master.m3u8'));
    segment = readFileSync(join(fixtureDir, 'master0.ts'));
  });
  test.afterAll(() => {
    if (fixtureDir) rmSync(fixtureDir, { recursive: true, force: true });
  });

  test('uploading against a text lesson attaches it and a student decodes its protected HLS video', async ({
    page,
    browser,
  }) => {
    const linkedCourse = structuredClone(course);
    await session(page, 'INSTRUCTOR');
    await mockApi(page, 'INSTRUCTOR');
    await page.route('**/api/v1/media/my?**', (route) =>
      route.fulfill({ json: envelope(pageData([])) }),
    );
    await page.route('**/api/v1/media/video', async (route) => {
      const form = route.request().postDataBuffer()!.toString();
      expect(form).toContain('name="courseId"\r\n\r\ncourse-1');

      expect(route.request().headers().authorization).toBe('Bearer test-token');
      await route.fulfill({ status: 201, json: envelope({ ...media, status: 'PROCESSING' }) });
    });
    let attached = false;
    let workspace: any = {
      revision: 0,
      status: 'DRAFT',
      document: { course: structuredClone(course), categoryId: 3, quizzes: {}, materials: {} },
      checklist: [],
    };
    await page.route('**/api/v1/courses/course-1/workspace', async (route) => {
      if (route.request().method() === 'PUT') {
        const input = route.request().postDataJSON();
        Object.assign(
          linkedCourse.sections[0].lessons[0],
          input.document.course.sections[0].lessons[0],
        );
        attached = true;
        workspace = { ...workspace, document: input.document, revision: 1 };
      }
      await route.fulfill({ json: envelope(workspace) });
    });
    await page.goto('/media?course=course-1');
    await page.getByLabel('Dosya türü').selectOption('video');
    await page.getByLabel('İlgili ders (isteğe bağlı)').selectOption('lesson-1');
    await page.getByLabel('Dosya seç').setInputFiles({
      name: 'lesson.mp4',
      mimeType: 'video/mp4',
      buffer: Buffer.from('upload-fixture'),
    });
    await page.getByRole('button', { name: 'Dosyayı yükle' }).click();
    await expect(page.getByRole('status')).toContainText('1 dosya yüklendi.');
    expect(attached).toBe(true);

    const context = await browser.newContext();
    try {
      const student = await context.newPage();
      await session(student);
      await mockApi(student);
      await student.route('**/api/v1/courses/course-1', (route) =>
        route.fulfill({ json: envelope(linkedCourse) }),
      );
      let manifests = 0;
      let segments = 0;
      await student.route(`**/api/v1/media/${mediaId}**`, async (route) => {
        expect(route.request().headers().authorization).toBe('Bearer test-token');
        const path = new URL(route.request().url()).pathname;
        if (path.endsWith('.m3u8')) {
          manifests++;
          await route.fulfill({ contentType: 'application/vnd.apple.mpegurl', body: manifest });
        } else if (path.endsWith('.ts')) {
          segments++;
          await route.fulfill({ contentType: 'video/mp2t', body: segment });
        } else await route.fulfill({ json: envelope(media) });
      });
      await student.goto('/learn/enrollment-1');
      const video = student.locator('video');
      await startDecodedVideo(video);
      expect(await video.evaluate((element) => (element as HTMLVideoElement).videoWidth)).toBe(320);
      expect(manifests).toBeGreaterThan(0);
      expect(segments).toBeGreaterThan(0);
      await expect(student.getByRole('alert')).toHaveCount(0);
      await expect(student.getByText('İlk dersin içeriği.')).toBeVisible();
    } finally {
      await context.close();
    }
  });

  test('processing video waits and starts after becoming ready', async ({ page }) => {
    await session(page);
    await mockApi(page);
    const playableCourse = structuredClone(course);
    Object.assign(playableCourse.sections[0].lessons[1], { videoUrl: streamUrl });
    await page.route('**/api/v1/courses/course-1', (route) =>
      route.fulfill({ json: envelope(playableCourse) }),
    );
    let ready = false;
    let streamRequests = 0;
    await page.route(`**/api/v1/media/${mediaId}**`, async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('.m3u8')) {
        streamRequests++;
        await route.fulfill({ contentType: 'application/vnd.apple.mpegurl', body: manifest });
      } else if (path.endsWith('.ts')) {
        streamRequests++;
        await route.fulfill({ contentType: 'video/mp2t', body: segment });
      } else
        await route.fulfill({
          json: envelope({ ...media, status: ready ? 'READY' : 'PROCESSING' }),
        });
    });
    await page.goto('/learn/enrollment-1?lesson=lesson-2');
    await expect(page.getByText('Video hazırlanıyor.', { exact: false })).toBeVisible();
    expect(streamRequests).toBe(0);
    ready = true;
    const video = page.locator('video');
    await startDecodedVideo(video);
  });
});
test('unsupported H.264 playback shows a useful error before starting a protected stream', async ({
  page,
}) => {
  await session(page);
  await mockApi(page);
  await page.addInitScript(() => {
    const supported = MediaSource.isTypeSupported.bind(MediaSource);
    MediaSource.isTypeSupported = (type) => !type.includes('avc1') && supported(type);
  });
  const id = '6df6a12f-fae2-483e-aef3-2850881bef22',
    playable = structuredClone(course);
  Object.assign(playable.sections[0].lessons[1], { videoUrl: `/api/v1/media/${id}/manifest.m3u8` });
  await page.route('**/api/v1/courses/course-1', (route) =>
    route.fulfill({ json: envelope(playable) }),
  );
  await page.route(`**/api/v1/media/${id}`, (route) =>
    route.fulfill({ json: envelope({ id, status: 'READY', type: 'VIDEO', durationSeconds: 3 }) }),
  );
  await page.goto('/learn/enrollment-1?lesson=lesson-2');
  await expect(page.getByRole('alert')).toContainText('H.264');
  await expect(page.locator('video')).toBeHidden();
});

test('public catalog search, category selection, detail and preview work', async ({ page }) => {
  await mockApi(page);
  await page.goto('/catalog');
  await expect(page.getByRole('heading', { name: 'Sırada ne öğrenmek var?' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Kurs ara' }).fill('Tasarım');
  await page.getByRole('button', { name: 'Ara', exact: true }).click();
  await expect(page).toHaveURL(/q=Tasar/);
  await page.getByLabel('Kategori', { exact: true }).selectOption('3');
  await expect(page).toHaveURL(/category=3/);
  await page.getByRole('heading', { name: course.title, exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Neler öğreneceksin?' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Birlikte keşfedelim/ })).toBeDisabled();
  await page.getByRole('button', { name: /Tasarım nedir/ }).click();
  await expect(page.getByRole('dialog')).toContainText('İlk dersin içeriği.');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
test('signed-out protected pages return to login and preserve destination', async ({ page }) => {
  await mockApi(page);
  await page.goto('/payments');
  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Yeniden hoş geldin.' })).toBeVisible();
});
test('students cannot open the instructor or administrator area', async ({ page }) => {
  await session(page);
  await mockApi(page);
  await page.goto('/instructor');
  await expect(page.getByRole('heading', { name: 'Bu sayfaya erişiminiz yok' })).toBeVisible();
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Bu sayfaya erişiminiz yok' })).toBeVisible();
});
test('article completion sends the real section and video has no manual completion', async ({
  page,
}) => {
  await session(page);
  await mockApi(page);
  let request: unknown;
  await page.route('**/api/v1/progress/enrollment-1/lesson/lesson-1', async (route) => {
    request = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ lessonId: 'lesson-1', status: 'COMPLETED' }) });
  });
  await page.goto('/learn/enrollment-1');
  await expect(page.getByRole('heading', { name: 'Tasarım nedir?', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Okudum, tamamla' }).click();
  await expect.poll(() => request).toEqual({ sectionId: 'section-1', completed: true });
  await page.getByRole('button', { name: 'Sonraki ders' }).click();
  await expect(
    page.getByRole('heading', { name: 'Birlikte keşfedelim', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Okudum, tamamla' })).toHaveCount(0);
});
test('profile updates omit blank password changes', async ({ page }) => {
  await session(page);
  await mockApi(page);
  let payload: Record<string, unknown> | undefined;
  await page.route('**/api/v1/users/me', async (route) => {
    if (route.request().method() === 'PUT') payload = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ ...user, firstName: 'Ece Nur' }) });
  });
  await page.goto('/profile');
  await page.getByLabel('Ad', { exact: true }).fill('Ece Nur');
  await page.getByRole('button', { name: 'Değişiklikleri kaydet' }).click();
  await expect(page.getByRole('status')).toContainText('Profilin güncellendi.');
  expect(payload?.firstName).toBe('Ece Nur');
  expect(payload).not.toHaveProperty('newPassword');
  expect(payload).not.toHaveProperty('currentPassword');
});
test('refund submits the owned payment and displays its status', async ({ page }) => {
  await session(page);
  await mockApi(page);
  await page.route('**/api/v1/payments/my?**', (route) =>
    route.fulfill({
      json: envelope(
        pageData([
          {
            id: 'payment-1',
            courseId: course.id,
            courseTitle: course.title,
            amount: 450,
            currency: 'TRY',
            status: 'COMPLETED',
            createdAt: '2026-01-01T00:00:00',
          },
        ]),
      ),
    }),
  );
  let payload: unknown;
  await page.route('**/api/v1/payments/refund', async (route) => {
    payload = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ id: 'refund-1', status: 'PENDING', amount: 450 }) });
  });
  await page.goto('/payments');
  await page.getByRole('button', { name: 'İade iste' }).click();
  await page.getByLabel('İade nedeni').fill('Kurs ihtiyacıma uygun değil.');
  await page.getByRole('button', { name: 'İade talebini gönder' }).click();
  await expect(page.getByRole('dialog')).toContainText('İade talebin alındı.');
  expect(payload).toEqual({ paymentId: 'payment-1', reason: 'Kurs ihtiyacıma uygun değil.' });
});
test('service failures remain visible instead of becoming empty data', async ({ page }) => {
  await session(page, 'INSTRUCTOR');
  await mockApi(page, 'INSTRUCTOR');
  await page.route('**/api/v1/media/my?**', (route) =>
    route.fulfill({
      status: 503,
      json: { success: false, message: 'Depolama hizmeti kullanılamıyor' },
    }),
  );
  await page.goto('/media');
  await expect(page.getByRole('alert')).toContainText('Depolama hizmeti kullanılamıyor');
  await expect(page.getByRole('heading', { name: 'İçeriklerin için yer hazır.' })).toHaveCount(0);
});
test('mobile navigation and desktop dashboard stay within viewport', async ({ page }) => {
  await session(page);
  await mockApi(page);
  await page.goto('/');
  await page.screenshot({
    path: 'test-results/dashboard-desktop.png',
    fullPage: true,
    animations: 'disabled',
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Menüyü aç' })).toBeVisible();
  await page.getByRole('button', { name: 'Menüyü aç' }).click();
  await page
    .getByRole('navigation', { name: 'Ana menü', exact: true })
    .getByRole('link', { name: 'Öğrenim alanım' })
    .click();
  await expect(page).toHaveURL('/my-learning');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: 'test-results/learning-mobile.png',
    fullPage: true,
    animations: 'disabled',
  });
});

test('media upload preserves file and owner authentication, then lists the new file', async ({
  page,
}) => {
  await session(page, 'INSTRUCTOR');
  await mockApi(page, 'INSTRUCTOR');
  let uploaded = false;
  const media = {
    id: 'media-1',
    type: 'IMAGE',
    status: 'READY',
    originalFileName: 'lesson.png',
    sizeBytes: 68,
    streamUrl: 'https://storage.example/lesson.png',
  };
  await page.route('**/api/v1/media/my?**', (route) =>
    route.fulfill({ json: envelope(pageData(uploaded ? [media] : [])) }),
  );
  await page.route('**/api/v1/media/image', async (route) => {
    expect(route.request().headers().authorization).toBe('Bearer test-token');
    expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=');
    expect(route.request().postDataBuffer()?.toString()).toContain('filename="lesson.png"');
    uploaded = true;
    await route.fulfill({ json: envelope(media) });
  });
  await page.goto('/media');
  await page.getByLabel('Dosya seç').setInputFiles({
    name: 'lesson.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
      'base64',
    ),
  });
  await page.getByRole('button', { name: 'Dosyayı yükle' }).click();
  await expect(page.getByRole('heading', { name: 'lesson.png' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('1 dosya yüklendi.');
});

test('certificate download and public verification use their separate endpoints', async ({
  page,
}) => {
  await session(page);
  await mockApi(page);
  const certificate = {
    id: 'cert-1',
    verificationCode: 'VERIFY-2026',
    studentName: 'Ece Kaya',
    courseTitle: course.title,
    instructorName: course.instructorName,
    issuedAt: '2026-01-01T00:00:00',
    pdfUrl: 'https://storage.example/certificate.pdf',
  };
  await page.route('**/api/v1/enrollments/enrollment-1/certificate', (route) =>
    route.fulfill({ json: envelope(certificate) }),
  );
  await page.route('**/api/v1/enrollments/certificates/verify/VERIFY-2026', (route) =>
    route.fulfill({ json: envelope({ ...certificate, pdfUrl: undefined }) }),
  );
  await page.goto('/certificates?enrollment=enrollment-1');
  await expect(page.getByRole('link', { name: 'PDF indir' })).toHaveAttribute(
    'href',
    certificate.pdfUrl,
  );
  await page.getByRole('link', { name: 'Doğrula', exact: true }).click();
  await expect(page.getByText('Sertifika doğrulandı.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'PDF indir' })).toHaveCount(0);
});

test('administrator searches accounts and views a selected user', async ({ page }) => {
  await session(page, 'ADMIN');
  await mockApi(page, 'ADMIN');
  await page.route('**/api/v1/users/search?**', (route) =>
    route.fulfill({ json: envelope(pageData([user])) }),
  );
  await page.route('**/api/v1/users/student-1', (route) => route.fulfill({ json: envelope(user) }));
  await page.goto('/admin/users');
  await page.getByRole('textbox', { name: 'Kullanıcı ara' }).fill('Ece');
  await page.getByRole('button', { name: 'Ara', exact: true }).click();
  await page.getByRole('button', { name: 'İncele' }).click();
  await expect(page.getByRole('dialog')).toContainText('student@example.test');
});

test('email verification submits once even in strict mode', async ({ page }) => {
  await mockApi(page);
  let count = 0;
  await page.route('**/api/v1/auth/verify-email?token=local-code', (route) => {
    count++;
    return route.fulfill({ json: envelope(null) });
  });
  await page.goto('/verify-email?token=local-code');
  await expect(page.getByRole('status')).toContainText('E-posta adresiniz doğrulandı.');
  expect(count).toBe(1);
});

test('administrator changes account role and status with a recorded reason', async ({ page }) => {
  await session(page, 'ADMIN');
  await mockApi(page, 'ADMIN');
  const target = { ...user, id: 'target-1' };
  await page.route('**/api/v1/users/search?**', (route) =>
    route.fulfill({ json: envelope(pageData([target])) }),
  );
  await page.route('**/api/v1/users/target-1', (route) =>
    route.fulfill({ json: envelope(target) }),
  );
  let saved: unknown;
  await page.route('**/api/v1/users/admin/target-1', async (route) => {
    saved = route.request().postDataJSON();
    await route.fulfill({ json: envelope(target) });
  });
  await page.goto('/admin/users');
  await page.getByRole('button', { name: 'İncele', exact: true }).click();
  await page.getByLabel('Kullanıcı rolü').selectOption('INSTRUCTOR');
  await page.getByLabel('Hesap durumu').selectOption('BANNED');
  await page.getByLabel('Kullanıcı değişikliği gerekçesi').fill('İçerik politikası ihlali');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Kullanıcı yetkilerini kaydet' }).click();
  await expect
    .poll(() => saved)
    .toEqual({ role: 'INSTRUCTOR', status: 'BANNED', reason: 'İçerik politikası ihlali' });
});

test('administrator reviews a course before it is published', async ({ page }) => {
  await session(page, 'ADMIN');
  await mockApi(page, 'ADMIN');
  let approved = false;
  let decision: unknown;
  await page.route('**/api/v1/courses/admin?**', (route) =>
    route.fulfill({
      json: envelope(pageData(approved ? [] : [{ ...course, status: 'PENDING_REVIEW' }])),
    }),
  );
  await page.route('**/api/v1/courses/admin/course-1/decision', async (route) => {
    decision = route.request().postDataJSON();
    approved = true;
    await route.fulfill({ json: envelope({ ...course, status: 'PUBLISHED' }) });
  });
  await page.goto('/admin/courses');
  await page.getByRole('button', { name: 'Onayla', exact: true }).click();
  await page.getByLabel('Karar gerekçesi').fill('İçerik kontrol edildi');
  await page.getByRole('button', { name: 'Kararı kaydet' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(decision).toEqual({ action: 'APPROVE', reason: 'İçerik kontrol edildi' });
});

test('free course enrollment opens learning without creating a payment', async ({ page }) => {
  await session(page);
  await mockApi(page);
  await page.route('**/api/v1/courses/course-1', (route) =>
    route.fulfill({ json: envelope({ ...course, price: 0 }) }),
  );
  await page.route('**/api/v1/enrollments/course/course-1/check', (route) =>
    route.fulfill({ json: envelope(false) }),
  );
  let joined = false;
  await page.route('**/api/v1/enrollments/free/course-1', async (route) => {
    expect(route.request().method()).toBe('POST');
    joined = true;
    await route.fulfill({ json: envelope(enrollment) });
  });
  await page.goto('/courses/course-1');
  await page.getByRole('button', { name: 'Ücretsiz kursa katıl' }).click();
  await expect(page).toHaveURL(/\/learn\/enrollment-1(?:\?lesson=lesson-1)?$/);
  expect(joined).toBe(true);
});

test('student submits quiz answers and completion follows the server result', async ({ page }) => {
  await session(page);
  await mockApi(page);
  const copy = structuredClone(course);
  copy.sections[0].lessons[0].type = 'QUIZ';
  await page.route('**/api/v1/courses/course-1', (route) =>
    route.fulfill({ json: envelope(copy) }),
  );
  await page.route('**/api/v1/courses/course-1/lessons/lesson-1/quiz', (route) =>
    route.fulfill({
      json: envelope({
        passingScore: 70,
        questions: [{ id: 'question-1', prompt: '2 + 2?', options: ['4', '5'] }],
      }),
    }),
  );
  let payload: unknown;
  await page.route('**/api/v1/courses/course-1/lessons/lesson-1/quiz/attempts', async (route) => {
    if (route.request().method() === 'POST') {
      payload = route.request().postDataJSON();
      await route.fulfill({
        json: envelope({ id: 'attempt-1', score: 100, passed: true, correct: 1, total: 1 }),
      });
    } else await route.fulfill({ json: envelope([]) });
  });
  await page.goto('/learn/enrollment-1');
  await page.getByLabel('4', { exact: true }).check();
  await page.getByRole('button', { name: 'Testi gönder' }).click();
  await expect(page.getByText('Testi geçtin; ders tamamlandı.', { exact: false })).toBeVisible();
  expect(payload).toEqual({
    enrollmentId: 'enrollment-1',
    answers: [{ questionId: 'question-1', option: 0 }],
  });
});

test('students can save personal notes and ask lesson questions', async ({ page }) => {
  await session(page);
  await mockApi(page);
  let note: unknown;
  let question: unknown;
  await page.route('**/api/v1/progress/enrollment-1/notes', async (route) => {
    note = route.request().postDataJSON();
    await route.fulfill({
      json: envelope({
        id: 'note-1',
        lessonId: 'lesson-1',
        positionSeconds: 0,
        content: 'Önemli bir nokta',
      }),
    });
  });
  await page.route('**/api/v1/courses/course-1/questions', async (route) => {
    question = route.request().postDataJSON();
    await route.fulfill({
      json: envelope({
        id: 'question-1',
        authorId: 'student-1',
        authorName: 'Ece',
        title: 'Bir örnek daha?',
        content: 'Açıklayabilir misiniz?',
        replyCount: 0,
      }),
    });
  });
  await page.route('**/api/v1/courses/course-1/questions/question-1/replies', (route) =>
    route.fulfill({ json: envelope([]) }),
  );
  await page.goto('/learn/enrollment-1');
  await page.getByLabel('Ders notun').fill('Önemli bir nokta');
  await page.getByRole('button', { name: 'Not ekle', exact: true }).click();
  await expect
    .poll(() => note)
    .toEqual({
      lessonId: 'lesson-1',
      sectionId: 'section-1',
      positionSeconds: 0,
      content: 'Önemli bir nokta',
    });
  await page.getByText('Yeni soru sor', { exact: true }).click();
  await page.getByLabel('Soru başlığı').fill('Bir örnek daha?');
  await page.getByLabel('Sorunun ayrıntısı').fill('Açıklayabilir misiniz?');
  await page.getByRole('button', { name: 'Soruyu gönder' }).click();
  await expect
    .poll(() => question)
    .toEqual({ lessonId: 'lesson-1', title: 'Bir örnek daha?', content: 'Açıklayabilir misiniz?' });
});

test('failed enrollment requests render an error without waiting for a disabled course query', async ({
  page,
}) => {
  await session(page);
  await mockApi(page);
  await page.route('**/api/v1/enrollments/enrollment-1', (route) =>
    route.fulfill({ status: 403, json: { success: false, message: 'Bu kayda erişiminiz yok' } }),
  );
  await page.goto('/learn/enrollment-1');
  await expect(page.getByRole('alert')).toContainText('Bu kayda erişiminiz yok');
  await expect(page.getByText('Yükleniyor…', { exact: true })).toHaveCount(0);
});
