# LMS Backend

Bu klasör yalnızca Java 25 / Spring Boot backend'ini içerir. React + TypeScript arayüzü ayrı [frontend/](../frontend/) klasöründedir. Buradaki komutlar `backend/` çalışma dizininden çalıştırılır.

Yeni yönetici/kurs onayı, ücretsiz kayıt, test/ödev, video ilerlemesi, soru-cevap, kişisel notlar ve ilk yönetici kurulumu [LMS_FEATURES.md](LMS_FEATURES.md) içinde açıklanır. Eğitmen yayın isteği artık yönetici incelemesine gönderilir.

## Bağımlılıklar ve altyapı

- Java 25; derleme ve çalışma sırasında preview özellikleri açık olmalıdır.
- Modüllerde bulunan Maven 3.9.16 wrapper.
- Docker Compose ile PostgreSQL, ZooKeeper/Kafka, MinIO ve MailHog.
- İçerik servisinin çalıştığı makinede `ffmpeg` ve `ffprobe` (H.264/libx264 ve AAC desteğiyle).

```bash
node scripts/setup-local-env.mjs
docker compose up -d
sh user-service/mvnw -B -DskipTests package
```

Compose projesinin adı `learning-management-system` olarak sabittir. Böylece dosyalar `backend/` altına taşındığında mevcut veritabanı ve nesne deposu volume'ları korunur. `init-db/` yolları bu klasöre göre çözülür. Veritabanlarını silmek istemiyorsanız `docker compose down -v` kullanmayın.

## Ortam değişkenleri

Yerel geliştirme için Node.js ile, depo kökünden bir kez çalıştırın:

```powershell
node .\backend\scripts\setup-local-env.mjs
```

Bu komut `backend/.env` dosyasını örnek Compose ayarlarıyla oluşturur; `JWT_SECRET` ve `INTERNAL_API_KEY` için ayrı rastgele anahtarlar üretir. Terminalde mevcut anahtarlar varsa onları kullanır. Var olan `.env` dosyasına dokunmaz, hiçbir anahtarı ekrana yazmaz. Dosya Git tarafından yok sayılır. Bu işlemden sonra **tüm backend servislerini yeniden başlatın**; kullanıcı servisi, gateway ve enrollment aynı anahtarları kullanmalıdır.

Spring servisleri ortak `.env` dosyasını depo kökü, `backend/` veya bir servis klasöründen başlatıldıklarında otomatik yükler. IntelliJ'de ana sınıfı yeniden çalıştırmanız yeterlidir. İşletim sistemi ortam değişkenleri ve JVM/komut satırı ayarları dosyadan daha yüksek önceliklidir; IDE'de eski/boş `INTERNAL_API_KEY` veya `JWT_SECRET` ayarları varsa kaldırın ya da ortak değerlerle eşleştirin.

Yerel enrollment servisini Windows'ta Maven ile başlatmak için:

```powershell
cd .\backend\enrollment-service
.\mvnw.cmd spring-boot:run
```

Üretimde yerel örnek parolaları kullanmayın; değerleri güvenli süreç ortamından sağlayın. Tüm servislerde aynı `JWT_SECRET` ve en az 32 karakterlik aynı `INTERNAL_API_KEY` kullanılmalıdır. `DB_PASSWORD`, `MINIO_ACCESS_KEY` ve `MINIO_SECRET_KEY` kullanılan PostgreSQL/S3 hizmetiyle eşleşmelidir. Ortak `.env` dosyasını elle hazırlayacaksanız `KEY=value` biçimini kullanın; `export` veya tırnak eklemeyin.

Ödeme için `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` backend'e; yayınlanabilir Stripe anahtarı frontend'e aittir. E-posta doğrulama bağlantılarının arayüzde açılması için bildirim servisinde `APP_FRONTEND_BASE_URL` kullanın.

## Servisleri çalıştırma

Ortak değişkenler süreç ortamına aktarıldıktan sonra her servisi ayrı bir terminalde başlatabilirsiniz:

```bash
# backend/ dizininden örnek:
java --enable-preview -jar user-service/target/user-service-1.0.0.jar
```

Alternatif olarak bir modülün içinden `sh mvnw spring-boot:run` çalıştırılabilir; preview JVM parametresi ortak POM'da tanımlıdır.

| Servis            | Port |
| ----------------- | ---- |
| API gateway       | 8080 |
| Kullanıcı         | 8081 |
| Kurs              | 8082 |
| Kayıt ve ilerleme | 8083 |
| Ödeme             | 8084 |
| İçerik ve medya   | 8085 |
| Bildirim          | 8086 |

Frontend yalnızca gateway'e bağlanır. İç servis portlarını dışarıya açmayın. PostgreSQL ve Kafka ortak altyapıdır; içerik/video/sertifika işlemleri ayrıca çalışan MinIO/S3 gerektirir. Bu bulut ortamındaki MinIO indirme engeli, klasör düzeninden bağımsız olarak devam eder.

## Ders videosu ekleme ve oynatma

Medya kitaplığına dosya yüklemek ile videoyu derse eklemek ayrı işlemlerdir. Kitaplıkta **Video** türünü, ilgili kursu ve dersi seçerek yüklediğinizde arayüz videoyu otomatik derse bağlar. Ders seçilmeden yüklenen dosya yalnızca kitaplıkta kalır. Mevcut metin derslerini düzeltmek için **Eğitmen stüdyosu → Kurs → Kurs müfredatı → Video ekle** yolundan hazır videoyu seçip **Videoyu derse bağla** düğmesine basın. Ders video türüne geçer; yazılı içerik korunur.

Kurs servisi ders sahibini doğrular, içerik servisine ortak `INTERNAL_API_KEY` ile videoyu bağlar ve göreli gateway adresini kaydeder. İçerik servisi videonun aynı eğitmene ait olduğunu doğrular. Başka kursa bağlı video taşınamaz; ilgili kurs için tekrar yüklenmelidir. Daha önce kurs seçilmeden yüklenen video bağlanırken kurs bilgisi de tamamlanır; böylece kayıtlı öğrenciler manifest ve segmentlere erişebilir. Kurs ve içerik servislerinin aynı anahtarla çalışması gerekir. Bu değişiklikten sonra ikisini yeniden başlatın ve frontend'i yenileyin.

FFmpeg kaynak codec'ini kopyalamak yerine H.264/AAC HLS çıktısı üretir. Sessiz ve bir saniyeden kısa videolar desteklenir. Dosyanın yüklenmiş olması, işlenmesinin tamamlandığı anlamına gelmez; öğrenci oynatıcısı **Video hazırlanıyor** durumunu gösterir ve hazır olduğunda akışı açar. **Başarısız** durumundaki dosyada kitaplık ayrıntısını kontrol edin; eski uyumsuz çıktılar için videoyu yeniden yükleyin.

Windows'ta `ffmpeg -version` ve `ffprobe -version` komutlarının içerik servisini başlattığınız ortamda çalıştığını kontrol edin. PATH yerine `backend/.env` içinde tam yollar da belirtilebilir:

```properties
VIDEO_FFMPEG=C:/ffmpeg/bin/ffmpeg.exe
VIDEO_FFPROBE=C:/ffmpeg/bin/ffprobe.exe
```

Farklı makine veya container kullanılıyorsa kurs servisinde `CONTENT_SERVICE_URL`, içerik servisinde `ENROLLMENT_SERVICE_URL` erişilebilir iç servis adreslerini göstermelidir.

## Testler

```bash
sh user-service/mvnw -B verify
```

Tam test için altyapı servisleri ve ortam değişkenleri gereklidir. Altyapı gerektirmeyen regresyon komutu ve iş kuralları [TESTING.md](TESTING.md) dosyasındadır.

Yerel ayar oluşturucusunun testleri depo kökünden `node --test backend/scripts/setup-local-env.test.mjs` ile çalışır.
