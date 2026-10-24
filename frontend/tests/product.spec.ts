import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { startDecodedVideo } from './videoAssertions';

test.describe('real product workflows', () => {
  test.skip(
    process.env.E2E_REAL_BACKEND !== '1' || !process.env.LMS_PRODUCT_FIXTURE_PATH,
    'Requires product-workflows.test.mjs fixtures and real services.',
  );
  test('an enrolled learner decodes adaptive video and can choose its quality', async ({
    page,
  }) => {
    const f = JSON.parse(readFileSync(process.env.LMS_PRODUCT_FIXTURE_PATH!, 'utf8'));
    await page.addInitScript(
      (auth) => sessionStorage.setItem('ileri.session', JSON.stringify(auth)),
      f.student,
    );
    await page.goto(`/learn/${f.enrollment.id}?lesson=${f.ids.video}`);
    await expect(page.getByRole('heading', { name: 'Adaptive video', exact: true })).toBeVisible();
    const quality = page.getByLabel('Video quality', { exact: true });
    await expect(quality).toBeVisible({ timeout: 15000 });
    await expect(quality.locator('option')).toHaveText(['Auto', '360p', '720p']);
    await quality.selectOption({ label: '720p' });
    const video = page.locator('video');
    await startDecodedVideo(video);
    await expect.poll(() => video.evaluate((v) => (v as HTMLVideoElement).videoHeight)).toBe(720);
    await quality.selectOption({ label: 'Auto' });
    await expect(quality).toHaveValue('-1');
  });
  test('learning path completion is calculated from real enrollment progress', async ({ page }) => {
    const f = JSON.parse(readFileSync(process.env.LMS_PRODUCT_FIXTURE_PATH!, 'utf8'));
    await page.addInitScript(
      (auth) => sessionStorage.setItem('ileri.session', JSON.stringify(auth)),
      f.student,
    );
    await page.goto(`/learning-paths?path=${f.path.id || f.path}`);
    await expect(page.getByRole('heading', { name: 'Learning paths', exact: true })).toBeVisible();
    await expect(page.getByText(f.course.title, { exact: true }).first()).toBeVisible();
    await expect(page.getByText('2 of 2 courses completed', { exact: true })).toBeVisible();
  });
  test('CSV invitations require learner acceptance before group reporting', async ({
    page,
    browser,
  }) => {
    const f = JSON.parse(readFileSync(process.env.LMS_PRODUCT_FIXTURE_PATH!, 'utf8'));
    await page.addInitScript(
      (auth) => sessionStorage.setItem('ileri.session', JSON.stringify(auth)),
      f.teacher,
    );
    await page.goto(`/organizations?organization=${f.organization}&group=${f.group}`);
    await expect(
      page.getByRole('heading', { name: 'Group learners and assignments', exact: true }),
    ).toBeVisible();
    await page.getByLabel('Import learners from CSV').setInputFiles({
      name: 'learners.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(`Name,Email\nLearner,"${f.student.user.email}"\n`),
    });
    await page.getByRole('button', { name: 'Invite learners', exact: true }).click();
    await expect(
      page.getByRole('listitem').filter({ hasText: f.student.user.email }),
    ).toContainText('INVITED');
    const context = await browser.newContext();
    try {
      const learner = await context.newPage();
      await learner.addInitScript(
        (auth) => sessionStorage.setItem('ileri.session', JSON.stringify(auth)),
        f.student,
      );
      await learner.goto('/organizations');
      await expect(
        learner.getByText('Accepting shares your progress', { exact: false }),
      ).toBeVisible();
      await learner.getByRole('button', { name: 'Accept invitation', exact: true }).click();
      await expect(learner.getByText('ACTIVE', { exact: true })).toBeVisible();
      await page.getByRole('button', { name: 'Refresh progress report' }).click();
      await expect(
        page.getByRole('table').getByRole('row').filter({ hasText: f.prerequisite.title }),
      ).toContainText('COMPLETED');
      const download = page.waitForEvent('download');
      await page.getByRole('button', { name: 'Export report as CSV' }).click();
      expect((await download).suggestedFilename()).toBe('training-report.csv');
    } finally {
      await context.close();
    }
  });
});
