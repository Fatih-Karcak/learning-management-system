# İleri — LMS arayüzü

Mevcut yedi Spring Boot servisi için Türkçe, mobil uyumlu React 19 + TypeScript uygulaması. Vite, React Router, TanStack Query, Stripe Elements ve HLS.js kullanır. Ürün ekranları gerçek API verilerini kullanır; örnek veriler yalnızca tarayıcı testlerinde bulunur.

## Başlatma

Node.js 20.19+ veya 22.12+ gerekir; bu ortamda Node 24 ile doğrulandı.

```bash
cd /workspace/learning-management-system/frontend
npm ci
cp .env.example .env.local
npm run dev
```

Geliştirme sunucusu 5173 portunda çalışır. `/api` istekleri `API_TARGET` ile belirtilen gateway'e yönlendirilir; varsayılan hedef 8080 portudur. Tarayıcıdan servis portlarına doğrudan erişilmez, CORS ayarını gevşetmek gerekmez.

Bu bulut ortamındaki backend yardımcıları:

```bash
bash /workspace/.lms-setup/start-core.sh
```

Medya/video/sertifika işlemleri için ayrıca gerçek MinIO/S3 ve içerik servisi; ödeme için Stripe yapılandırması gerekir. İçerik servisinin başlangıcı önceki ortam kurulumunda MinIO indirme erişimi nedeniyle engelliydi. Arayüz bu servislerin hatalarını gösterir; başarılı işlem görüntüsü üretmez.

## Yapılandırma

| Ayar                            | Kullanım                                                                                                                                                                                                                |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `API_TARGET`                    | Vite geliştirme/önizleme proxy hedefi; varsayılan `http://127.0.0.1:8080`.                                                                                                                                              |
| `VITE_API_BASE_URL`             | Boş bırakılırsa aynı kökteki `/api` kullanılır. Ayrı bir API adresi gerekiyorsa gateway'in CORS politikası da o adresi desteklemelidir. Derleme sırasında uygulanır.                                                    |
| `VITE_STRIPE_PUBLISHABLE_KEY`   | Stripe **yayınlanabilir** anahtarı. Yoksa kart ödeme formu açılmaz.                                                                                                                                                     |
| Backend `APP_FRONTEND_BASE_URL` | Bildirim servisinin doğrulama e-postalarını frontend'in `/verify-email` sayfasına yönlendirir. Yerel geliştirmede tarayıcının erişebildiği frontend adresini kullanın. Boşsa mevcut API doğrulama bağlantıları korunur. |

`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `JWT_SECRET`, `INTERNAL_API_KEY`, veritabanı ve S3 parolaları yalnızca backend'e aittir. Bunları `VITE_` değişkenlerine koymayın. Kart verileri Stripe Elements üzerinden işlenir. Ödeme sonucu ve kurs kaydı backend/webhook yanıtıyla doğrulanır. Backend'deki Stripe hesabı ve frontend yayınlanabilir anahtarı aynı test/canlı hesaba ait olmalıdır.

Erişim token'ı sekmeye ait `sessionStorage` içinde tutulur ve API'ye `Authorization` başlığıyla gönderilir. 401 yanıtında oturum ve kullanıcıya ait sorgu önbelleği temizlenir. Backend refresh endpoint'i sağlamadığı için otomatik token yenileme yoktur. Rol menüleri kullanım kolaylığı sağlar; gerçek yetkilendirme backend'de uygulanır.

## Kapsam

| Alan          | Ekranlar ve işlemler                                                                                                                           |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Hesap         | Öğrenci/eğitmen kaydı, giriş, e-posta doğrulama, çıkış, profil/avatar/telefon/biyografi, parola değişikliği, hesabı devre dışı bırakma.        |
| Keşif         | Yayındaki/popüler kurslar, arama, dinamik kategori filtresi, sayfalama, eğitmenin kursları, kurs ayrıntıları, tanıtım videosu ve ön izlemeler. |
| Öğrenim       | Kayıtlar, ders/bölüm menüsü, Markdown içerik, korumalı HLS video, izleme konumu kaydı, makale tamamlama, kurs ilerlemesi.                      |
| Değerlendirme | Kurs yorumlarını görüntüleme; kayıtlı öğrencinin puan/yorum eklemesi ve kendi yorumunu silmesi.                                                |
| Ödeme         | PaymentIntent, Stripe Elements, asenkron ödeme durumu, ödeme geçmişi/ayrıntısı, makbuz ve iade talebi.                                         |
| Sertifika     | Tamamlanan kursun sertifikasını görüntüleme, süreli PDF bağlantısı ve herkese açık doğrulama kodu sorgusu.                                     |
| Eğitmen       | Taslak/yayınlanmış kurslar; kurs oluşturma/güncelleme/yayınlama; bölüm ve video/makale/test/ödev türündeki dersleri oluşturma/silme.           |
| Medya         | Kullanıcıya ait dosyaların listesi; kurs/dersle ilişkilendirilen görsel/video yükleme; işleme durumu, ayrıntı, bağlantı kopyalama ve silme.    |
| Bildirim      | Kullanıcının e-posta bildirim kayıtları ve gönderim durumları. Backend'de okundu/okunmadı işlemi yoktur.                                       |
| Yönetim       | Yalnızca yöneticiye açık kullanıcı arama/listesi/ayrıntısı. Backend'de rol değiştirme veya kullanıcı yasaklama endpoint'i yoktur.              |

Backend entegrasyonunu tamamlamak için eklenen küçük API uzantıları:

- `GET /api/v1/courses/mine`: kimliği doğrulanmış eğitmenin **tüm durumdaki** kursları; başka bir kullanıcının ID'sini kabul etmez. Mevcut herkese açık eğitmen endpoint'i yalnızca yayınlanmış kursları döndürür.
- `GET /api/v1/courses/categories`: veritabanındaki kategorilerin ID/ad/açıklama listesi.
- `GET /api/v1/media/my`: kimliği doğrulanmış eğitmenin/yöneticinin kendi medya dosyaları; sayfalıdır.
- Bildirim servisinde isteğe bağlı `APP_FRONTEND_BASE_URL` desteği.

## Backend'in mevcut sınırları

- Video/test/ödev derslerinin tamamlanması güvenilir bir iç servis tarafından doğrulanır. Arayüz yalnızca makaleleri manuel tamamlar; video süresinden sahte tamamlama sonucu üretmez. Test sorusu/yanıtı ve ödev teslimi için backend'de öğrenci endpoint'i bulunmadığından bu ders türleri içerik olarak gösterilir.
- Ücretsiz kurs için ayrı bir kayıt endpoint'i yoktur; mevcut ödeme servisi sıfır tutarı da Stripe'a yollar. Ücretsiz kayıt desteği için backend'de ayrı bir akış gerekir. Arayüz kendi başına kayıt oluşturmaz.
- Kupon alanı mevcut istek sözleşmesine gönderilir; tutar olarak yalnızca sunucunun döndürdüğü değer kullanılır. Backend şu anda kupon hesaplaması uygulamaz.
- Kurs/bölüm/ders silme ve düzenleme seçenekleri mevcut endpoint'lerle sınırlıdır: kurs silme/arşivleme, bölüm/ders güncelleme endpoint'i bulunmaz.
- Görseller için MinIO'nun döndürdüğü bağlantılar sürelidir. Kalıcı kurs kapaklarında kalıcı, herkese açık bir görsel URL'si kullanın.
- PDF, e-posta, video dönüştürme ve kurs kaydı asenkron olabilir; servis ve ağ yapılandırmasına bağlı hatalar kullanıcıya gösterilir.

## Kontroller

```bash
npm run build          # strict TypeScript + üretim derlemesi
npm test               # API sınırı, auth, hata ve multipart testleri
npm run test:e2e       # kontrollü API yanıtlarıyla Chromium iş akışları
E2E_REAL_BACKEND=1 npm run test:e2e -- tests/backend.spec.ts
npm run format:check
```

Playwright varsayılan olarak bu ortamda bulunan `/usr/bin/chromium` kullanır. Farklı bir kurulumda `PLAYWRIGHT_CHROMIUM_PATH` verin. Gerçek backend testi PostgreSQL/Kafka/gateway/user/course servislerini gerektirir ve `frontend-e2e-…@example.test` adresli yerel bir eğitmen ile yayınlanmış test kursu oluşturur; üretim veritabanında çalıştırmayın. Diğer tarayıcı testleri backend'i taklit eder ve gerçek Stripe/S3 işlemi doğrulamaz.

Backend regresyon testleri depo kökünden çalıştırılabilir:

```bash
bash /workspace/.lms-setup/mvn.sh -B \
  -Dtest=CourseRegressionTest,MediaDeletionTest,FrontendEmailLinkTest,AccessTokenValidationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Yayınlama

`npm run build` çıktısı `dist/` dizinidir. `npm run preview` 4173 portunda yerel derleme kontrolü içindir. Üretimde dosyaları statik bir sunucuda yayınlayın; `/api/` gateway'e proxy edilmeli, diğer uygulama yolları `index.html` dosyasına dönmelidir. Örnek Nginx yapılandırması [nginx.conf](nginx.conf) dosyasındadır. `APP_FRONTEND_BASE_URL` ve Stripe dönüş adresleri yayınlanan HTTPS köküyle eşleşmelidir.
