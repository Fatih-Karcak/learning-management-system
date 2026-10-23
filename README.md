# Learning Management System

Öğrencilerin kurslara katılıp öğrenme ilerlemelerini takip edebildiği, eğitmenlerin içerik hazırladığı ve yöneticilerin yayınları denetlediği bir eğitim platformudur. Arayüz **React 19 + TypeScript**, backend **Java 25 + Spring Boot** ile geliştirilmiştir. Frontend ve backend ayrı projelerdir.

Ücretsiz kurs iş akışı ödeme gerektirmez. Depodaki mevcut Stripe ödeme/iade entegrasyonu korunur; yerel deneme dağıtımında ödeme servisi isteğe bağlıdır.

## Özellikler

| Kullanıcı | Yapabildikleri                                                                                                                                                                                                                          |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Öğrenci   | Kurs arama ve filtreleme, ücretsiz kayıt, istek listesi, korumalı video izleme, kaldığı yerden devam etme, ders ve kurs ilerlemesi, çalışma planı, yer imleri ve zaman damgalı kişisel notlar.                                          |
| Öğrenci   | Süre/deneme sınırı olan testler, dosyalı ödev teslimi ve düzeltme, soru-cevap, yorum/puan, duyurular, bildirimler ve tamamlanan kurs için doğrulanabilir PDF sertifika.                                                                 |
| Eğitmen   | Bölüm/ders hazırlama, metin ve video dersleri, soru bankası, kaynak ve altyazı ekleme, medya kitaplığı, taslak kaydetme, öğrenci ön izlemesi ve yönetici incelemesine gönderme.                                                         |
| Eğitmen   | Metin biçimlendirme düğmeleri ve Markdown ön izlemesi, ders kopyalama, hazır/kişisel kurs şablonları, davetle ortak eğitmen düzenlemesi. Kurs sahibi ayrıca öğrenci ilerlemesi, test sonuçları, ödevler ve kurs raporlarını takip eder. |
| Yönetici  | Kurs onayı veya gerekçeli düzeltme isteği, kullanıcı/rol/durum ve kategori yönetimi, gerekçeli kurs kaydı verme/iptal, destek ve içerik şikâyetleri, işlem geçmişi ve başarısız işlerin takibi.                                         |
| Hesap     | E-posta doğrulama, parola sıfırlama, oturumları görüntüleme/sonlandırma, isteğe bağlı TOTP doğrulaması, bildirim tercihleri, kişisel veri dışa aktarma ve hesap kapatma talebi.                                                         |

Hesap kapatma talebi hesabı devre dışı bırakır ve yönetici incelemesine iletir. Fiziksel veri silme/anonimleştirme, [veri saklama prosedürü](docs/PRIVACY_OPERATIONS.md) kapsamında yürütülür.

### Bir kurs nasıl öğrenciye ulaşır?

1. Eğitmen boş kurs oluşturur veya bir kurs şablonu seçer.
2. Çalışma taslağına bölümler, dersler, metinler, videolar, testler ve kaynaklar ekler. Ortak çalışacaksa kayıtlı ve doğrulanmış eğitmenleri davet eder; erişim davet kabul edilince açılır.
3. **Taslağı kaydet** ile değişiklikleri saklar. Bu işlem yayındaki öğrenci içeriğini değiştirmez.
4. Kurs sahibi **İncelemeye gönder** işlemini yapar. Yönetici içeriği ön izler, onaylar veya gerekçeyle düzeltme ister. Onaylanan sürüm öğrenciye açılır.
5. Öğrenci ücretsiz kursa kaydolur; dersleri, testleri ve ödevleri tamamlar. İlerleme backend tarafından hesaplanır; tamamlanan kurs için sertifika üretimi başlatılır.

Kabul edilmiş ortak eğitmen taslağı ve kursun medya kitaplığını düzenleyebilir. Yayın başvurusu, ekip yönetimi, öğrenci raporları ve notlandırma kurs sahibinde kalır. Eşzamanlı düzenlemede eski sürümün kaydı diğer kişinin değişikliğini ezmez. Ayrıntılar: [eğitmen içerik araçları](docs/AUTHORING.md).

## Klasörlerin görevleri

```text
learning-management-system/
├── frontend/                 # React + TypeScript arayüzü, npm bağımlılıkları ve tarayıcı testleri
├── backend/                  # Spring servisleri, ortak Maven projesi ve veritabanı migration'ları
│   ├── pom.xml
│   ├── docker-compose.yml    # Geliştirme altyapısı: PostgreSQL, Kafka, MinIO, Mailhog
│   ├── init-db/
│   ├── scripts/              # Ortam hazırlama ve gerçek API kontrol betikleri
│   ├── api-gateway/
│   ├── user-service/
│   ├── course-service/
│   ├── enrollment-service/
│   ├── payment-service/
│   ├── content-service/
│   └── notification-service/
├── deploy/                   # Yerel deneme/VPS dağıtımı, HTTPS, izleme ve yedekleme
├── docs/                     # Özellik kuralları, pilot planı ve doğrulama kayıtları
└── .github/workflows/        # GitHub Actions derleme ve otomatik kontrolleri
```

`deploy/`, `docs/` ve `.github/` uygulama kodunun kurulum, dokümantasyon ve test yardımcılarıdır. Spring kodu `backend/`, React kodu `frontend/` içinde kalır. Depo kökünde Maven projesi bulunmaz.

## Sunucu almadan yerel deneme

Bu yol, uygulamayı kendi bilgisayarınızda ayrı bir Docker projesi olarak denemek içindir. Gereksinimler: **Docker Engine, Docker Compose 2.24.4+, Node.js 22.12+**, ilk derlemeler için internet erişimi ve yeterli boş disk. Başlangıç için 16 GB RAM önerilir. Java ve FFmpeg uygulama imajlarında sağlanır.

Komutları depo kökünden çalıştırın:

```bash
bash deploy/scripts/staging.sh prepare
bash deploy/scripts/staging.sh config
bash deploy/scripts/staging.sh up
bash deploy/scripts/staging.sh status
bash deploy/scripts/staging.sh check
```

- `prepare`: bağımsız rastgele anahtarları ve ilk yönetici parolasını özel `deploy/.env.staging` dosyasına oluşturur; mevcut dosyanın üzerine yazmaz.
- `config`: ayarları ve Compose yapılandırmasını denetler.
- `up`: imajları derler ve servisleri başlatır. İlk çalıştırma indirmeler nedeniyle uzun sürebilir.
- `status`: servis durumlarını gösterir. `check`: uygulama/API erişimini ve iç yolların dışarıya kapalı olduğunu kontrol eder.

| Adres                    | Amaç                                               |
| ------------------------ | -------------------------------------------------- |
| `http://127.0.0.1:18088` | Uygulama                                           |
| `http://127.0.0.1:18025` | Deneme e-postaları; Mailhog gerçek posta göndermez |
| `http://127.0.0.1:19090` | Prometheus ölçümleri                               |
| `http://127.0.0.1:19000` | MinIO dosya bağlantılarının adresi                 |

İlk yönetici hesabı `admin@academy.localhost` adresidir. Parolayı `deploy/.env.staging` dosyasındaki `APP_BOOTSTRAP_ADMIN_PASSWORD` değerinden okuyun. Bu dosya Git tarafından dışlanır; paylaşmayın. İlk girişten sonra bootstrap yönetici değişkenlerini kaldırma adımı dahil ayrıntılar [yerel deneme rehberindedir](deploy/STAGING.md).

Bu kurulum HTTP ve yalnızca yerel erişim kullanır; internete açık yayın için [VPS/HTTPS rehberini](deploy/README.md) izleyin. ClamAV ilk açılışta imza indirirken kaynak dosyası yüklemeleri geçici olarak 503 dönebilir.

```bash
bash deploy/scripts/staging.sh down
```

Bu komut konteynerleri kapatır, veri birimlerini silmez. Deneme ortamı `lms-staging` adı ve ayrı veri birimleriyle çalışır.

## Geliştirici olarak çalıştırma

Kod üzerinde çalışmak için frontend'i ve Spring servislerini ayrı süreçler olarak başlatabilirsiniz. Bu yol yukarıdaki Docker uygulama dağıtımına alternatiftir.

### Backend

**Java 25**, Docker Compose, Node.js ve içerik servisinin çalıştığı ortamda **FFmpeg/ffprobe** gerekir. FFmpeg H.264/libx264 ve AAC desteği içermelidir. Maven wrapper depoda bulunur.

```bash
# Depo kökünden:
node backend/scripts/setup-local-env.mjs
cd backend
docker compose up -d
sh user-service/mvnw -B -DskipTests package
```

`setup-local-env.mjs`, Git'e eklenmeyen ortak `backend/.env` dosyasını oluşturur. Tüm servisler aynı `JWT_SECRET` ve `INTERNAL_API_KEY` ile çalışmalıdır. Bu yerel ayarlar üretim ayarlarının yerine geçmez.

Buradaki `docker compose up -d` altyapıyı başlatır; Maven komutu JAR dosyalarını üretir. Ardından her Spring servisini ayrı terminalde başlatın. Örneğin `backend/` dizininden:

```bash
java --enable-preview -jar user-service/target/user-service-1.0.0.jar
```

Diğer servisler için ilgili modülün JAR dosyasını kullanın veya modül içinde `sh mvnw spring-boot:run` çalıştırın. Ortak POM Maven ile çalıştırmada preview ayarını sağlar. IDE/Windows ve ortam değişkenleri için [backend rehberine](backend/README.md) bakın.

| Servis               | Geliştirme portu | Sorumluluk                                              |
| -------------------- | ---------------- | ------------------------------------------------------- |
| API gateway          | 8080             | Frontend'in kullandığı API girişi ve erişim kontrolleri |
| user-service         | 8081             | Kullanıcılar, kimlik doğrulama, oturumlar ve destek     |
| course-service       | 8082             | Kurslar, taslaklar, ekipler, testler ve ödevler         |
| enrollment-service   | 8083             | Kurs kayıtları, öğrenme ilerlemesi ve sertifikalar      |
| payment-service      | 8084             | Mevcut ödeme ve iade entegrasyonu                       |
| content-service      | 8085             | Dosyalar, video dönüştürme ve korumalı medya            |
| notification-service | 8086             | E-posta ve uygulama içi bildirimler                     |

Frontend API isteklerini gateway'e gönderir. Canlı dağıtımda iç servis ve veritabanı portları özel ağda tutulur.

### Frontend

Node.js **20.19+ veya 22.12+** gerekir. Node.js 22.12+ kullanmak dağıtım yardımcılarıyla da uyumludur.

```bash
# Depo kökünden, başka bir terminalde:
cd frontend
npm ci
npm run dev
```

Arayüz `http://localhost:5173` adresinde açılır. `/api` istekleri varsayılan olarak `http://127.0.0.1:8080` gateway'ine yönlendirilir; gerekirse `API_TARGET` ile değiştirilir. Diğer ayarlar [frontend rehberindedir](frontend/README.md).

### Video ve MinIO neden gerekli?

MinIO, videoları, yüklenen dosyaları ve sertifika PDF'lerini saklayan S3 uyumlu nesne deposudur. Veritabanı bu dosyaların kendisini tutmaz. İçerik servisi yüklenen videoyu FFmpeg ile H.264/AAC HLS biçimine dönüştürür; öğrenci oynatıcısı yetkili manifest/segment istekleriyle izler.

**Dosya yüklemek, videoyu öğrenciye yayımlamakla aynı işlem değildir.** Videoyu ilgili kursun taslağındaki derse bağlayın, işleme durumunun hazır olmasını bekleyin, taslağı kaydedip incelemeye gönderin ve yöneticiyle onaylayın. Öğrenci yalnızca başlık/metin görüyorsa önce bu bağlantıyı ve onaylanan sürümü kontrol edin.

Bu geliştirme bulutunda resmî sabit kaynak sürümünden derlenen **gerçek MinIO** ile yükleme, imzalı bağlantılar, yeniden başlatınca dosyanın korunması ve öğrenci video oynatması doğrulandı. Yeni makinede depolamayı ayrıca başlatmanız gerekir. MinIO imaj deposuna erişilemiyorsa [kaynak kodundan imaj oluşturma seçeneğini](deploy/STAGING.md#minio-kayıt-deposuna-erişilemiyorsa) kullanabilirsiniz.

## Kontroller ve doğrulama durumu

Frontend kontrolleri:

```bash
cd frontend
npm test
npm run build
npm run format:check
npx playwright install chrome
PLAYWRIGHT_BROWSER_CHANNEL=chrome E2E_VIDEO=1 npm run test:e2e
```

Video tarayıcı senaryoları FFmpeg gerektirir. Yukarıdaki tarayıcı komutu kontrollü API yanıtlarıyla çalışan senaryoları yürütür. Gerçek backend senaryoları çalışan servisler ve özel fixture dosyaları ister; ayrıntılar [test rehberinde](backend/TESTING.md) ve [yerel deneme rehberindedir](deploy/STAGING.md).

Java birim/sözleşme kontrolleri ve gerçek FFmpeg testleri:

```bash
# Depo kökünden:
cd backend
sh user-service/mvnw -B '-Dtest=*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dlms.ffmpeg.tests=true test
```

Bu seçim Spring bağlamı açan `*ApplicationTests` testlerini içermez. Tam `verify` ve gerçek HTTP akışları için gerekli altyapıyı ayrıca çalıştırın.

23 Ekim 2026 yerel doğrulamasında **140 Java testi, 39 gerçek HTTP senaryosu, 12 frontend birim testi ve 37 tarayıcı senaryosu geçti**. Aynı gerçek tarayıcı senaryolarının 7'si ayrıca Caddy üzerinden kontrol edildi; altı veritabanı yedeği geçici veritabanlarına başarıyla geri yüklendi. Bunlar yeni push sonrasındaki GitHub Actions sonucu değildir.

Tam uygulama Compose dağıtımı ve tüm yeni Docker imajları bu ortamda birlikte çalıştırılmadı. Kamuya açık sunucu/DNS/HTTPS, gerçek SMTP teslimi, ClamAV güncellemesi/taraması ve harici medya yedeğinden geri dönüş ayrıca doğrulanmalıdır. Gerçek kullanıcı pilotu henüz yapılmadı. Kapsam ve sınırlamalar [doğrulama kaydında](docs/VALIDATION.md) açıklanır.

## İleri kurulum ve rehberler

| Belge                                                         | İçerik                                                              |
| ------------------------------------------------------------- | ------------------------------------------------------------------- |
| [Platform kuralları](docs/PLATFORM.md)                        | Taslak/yayın, öğrenme, test/ödev, bildirim ve hesap davranışları    |
| [Eğitmen içerik araçları](docs/AUTHORING.md)                  | Metin editörü, ders kopyalama, şablonlar ve ortak eğitmen yetkileri |
| [Yerel deneme](deploy/STAGING.md)                             | Sunucu almadan ayrı deneme ortamı ve MinIO kaynak imajı             |
| [VPS üzerinde yayın](deploy/README.md)                        | Alan adı, HTTPS, SMTP, Docker, izleme, yedek ve güncelleme          |
| [Backend](backend/README.md) / [Frontend](frontend/README.md) | Ayrı geliştirme projelerinin ayrıntılı ayarları                     |
| [Backend testleri](backend/TESTING.md)                        | Java ve gerçek API kontrollerinin gereksinimleri                    |
| [LMS iş kuralları](backend/LMS_FEATURES.md)                   | İlk yönetici, ücretsiz kayıt, ilerleme ve temel API'ler             |
| [Pilot planı](docs/PILOT.md)                                  | Gerçek öğrenci/eğitmen senaryoları ve izlenecek ölçümler            |
| [Veri işlemleri](docs/PRIVACY_OPERATIONS.md)                  | Veri dışa aktarma, saklama, hesap kapatma ve yönetici incelemesi    |
| [Doğrulama kaydı](docs/VALIDATION.md)                         | Yapılan kontroller ve henüz denenmemiş dağıtım adımları             |
