# İleri — LMS arayüzü

Mevcut yedi Spring Boot servisi için Türkçe, mobil uyumlu React 19 + TypeScript uygulaması. Vite, React Router, TanStack Query, Stripe Elements ve HLS.js kullanır. Ürün ekranları gerçek API verilerini kullanır; örnek veriler yalnızca tarayıcı testlerinde bulunur.

Bu klasör bağımsız frontend projesidir. Spring servisleri, Maven projesi ve Docker Compose yapılandırması ayrı [backend/](../backend/) klasöründedir.

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

| Alan          | Ekranlar ve işlemler                                                                                                                                |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hesap         | Öğrenci/eğitmen kaydı, giriş, e-posta doğrulama, çıkış, profil/avatar/telefon/biyografi, parola değişikliği, hesabı devre dışı bırakma.             |
| Keşif         | Yayındaki/popüler kurslar, arama, dinamik kategori filtresi, sayfalama, eğitmenin kursları, kurs ayrıntıları, tanıtım videosu ve ön izlemeler.      |
| Öğrenim       | Ücretsiz kayıt, ders/bölüm menüsü, korumalı HLS, zaman ve benzersiz aralıklarla video tamamlama, kaldığın yerden devam, makale tamamlama, ilerleme. |
| Etkileşim     | Sunucuda puanlanan testler; ödev teslimi, düzeltme ve kabul; kişisel zaman damgalı notlar; ders/kurs soru-cevap ve kabul edilen yanıtlar.           |
| Değerlendirme | Kurs yorumlarını görüntüleme; kayıtlı öğrencinin puan/yorum eklemesi ve kendi yorumunu silmesi.                                                     |
| Ödeme         | PaymentIntent, Stripe Elements, asenkron ödeme durumu, ödeme geçmişi/ayrıntısı, makbuz ve iade talebi.                                              |
| Sertifika     | Tamamlanan kursun sertifikasını görüntüleme, süreli PDF bağlantısı ve herkese açık doğrulama kodu sorgusu.                                          |
| Eğitmen       | Kurs/müfredat yönetimi, yönetici incelemesine gönderme, arşivleme, test hazırlama, ödev değerlendirme, öğrenciler ve ilerleme listesi.              |
| Medya         | Kullanıcıya ait dosyaların listesi; kurs/dersle ilişkilendirilen görsel/video yükleme; işleme durumu, ayrıntı, bağlantı kopyalama ve silme.         |
| Bildirim      | Kullanıcının e-posta bildirim kayıtları ve gönderim durumları. Backend'de okundu/okunmadı işlemi yoktur.                                            |
| Yönetim       | Yönetici özeti, kullanıcı rolü/durumu ve işlem kaydı, kurs onayı/ret/arşiv, kategori yönetimi, gerekçeli kayıt verme/iptal ve öğrenci ilerlemesi.   |

Backend entegrasyonunu tamamlamak için eklenen küçük API uzantıları:

- `GET /api/v1/courses/mine`: kimliği doğrulanmış eğitmenin **tüm durumdaki** kursları; başka bir kullanıcının ID'sini kabul etmez. Mevcut herkese açık eğitmen endpoint'i yalnızca yayınlanmış kursları döndürür.
- `GET /api/v1/courses/categories`: veritabanındaki kategorilerin ID/ad/açıklama listesi.
- `GET /api/v1/media/my`: kimliği doğrulanmış eğitmenin/yöneticinin kendi medya dosyaları; sayfalıdır.
- Bildirim servisinde isteğe bağlı `APP_FRONTEND_BASE_URL` desteği.

## Backend'in mevcut sınırları

- Video bildirimleri beş saniyede bir gönderilir; backend süre ve izlenen aralıkları kontrol eder. İleri sarmak veya aynı bölümü tekrar izlemek tamamlanmamış aralıklara kredi vermez. Test ve ödev tamamlaması backend sonucundan alınır. Arayüz kendisi kurs yüzdesi veya sertifika üretmez.
- Ücretsiz kayıt `/enrollments/free/{courseId}` kullanır; kart formuna gönderilmez. Yönetici ayrıca gerekçeli kayıt verebilir. Ücretli ödeme ve iade akışı mevcut yapısını korur.
- Kupon alanı mevcut istek sözleşmesine gönderilir; tutar olarak yalnızca sunucunun döndürdüğü değer kullanılır. Backend şu anda kupon hesaplaması uygulamaz.
- Bölüm/ders genel düzenleme seçenekleri mevcut endpoint'lerle sınırlıdır. Video bağlama/değiştirme, test içeriğini düzenleme ve kurs arşivleme desteklenir; genel bölüm/ders güncellemesi ve kursu kalıcı silme bu sürümde bulunmaz.
- Görseller için MinIO'nun döndürdüğü bağlantılar sürelidir. Kalıcı kurs kapaklarında kalıcı, herkese açık bir görsel URL'si kullanın.
- PDF, e-posta, video dönüştürme ve kurs kaydı asenkron olabilir; servis ve ağ yapılandırmasına bağlı hatalar kullanıcıya gösterilir.

## Kontroller

```bash
npm run build          # strict TypeScript + üretim derlemesi
npm test               # API sınırı, auth, hata ve multipart testleri
npm run test:e2e       # kontrollü API yanıtlarıyla Chromium iş akışları
E2E_VIDEO=1 npm run test:e2e  # FFmpeg ile üretilen gerçek HLS'i Chromium'da oynatır
E2E_REAL_BACKEND=1 npm run test:e2e -- tests/backend.spec.ts
npm run format:check
```

Playwright varsayılan olarak bu ortamda bulunan `/usr/bin/chromium` kullanır. Farklı bir kurulumda `PLAYWRIGHT_CHROMIUM_PATH` verin. Gerçek backend testi PostgreSQL/Kafka/gateway/user/course servislerini gerektirir ve `frontend-e2e-…@example.test` adresli yerel bir eğitmen ile incelemeye gönderilmiş test kursu oluşturur; üretim veritabanında çalıştırmayın. Diğer tarayıcı testleri backend'i taklit eder ve gerçek Stripe/S3 işlemi doğrulamaz.

`E2E_VIDEO=1` için PATH'te FFmpeg gerekir. İki ek test, yüklenen videonun seçilen metin dersine bağlanmasını ve öğrenci hesabında HLS manifest/segment isteklerinin yetkili gönderilmesini sınar; görüntü genişliğini ve ilerleyen oynatma süresini ölçer. İşlenen videonun hazır olana kadar beklemesi de kontrol edilir. Bu testlerde API yanıtları taklit edilir, video verisi gerçekten çözülür.

Öğrenci derste yalnızca başlık/metin görüyorsa eğitmen hesabında **Kurs müfredatı → Video ekle → Medya kitaplığından video seç → Videoyu derse bağla** yolunu kullanın. Yeni yüklemelerde **İlgili ders** seçimi bağlantıyı otomatik kurar. Metin içerik korunur; öğrenci sayfasında oynatıcı da görünür.

Backend regresyon testleri `backend/` klasöründen çalıştırılır:

```bash
cd ../backend
sh user-service/mvnw -B \
  -Dtest=CourseRegressionTest,MediaDeletionTest,FrontendEmailLinkTest,AccessTokenValidationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Yayınlama

`npm run build` çıktısı `dist/` dizinidir. `npm run preview` 4173 portunda yerel derleme kontrolü içindir. Üretimde dosyaları statik bir sunucuda yayınlayın; `/api/` gateway'e proxy edilmeli, diğer uygulama yolları `index.html` dosyasına dönmelidir. Örnek Nginx yapılandırması [nginx.conf](nginx.conf) dosyasındadır. `APP_FRONTEND_BASE_URL` ve Stripe dönüş adresleri yayınlanan HTTPS köküyle eşleşmelidir.
