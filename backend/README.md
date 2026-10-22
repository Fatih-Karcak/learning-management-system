# LMS Backend

Bu klasör yalnızca Java 25 / Spring Boot backend'ini içerir. React + TypeScript arayüzü ayrı [frontend/](../frontend/) klasöründedir. Buradaki komutlar `backend/` çalışma dizininden çalıştırılır.

## Bağımlılıklar ve altyapı

- Java 25; derleme ve çalışma sırasında preview özellikleri açık olmalıdır.
- Modüllerde bulunan Maven 3.9.16 wrapper.
- Docker Compose ile PostgreSQL, ZooKeeper/Kafka, MinIO ve MailHog.

```bash
docker compose up -d
sh user-service/mvnw -B -DskipTests package
```

Compose projesinin adı `learning-management-system` olarak sabittir. Böylece dosyalar `backend/` altına taşındığında mevcut veritabanı ve nesne deposu volume'ları korunur. `init-db/` yolları bu klasöre göre çözülür. Veritabanlarını silmek istemiyorsanız `docker compose down -v` kullanmayın.

## Ortam değişkenleri

Tüm servislerde aynı `JWT_SECRET` ve en az 32 karakterlik aynı `INTERNAL_API_KEY` kullanılmalıdır. `DB_PASSWORD`, `MINIO_ACCESS_KEY` ve `MINIO_SECRET_KEY` yerel altyapı ayarlarıyla eşleşmelidir. Değerleri güvenli ortam değişkenleri veya Git'in yok saydığı yerel yapılandırma ile sağlayın. Compose'un `.env` dosyası Java süreçlerine kendiliğinden aktarılmaz.

Ödeme için `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` backend'e; yayınlanabilir Stripe anahtarı frontend'e aittir. E-posta doğrulama bağlantılarının arayüzde açılması için bildirim servisinde `APP_FRONTEND_BASE_URL` kullanın.

## Servisleri çalıştırma

Ortak değişkenler süreç ortamına aktarıldıktan sonra her servisi ayrı bir terminalde başlatabilirsiniz:

```bash
# backend/ dizininden örnek:
java --enable-preview -jar user-service/target/user-service-1.0.0.jar
```

Alternatif olarak bir modülün içinden `sh mvnw spring-boot:run` çalıştırılabilir; preview JVM parametresi ortak POM'da tanımlıdır.

| Servis | Port |
| --- | --- |
| API gateway | 8080 |
| Kullanıcı | 8081 |
| Kurs | 8082 |
| Kayıt ve ilerleme | 8083 |
| Ödeme | 8084 |
| İçerik ve medya | 8085 |
| Bildirim | 8086 |

Frontend yalnızca gateway'e bağlanır. İç servis portlarını dışarıya açmayın. PostgreSQL ve Kafka ortak altyapıdır; içerik/video/sertifika işlemleri ayrıca çalışan MinIO/S3 gerektirir. Bu bulut ortamındaki MinIO indirme engeli, klasör düzeninden bağımsız olarak devam eder.

## Testler

```bash
sh user-service/mvnw -B verify
```

Tam test için altyapı servisleri ve ortam değişkenleri gereklidir. Altyapı gerektirmeyen regresyon komutu ve iş kuralları [TESTING.md](TESTING.md) dosyasındadır.
