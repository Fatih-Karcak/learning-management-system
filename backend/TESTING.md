LMS geliştirme kontrolleri

Projeyi Java 25 ve modüllerdeki Maven 3.9.16 wrapper ile çalıştırın. Derleme ve çalışma sırasında preview özellikleri açık olmalıdır. PostgreSQL, Kafka ve S3 uyumlu nesne deposu; bildirim denemelerinde ayrıca yerel SMTP yakalayıcısı gerekir. Veritabanları `init-db` betikleriyle oluşturulur; yeni Flyway migration dosyaları mevcut kayıtları koruyarak uygulanır.

Tüm servislerde aynı `JWT_SECRET` ve en az 32 karakterlik aynı `INTERNAL_API_KEY` kullanılmalıdır. `INTERNAL_API_KEY`, gateway'in hesap durumu kontrolünde ve servisler arası çağrılarda kullanılır. Anahtarlar yalnızca güvenli ortam değişkenlerinde veya Git tarafından yok sayılan yerel yapılandırmada tutulmalıdır. `DB_PASSWORD`, `MINIO_ACCESS_KEY` ve `MINIO_SECRET_KEY`, kullanılan PostgreSQL/S3 hizmetinin ayarlarıyla eşleşmelidir. Örnek yerel altyapı ayarları `docker-compose.yml` içindedir. Depo kökünden `node backend/scripts/setup-local-env.mjs` komutu ortak `backend/.env` dosyasını oluşturur; Spring yapılandırması bu dosyayı otomatik yükler. Mevcut süreç ortamı değerleri önceliklidir.

`backend/` dizininden tam doğrulama:

```sh
sh user-service/mvnw -B verify
```

Bu komut yedi modülü derler, mevcut uygulama bağlamı testlerini ve güvenlik/iş kuralı regresyonlarını çalıştırır. Uygulama bağlamı testleri yerel altyapıyı kullanır. Yalnızca altyapı gerektirmeyen regresyonları çalıştırmak için:

```sh
sh user-service/mvnw -B \
  -Dtest=AccessTokenValidationTest,GatewaySecurityTest,AccountRegressionTest,InternalServiceAuthFilterTest,PaymentRegressionTest,RefundRegressionTest,CourseClientContractTest,CourseRegressionTest,MediaDeletionTest,MediaPlaybackTest,InternalMediaAccessTest,LessonVideoTest,ContentClientContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DargLine=--enable-preview test
```

Gerçek FFmpeg ile uyumsuz MPEG-4/PCM kaynağının H.264/AAC HLS'e çevrildiğini, segmentin çözülebildiğini, sessiz/kısa videoyu ve bozuk dosya hatasını doğrulamak için:

```sh
sh user-service/mvnw -B -pl content-service \
  -Dtest=VideoProcessingTest -Dlms.ffmpeg.tests=true test
```

Bu üç test `ffmpeg`/`ffprobe` gerektirir; özellik verilmediğinde atlanır. Nesne deposu taklit edilir; gerçek MinIO dağıtımını doğrulamaz. `MediaPlaybackTest` kayıtlı öğrencinin manifest/segment erişimini ve kayıtsız/iade edilmiş erişimin reddini sınar. `LessonVideoTest` metin derse video eklemeyi, sahiplik kontrolünü ve içerik servisi hatalarının başarılı işlem gibi dönmemesini doğrular.

Ödeme istemcisi sözleşme testi yerel HTTP sunucusunu kullanır. Ödeme başlatma birim testi Stripe SDK çağrısını taklit eder; gerçek kart işlemi yapmaz. Gerçek ödeme/para iadesi doğrulaması için ayrıca Stripe test hesabı ve SDK ile uyumlu webhook API sürümü gerekir. İmzalı webhook'lar için kullanılan anahtar ödeme servisinin yapılandırmasıyla eşleşmelidir.

Güvenlik ve iş kuralları:

- Herkese açık kayıt STUDENT veya INSTRUCTOR oluşturabilir; ADMIN ataması bu uç noktadan yapılamaz.
- Kullanıcı kimliği ve rolü servislerde doğrulanmış JWT/Authentication nesnesinden alınır. İstemcinin kimlik başlıkları yetki kaynağı değildir.
- Gateway pasif/yasaklı hesapları ve rolü değişmiş eski token'ları reddeder. Hesap durumunu doğrulayamıyorsa korumalı isteği iletmez.
- Refresh token API erişim token'ı olarak kullanılamaz. Internal API'ler servis anahtarı ister; iç servis portları ayrıca özel ağda tutulmalıdır.
- Taslaklar sahibi/yönetici tarafından görülebilir. Anonim veya kayıtlı olmayan öğrenciye ders metni/video adresi yalnızca ön izlemeye açık derslerde döner. Kurs meta verisi ve ders kimlikleri katalog/kayıt iş akışı için korunur.
- Kurs yayımlanırken en az bir ders bulunmalıdır; yayımlanmış kursun son dersi silinemez. Yorum yapmak için aktif/tamamlanmış kurs kaydı gerekir.
- Ödeme durumu ile bildirim olayları aynı veritabanı işleminde payment_outbox'a yazılır. Gönderilemeyen olaylar tekrar denenir. Yinelenen başarı bildirimi terminal ödeme durumunu değiştirmez; aynı ödeme için ikinci makbuz olayı oluşturmaz.
- Kurs sayacı enrollment kimliğiyle tekrar işlemeyi engeller. Mesajlaşma en az bir kez teslim esasına dayanır; üretim gözlemlemesinde outbox kuyruğu ve Kafka hata konuları izlenmelidir.

Çalışan sistemin işlevsel kontrolü yalnızca sağlık uç noktalarına dayanmamalıdır: farklı hesaplarla ödeme/kayıt sahipliğini, ücretli içerik erişimini, iade sonrası erişimi, görsel yükleyip silmeyi, video/HLS akışını ve sertifika PDF'i üretip indirmeyi de sınayın. S3 test sunucusuyla başarılı deneme, gerçek MinIO dağıtımını veya gerçek Stripe işlemlerini doğrulamaz.
