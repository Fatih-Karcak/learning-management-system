import { test, expect, type Page } from '@playwright/test';

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
    else if (path.startsWith('/api/v1/progress/')) data = [];
    await route.fulfill({ json: envelope(data) });
  });
}
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
  await expect(page.getByRole('status')).toContainText('Görsel yüklendi.');
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
