import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { startDecodedVideo } from './videoAssertions';
const enabled = process.env.E2E_REAL_BACKEND === '1' && !!process.env.LMS_E2E_FIXTURE_PATH;
type Session = { accessToken: string; refreshToken: string; user: { id: string } };
const fixtures = () => JSON.parse(readFileSync(process.env.LMS_E2E_FIXTURE_PATH!, 'utf8'));
async function login(page: Page, session: Session) {
  await page.addInitScript(
    (s) => sessionStorage.setItem('ileri.session', JSON.stringify(s)),
    session,
  );
}
test.describe('real platform experience', () => {
  test.skip(!enabled, 'Requires platform-workflows.test.mjs fixtures and the running services.');
  test('student decodes an uploaded video with subtitles and saves a private bookmark', async ({
    page,
  }) => {
    const f = fixtures();
    const bookmarkTitle = `Tarayıcı pilot yer imi ${crypto.randomUUID()}`;
    test.skip(!f.video, 'Video fixture required');
    await login(page, f.student);
    await page.goto(`/learn/${f.enrollmentId}?lesson=${f.video}`);
    const video = page.locator('video');
    await startDecodedVideo(video);
    await expect
      .poll(() => video.evaluate((e) => (e as HTMLVideoElement).textTracks[0]?.cues?.length || 0))
      .toBeGreaterThan(0);
    expect(await video.evaluate((e) => (e as HTMLVideoElement).videoWidth)).toBe(320);
    await page.getByLabel(/Bu konuma yer imi ekle/).fill(bookmarkTitle);
    await page.getByRole('button', { name: 'Yer imi ekle', exact: true }).click();
    await expect(page.getByRole('button', { name: new RegExp(bookmarkTitle) })).toBeVisible();
    await expect
      .poll(async () => {
        const response = await page.request.get(
          `${process.env.LMS_E2E_API_URL || 'http://127.0.0.1:8080/api/v1'}/progress/${f.enrollmentId}`,
          { headers: { Authorization: `Bearer ${f.student.accessToken}` } },
        );
        return (await response.json()).data.find(
          (p: { lessonId: string }) => p.lessonId === f.video,
        )?.status;
      })
      .toBe('COMPLETED');
    await page.goto('/study-plan');
    await expect(page.getByRole('heading', { name: 'Çalışma planım' })).toBeVisible();
    await expect(page.getByRole('link', { name: new RegExp(bookmarkTitle) })).toBeVisible();
  });
  test('instructor sees learning reports and can publish an announcement', async ({ page }) => {
    const f = fixtures();
    const announcementTitle = `Tarayıcı pilot duyurusu ${crypto.randomUUID()}`;
    await login(page, f.teacher);
    await page.goto(`/instructor/${f.courseId}`);
    await page.getByRole('button', { name: 'Öğrenme raporu', exact: true }).click();
    await expect(
      page.getByRole('table', { name: 'Ders bazında katılım ve sonuçlar' }),
    ).toBeVisible();
    await expect(page.getByRole('row', { name: /Practice test/ })).toContainText('%100');
    await page.getByRole('button', { name: 'Duyurular', exact: true }).click();
    await page.getByLabel('Duyuru başlığı').fill(announcementTitle);
    await page.getByLabel('Duyuru metni').fill('Bu duyuru gerçek backend üzerinden kaydedildi.');
    await page.getByRole('button', { name: 'Duyuruyu yayımla' }).click();
    await expect(page.getByRole('heading', { name: announcementTitle })).toBeVisible();
  });
  test('student saves preferences and creates a study task', async ({ page }) => {
    const f = fixtures();
    const taskTitle = `Tarayıcı üzerinden çalışma planı ${crypto.randomUUID()}`;
    await login(page, f.student);
    await page.goto('/security');
    await page.getByLabel('Haftalık video çalışma hedefi (dakika)').fill('90');
    await page.getByLabel('Öğrenmeyle ilgili e-postalar').uncheck();
    await page.getByRole('button', { name: 'Tercihleri kaydet' }).click();
    await expect(page.getByRole('status')).toContainText('İşlem kaydedildi.');
    await page.reload();
    await expect(page.getByLabel('Haftalık video çalışma hedefi (dakika)')).toHaveValue('90');
    await expect(page.getByLabel('Öğrenmeyle ilgili e-postalar')).not.toBeChecked();
    await page.goto('/study-plan');
    await page.getByLabel('Çalışma hedefi', { exact: true }).fill(taskTitle);
    await page.getByRole('button', { name: 'Plana ekle' }).click();
    await expect(page.getByLabel(taskTitle, { exact: true })).toBeVisible();
    await page.getByLabel(taskTitle, { exact: true }).check();
    await expect(page.getByLabel(taskTitle, { exact: true })).toBeChecked();
  });
  test('administrator reviews and publishes a complete workspace through the interface', async ({
    page,
  }) => {
    const f = fixtures();
    const id = crypto.randomUUID();
    const response = await page.request.post('http://127.0.0.1:8080/api/v1/courses', {
      headers: { Authorization: `Bearer ${f.teacher.accessToken}` },
      data: {
        title: `Browser moderation ${id}`,
        description: 'A reviewed course',
        shortDescription: 'Moderation fixture',
        price: 0,
        categoryId: 1,
      },
    });
    expect(response.ok()).toBeTruthy();
    const c = (await response.json()).data;
    const get = await page.request.get(`http://127.0.0.1:8080/api/v1/courses/${c.id}/workspace`, {
      headers: { Authorization: `Bearer ${f.teacher.accessToken}` },
    });
    const w = (await get.json()).data;
    w.document.course.requirements = ['No prerequisites'];
    w.document.course.outcomes = ['Finish the lesson'];
    w.document.course.sections = [
      {
        id: crypto.randomUUID(),
        title: 'Introduction',
        orderIndex: 0,
        lessons: [
          {
            id: crypto.randomUUID(),
            title: 'First article',
            content: 'Article for review',
            type: 'ARTICLE',
            orderIndex: 0,
            previewAllowed: false,
          },
        ],
      },
    ];
    const saved = await page.request.put(`http://127.0.0.1:8080/api/v1/courses/${c.id}/workspace`, {
      headers: { Authorization: `Bearer ${f.teacher.accessToken}` },
      data: { revision: 0, document: w.document },
    });
    expect(saved.ok()).toBeTruthy();
    const revision = (await saved.json()).data.revision;
    const submitted = await page.request.post(
      `http://127.0.0.1:8080/api/v1/courses/${c.id}/workspace/submit?revision=${revision}`,
      { headers: { Authorization: `Bearer ${f.teacher.accessToken}` } },
    );
    expect(submitted.ok()).toBeTruthy();
    await login(page, f.admin);
    await page.goto('/admin/courses');
    const row = page.locator('article').filter({ hasText: c.title });
    await row.getByRole('button', { name: 'Taslağı incele' }).click();
    await page.getByRole('button', { name: 'Taslağı onayla ve yayımla' }).click();
    await expect(page.getByRole('dialog').getByText('Yayında', { exact: true })).toBeVisible();
    const published = await page.request.get(`http://127.0.0.1:8080/api/v1/courses/${c.id}`);
    expect((await published.json()).data.status).toBe('PUBLISHED');
  });
  test('support request form saves a real ticket and its reply', async ({ page }) => {
    const f = fixtures();
    const supportTitle = `Tarayıcı pilot destek talebi ${crypto.randomUUID()}`;
    await login(page, f.student);
    await page.goto('/support');
    await page.getByLabel('Konu', { exact: true }).fill(supportTitle);
    await page.getByLabel('Kategori', { exact: true }).selectOption('TECHNICAL');
    await page
      .getByLabel('Açıklama', { exact: true })
      .fill('Öğrenci destek akışı kontrol ediliyor.');
    await page.getByRole('button', { name: 'Destek talebi aç' }).click();
    await page.getByRole('button', { name: supportTitle, exact: true }).click();
    await page.getByLabel('Yanıt', { exact: true }).fill('Sorunla ilgili ek bilgi.');
    await page.getByRole('button', { name: 'Yanıt gönder' }).click();
    await expect(page.getByText('Sorunla ilgili ek bilgi.', { exact: true })).toBeVisible();
  });
});
