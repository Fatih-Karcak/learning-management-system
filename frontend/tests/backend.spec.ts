import { test, expect } from '@playwright/test';

test('real backend: register instructor, create draft, add lesson, publish and discover', async ({
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
  await page.getByLabel('Ad', { exact: true }).fill('Tarayıcı');
  await page.getByLabel('Soyad', { exact: true }).fill('Kontrolü');
  await page.getByLabel('E-posta adresi').fill(email);
  await page.getByLabel('Şifre', { exact: true }).fill(`Local-${suffix}-Test!`);
  await page.getByLabel('Nasıl katılmak istersin?').selectOption('INSTRUCTOR');
  await page.getByRole('button', { name: 'Hesabımı oluştur' }).click();
  await expect(page.getByRole('heading', { name: /Merhaba, Tarayıcı/ })).toBeVisible();
  await page
    .getByRole('navigation', { name: 'Eğitmen menüsü' })
    .getByRole('link', { name: 'Kurslarım', exact: true })
    .click();
  await page.getByRole('link', { name: 'Yeni kurs oluştur' }).click();
  await page.getByLabel('Kurs başlığı').fill(title);
  await page
    .getByLabel('Kısa açıklama', { exact: true })
    .fill('Gerçek backend ile uçtan uca tarayıcı doğrulaması.');
  await page
    .getByLabel('Kurs açıklaması', { exact: true })
    .fill(
      '## Öğrenme yolculuğu\nBu kurs arayüz ve backend bağlantısını doğrulamak için oluşturuldu.',
    );
  await page.getByLabel('Kategori', { exact: true }).selectOption({ index: 1 });
  await page.getByLabel('Fiyat (₺)', { exact: true }).fill('250');
  await page.getByRole('button', { name: 'Taslağı oluştur' }).click();
  await expect(page).toHaveURL(/\/instructor\/[a-f0-9-]+$/);
  await expect(page.getByRole('button', { name: 'Yayınla', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Bölüm ekle' }).click();
  await page.getByRole('dialog').getByLabel('Başlık', { exact: true }).fill('Başlangıç');
  await page.getByRole('dialog').getByRole('button', { name: 'Ekle', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Ders ekle' }).click();
  await page.getByRole('dialog').getByLabel('Başlık', { exact: true }).fill('İlk yazılı ders');
  await page
    .getByRole('dialog')
    .getByLabel('Ders içeriği', { exact: true })
    .fill('Gerçek backend üzerinden kaydedilen ders içeriği.');
  await page.getByRole('dialog').getByLabel('Bu ders herkese açık ön izlenebilir').check();
  await page.getByRole('dialog').getByRole('button', { name: 'Ekle', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Yayınla', exact: true })).toBeEnabled();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Yayınla', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Yayınla', exact: true })).toHaveCount(0);
  await page
    .getByRole('navigation', { name: 'Eğitmen menüsü' })
    .getByRole('link', { name: 'Kurslarım', exact: true })
    .click();
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Çıkış yap' }).click();
  await page.goto('/catalog');
  await page.getByRole('textbox', { name: 'Kurs ara' }).fill(suffix);
  await page.getByRole('button', { name: 'Ara', exact: true }).click();
  await page.getByRole('heading', { name: title, exact: true }).click();
  await page.getByRole('button', { name: /İlk yazılı ders/ }).click();
  await expect(page.getByRole('dialog')).toContainText(
    'Gerçek backend üzerinden kaydedilen ders içeriği.',
  );
});
