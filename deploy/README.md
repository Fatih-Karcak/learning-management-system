# VPS üzerinde yayınlama

React ve Spring ayrı klasörlerde kalır. Bu dağıtım React'i Caddy ile, altı Spring servisini ayrı konteynerlerde çalıştırır. PostgreSQL, Kafka, MinIO, ClamAV ve Prometheus da aynı özel Docker ağındadır. Mevcut ödeme servisi isteğe bağlı `payments` profilindedir; ücretsiz kurs pilotunda gerekli değildir.

Bu dosyalar kurulumu tanımlar. Gerçek sunucu, DNS, SMTP ve harici yedekleme hesabı ayrıca sağlanmalıdır. Tek VPS yüksek erişilebilirlik sağlamaz. İki eşzamanlı video dönüştürmesiyle başlangıç için 4–8 vCPU, 16 GB RAM ve yeterli SSD alanı planlayın. Kaynak ihtiyacını pilot ölçümleriyle doğrulayın.

## İlk kurulum

1. Linux sunucuya Docker Engine, Compose v2, Node.js 22+, curl ve Python 3 kurun. Depoyu kendi hesabınızla indirin. DNS'te uygulama ve dosya alan adlarını bu sunucuya yönlendirin.
2. Depo kökünde `cp deploy/.env.example deploy/.env` ve `chmod 600 deploy/.env` çalıştırın. Dosyadaki örnekleri gerçek değerlerle değiştirin. Anahtarları bağımsız ve rastgele üretin; kaynak koda veya `VITE_` değişkenlerine koymayın. `.env` değerlerini tek satır olarak yazın; denetim betiği tırnaksız `ANAHTAR=değer` biçimini okur.
3. İlk yönetici için `APP_BOOTSTRAP_ADMIN_EMAIL` ve en az 12 karakterlik `APP_BOOTSTRAP_ADMIN_PASSWORD` tanımlayın. SMTP hesabını yapılandırın. MFA şifreleme anahtarını JWT anahtarından ayrı, kalıcı ve yedekli saklayın.
4. Yalnızca SSH ve web portlarını dışarıya açın. Veritabanı ve Spring portları Compose tarafından yayımlanmaz. SSH erişimini kendi ağınızla sınırlayın.
5. Kaynak izinlerini doğrulayın: `chmod 755 backend/init-db` ve `chmod 644 backend/init-db/*.sql`. Node.js 22+ ile yapılandırma kontrolünü çalıştırın:

```bash
node deploy/scripts/check-config.mjs deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yml build
docker compose --env-file deploy/.env -f deploy/compose.yml up -d
```

Caddy iki alan adı için TLS sertifikası alır. DNS doğru olmalı ve 80/443 portları ulaşılabilir olmalıdır. MinIO'nun yönetim konsolu dışarıya açılmaz. `S3_DOMAIN`, tarayıcıya verilen süreli görsel/sertifika bağlantılarının erişilebilir adresidir.

```bash
docker compose --env-file deploy/.env -f deploy/compose.yml ps
bash deploy/scripts/smoke.sh https://uygulama-alan-adiniz
```

Yöneticiyle giriş yapın, MFA'yı açın ve kurulumda gösterilen anahtarı güvenli yerde saklayın. Başlangıç yönetici değişkenlerini `.env` dosyasından kaldırıp kullanıcı servisini yeniden oluşturun. Diğer yöneticilere **Tam yönetim**, **Kurs moderasyonu** veya **Destek** kapsamı verin. Bir kapsam değiştiğinde hedefin açık oturumları kapanır.

ClamAV ilk başlangıçta imza verilerini indirir. Tarayıcı hazır değilse PDF/TXT/VTT yüklemeleri 503 ile reddedilir; taramayı kapatarak yayına çıkmayın. Gerçek bir temiz dosya ve güvenlik test dosyasıyla tarayıcı bağlantısını ayrıca doğrulayın.

## Yedek ve geri dönüş

```bash
bash deploy/scripts/backup.sh /guvenli-yedek-dizini
bash deploy/scripts/restore-check.sh /guvenli-yedek-dizini/zaman-lms_courses.dump
```

Yedek betiği altı veritabanını ayrı özel biçimli dosyalara alır ve SHA-256 listesi oluşturur. Dosyalar gizli izinlerle yazılır. Geri dönüş denemesi benzersiz, geçici bir veritabanı oluşturur; migration geçmişini kontrol eder ve bu veritabanını kaldırır. Canlı veritabanlarının üzerine yazmaz. Her servis yedeğini kontrol edin.

Veritabanları ayrı anlarda yedeklenir. Servisler arası aynı ana ait bir geri dönüş gerekiyorsa yazmaları durdurup yedek alın veya yönetilen PostgreSQL üzerinde koordineli PITR kullanın. Yedekleri şifreleyip sunucudan bağımsız bir konuma kopyalayın. Anahtar yedeklerini veritabanı yedeğinden ayrı koruyun.

Medya için bağımsız S3 hesabında özel ve sürümlemeli bir bucket oluşturun. `BACKUP_S3_*` değişkenlerini güvenli `.env` dosyasına ekleyin:

```bash
node deploy/scripts/backup-media.mjs deploy/.env
```

Bu işlem hedefteki nesneleri silmeden dört bucket'ı kopyalar. İlk kullanımda hedef yetkilerini doğrulayın ve örnek video, altyazı ve sertifikayı yedekten ayrı test ortamına geri getirip açın. **Veritabanı yedeği videoları içermez.** Betik burada harici S3 hesabıyla çalıştırılmış sayılmaz.

## Güncelleme ve işletim

- Önce CI testlerini çalıştırın, ardından aynı değişikliği ayrı test sunucusunda deneyin. Migration'lar açılışta Flyway ile uygulanır.
- Her dağıtıma farklı `RELEASE_TAG` verin. Dağıtımdan önce veritabanı ve medya yedeklerini doğrulayın. `up -d --build` ile yeniden oluşturup smoke testini tekrarlayın.
- Şema değişmişse eski imajı doğrudan açmak güvenli bir geri dönüş değildir. Uyumluluğu test edin; gerekirse uygulama ve veriyi doğrulanmış yedekle birlikte ayrı ortamda geri getirin.
- Prometheus yalnızca `127.0.0.1:9090` üzerinde erişilir. SSH tüneliyle açın. Servis erişimi ve HTTP 5xx oranı için alarm kuralları vardır. Uyarıları dışarıya göndermek için kurumunuzun Alertmanager/alıcı ayarları ayrıca gerekir.
- Yönetici işletim ekranında başarısız bildirimler, veri talepleri ve video işleri görünür. Videolar iki işçiyle dönüştürülür; süreç kesilirse `PROCESSING` kayıtları tekrar ele alınır. Başarısız işler yeniden başlatılabilir.
- Gateway istek kimliğini `X-Request-Id` başlığında verir. Loglarda sorgu metni, parola veya token yazdırılmaz. Uygulama loglarını ve disk kullanımını izleyin.
- SMTP teslimini gerçek posta kutusuyla; HTTPS, MinIO imzalı URL'leri, ClamAV ve harici yedeklemeyi gerçek sunucuda doğrulayın. Yerel test sonucu bunların yerine geçmez.

## CI

`.github/workflows/verify.yml` Java 25 testlerini, gerçek FFmpeg kontrollerini, TypeScript/Vite derlemesini, API istemcisi testlerini ve tarayıcı senaryolarını çalıştırır. Ortama bağlı gerçek servis senaryoları ayrıca `backend/scripts/platform-workflows.test.mjs` ile çalıştırılır. Workflow otomatik dağıtım veya Git push yapmaz.

## Kurumsal proxy ile imaj derleme

Frontend Dockerfile isteğe bağlı `build_ca` BuildKit secret'ını destekler. Özel CA gerekiyorsa sertifikayı bu kanaldan sağlayın; TLS doğrulamasını kapatmayın. Docker derleme ortamı paket deposunu ve tanımlı proxy'nin DNS adını çözebilmelidir. Proxy ayarlarını Docker'ın standart build argümanlarıyla iletin; parola veya token'ı Dockerfile'a yazmayın. Yayımlanabilir Stripe anahtarı ödeme profili kullanılacaksa `VITE_STRIPE_PUBLISHABLE_KEY` ile derlenir; gizli Stripe anahtarları yalnızca backend ortamındadır.

Bu bulutta MinIO imajları için kayıt defteri erişimi reddedildi. Üretim sunucusunda imaj erişimini önceden doğrulayın; kurumunuzun onaylı imaj aynası gerekiyorsa Compose imaj kaynağını o kaynağa göre düzenleyin. Bu engel uygulamanın S3 test sunucusuyla doğrulanmasıyla giderilmiş sayılmaz.
