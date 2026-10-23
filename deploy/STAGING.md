# Sunucu almadan deneme ortamı

Bu kurulum yalnızca yerel bilgisayar için HTTP kullanır. Web, dosya, e-posta görüntüleyici ve izleme portları `127.0.0.1` adresine bağlanır. İnternete açık yayın için [VPS rehberini](README.md) ve gerçek alan adı/HTTPS/SMTP ayarlarını kullanın.

## Başlatma

Docker Engine, Compose **2.24.4+** ve Node.js 22+ gerekir. Üretimden ayrı `lms-staging` projesi ve ayrı veri birimleri kullanılır. Derleme ve video dönüştürme için yeterli boş disk ve bellek ayırın; başlangıçta 16 GB RAM önerilir.

```bash
bash deploy/scripts/staging.sh prepare
bash deploy/scripts/staging.sh config
bash deploy/scripts/staging.sh up
bash deploy/scripts/staging.sh status
bash deploy/scripts/staging.sh check
```

`prepare` bağımsız rastgele anahtarları ve ilk yönetici parolasını `deploy/.env.staging` dosyasına 0600 izinle yazar; mevcut dosyanın üzerine yazmaz. Dosya Git tarafından dışlanır ve parolalar terminale basılmaz. İlk yönetici `admin@academy.localhost` hesabıdır; parolayı özel ayar dosyanızdan okuyun. İlk girişten sonra bootstrap yönetici değişkenlerini dosyadan kaldırıp kullanıcı servisini yeniden oluşturun.

| Adres                    | İşlev                                                  |
| ------------------------ | ------------------------------------------------------ |
| `http://127.0.0.1:18088` | Uygulama                                               |
| `http://127.0.0.1:18025` | Deneme e-postaları (Mailhog; gerçek e-posta göndermez) |
| `http://127.0.0.1:19090` | Prometheus                                             |
| `http://127.0.0.1:19000` | Süreli dosya bağlantılarının S3 adresi                 |

İlk açılışta ClamAV imza indirirken kaynak yüklemeleri 503 dönebilir; hazır olmasını bekleyin. Deneme kurulumu da gerçek MinIO kullanır. İç servis, veritabanı ve MinIO konsolu dışarıya açılmaz. Bir komutun başarılı olması bütün bağımlılıkların hazır olduğu anlamına gelmez; `status` ve `check` sonuçlarını doğrulayın.

## MinIO kayıt deposuna erişilemiyorsa

Resmî kaynak kodunun sabit sürümünden kendi imajınızı oluşturabilirsiniz:

```bash
docker build -f deploy/minio/Dockerfile -t lms/minio-source:2025-04-22 deploy/minio
```

Özel ayar dosyanıza `MINIO_IMAGE=lms/minio-source:2025-04-22` ekleyip kurulumu yeniden çalıştırın. Dockerfile, resmî `RELEASE.2025-04-22T22-12-26Z` etiketinin commit kimliğini doğrular; kaynak derlemesi Go modül depolarına erişim gerektirir. Bu imaj yerel olarak oluşturulur, bir kayıt deposuna otomatik yayımlanmaz. MinIO'nun AGPLv3 lisansı son imaja eklenir; resmî kaynak adresi Dockerfile'dadır.

Bu geliştirme bulutunda aynı sabit kaynak sürümünden gerçek MinIO derlenip çalıştırıldı. Dockerfile'ın bütün kaynak derleme aşamaları burada ayrıca çalıştırılmış sayılmaz; gerçek sunucuda imaj üretimini doğrulayın.

## Akış testleri

Uygulama açıldıktan sonra bir eğitmenle kurs hazırlayın, yöneticiyle onaylayın, öğrenciyle kaydolup videoyu izleyin. Bir dosya yükleyip indirin; e-posta doğrulama ve parola sıfırlama bağlantılarını Mailhog üzerinden deneyin.

Otomatik HTTP senaryoları için `RUN_LMS_E2E=1`, `LMS_E2E_API_URL=http://127.0.0.1:18088/api/v1`, `LMS_E2E_MAILHOG_URL=http://127.0.0.1:18025`, `LMS_E2E_ADMIN_EMAIL` ve `LMS_E2E_ADMIN_PASSWORD` değişkenlerini özel ortamınızdan sağlayın. Gerçek kullanıcı veritabanına karşı çalıştırmayın; senaryolar deneme hesapları ve kurslar oluşturur.

```bash
node --test backend/scripts/lms-features.test.mjs
node --test backend/scripts/platform-workflows.test.mjs
node --test backend/scripts/course-authoring.test.mjs
```

Son iki betik için sırasıyla `LMS_E2E_FIXTURE_PATH` ve `LMS_AUTHORING_FIXTURE_PATH` ile özel geçici JSON dosyaları tanımlayabilirsiniz. Bu dosyalar oturum token'ları içerir; paylaşmayın ve işiniz bitince silin. Tarayıcı testlerini `frontend` içinden `E2E_BASE_URL=http://127.0.0.1:18088`, `E2E_REAL_BACKEND=1` ve aynı fixture yollarıyla çalıştırın. Chrome için `PLAYWRIGHT_BROWSER_CHANNEL=chrome` ve `npx playwright install chrome` kullanın. `E2E_VIDEO=1` video senaryolarını etkinleştirir; FFmpeg gerekir.

## Yedek ve durdurma

Deneme veritabanlarını kontrol etmek için `LMS_ENV_FILE=deploy/.env.staging`, `LMS_COMPOSE_OVERRIDE=deploy/compose.staging.yml` ve `LMS_PROJECT_NAME=lms-staging` ayarlayıp mevcut yedek betiklerini kullanın:

```bash
bash deploy/scripts/backup.sh /guvenli-deneme-yedekleri
bash deploy/scripts/restore-check.sh /guvenli-deneme-yedekleri/zaman-lms_courses.dump
```

Her servis yedeğini ayrı kontrol edin. Veritabanı yedeği medya dosyalarını içermez; medya için bağımsız S3 yedeği ve geri dönüş denemesi gerekir. `bash deploy/scripts/staging.sh down` konteynerleri kapatır, veri birimlerini silmez. Gerçek DNS, TLS sertifikası verme/yenileme, dış SMTP ve harici yedekleme bu yerel denemeyle doğrulanmış sayılmaz.
