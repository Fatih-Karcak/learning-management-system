import { test, expect } from '@playwright/test';

test('real backend: register instructor, add lesson and submit for administrator review', async ({
  page,
}) => {
  test.skip(
    process.env.E2E_REAL_BACKEND !== '1',
    'Requires the running PostgreSQL/Kafka/gateway/user/course services.',
  );
  const suffix = Date.now().toString(36);
  const email = `frontend-e2e-${suffix}@example.test`;
  const title = `Frontend doğrulama kursu ${suffix}`;
  await page.goto('/register');
  await page.getByLabel('First name', { exact: true }).fill('Tarayıcı');
  await page.getByLabel('Last name', { exact: true }).fill('Kontrolü');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(`Local-${suffix}-Test!`);
  await page.getByLabel('How would you like to join?').selectOption('INSTRUCTOR');
  await page.getByRole('button', { name: 'Create my account' }).click();
  await expect(page.getByRole('heading', { name: /Hello, Tarayıcı/ })).toBeVisible();
  await page
    .getByRole('navigation', { name: 'Instructor navigation' })
    .getByRole('link', { name: 'My courses', exact: true })
    .click();
  await page.getByRole('link', { name: 'Create a new course' }).click();
  await page.getByLabel('Course title').fill(title);
  await page
    .getByLabel('Short description', { exact: true })
    .fill('Gerçek backend ile uçtan uca tarayıcı doğrulaması.');
  await page
    .getByLabel('Course description', { exact: true })
    .fill(
      '## Öğrenme yolculuğu\nBu kurs arayüz ve backend bağlantısını doğrulamak için oluşturuldu.',
    );
  await page.getByLabel('Category', { exact: true }).selectOption({ index: 1 });
  await page.getByLabel('Price (TRY)', { exact: true }).fill('250');
  await page.getByRole('button', { name: 'Create draft' }).click();
  await expect(page).toHaveURL(/\/instructor\/[a-f0-9-]+$/);
  await page.getByLabel('Requirements — one per line').fill('Ön koşul yok');
  await page.getByLabel('Learning outcomes — one per line').fill('İlk yazılı dersi tamamla');
  await page.getByRole('button', { name: 'Add section', exact: true }).click();
  await page.getByLabel('Section 1 title').fill('Beginner');
  await page.getByRole('button', { name: 'Add lesson', exact: true }).click();
  await page
    .getByRole('dialog')
    .getByLabel('Lesson title', { exact: true })
    .fill('İlk yazılı ders');
  await page
    .getByRole('dialog')
    .getByLabel('Lesson text', { exact: true })
    .fill('Gerçek backend üzerinden kaydedilen ders içeriği.');
  await page.getByRole('button', { name: 'Finish editing' }).click();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Withdraw submission', exact: true }),
  ).toBeVisible();
  await page
    .getByRole('navigation', { name: 'Instructor navigation' })
    .getByRole('link', { name: 'My courses', exact: true })
    .click();
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.goto('/catalog');
  await page.getByRole('textbox', { name: 'Search courses' }).fill(suffix);
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(page.getByRole('heading', { name: title, exact: true })).toHaveCount(0);
});
