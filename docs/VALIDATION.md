# Doğrulama kaydı — 23 Ekim 2026

| Kontrol                                              | Sonuç                                                                       |
| ---------------------------------------------------- | --------------------------------------------------------------------------- |
| Yedi modülde Java `*Test` seçimi                     | **140 geçti**, hata/atlama yok                                              |
| Eski LMS HTTP entegrasyonları                        | **13 geçti**                                                                |
| Yeni platform HTTP entegrasyonları                   | **18 geçti**                                                                |
| Ortak düzenleme, şablon ve gerçek S3 HTTP testleri   | **8 geçti**                                                                 |
| Frontend API / oturum yenileme / ders kopyalama      | **12 geçti**                                                                |
| Chrome for Testing 156 tarayıcı senaryoları          | **37 geçti**                                                                |
| Üretim React çıktısı + Caddy deneme yapılandırması   | Aynı gerçek servis senaryolarının **7'si ayrıca geçti**                     |
| TypeScript / Vite üretim derlemesi                   | Geçti                                                                       |
| Frontend biçim kontrolü / Git boşluk kontrolü        | Geçti                                                                       |
| Yerel ortam üreticisi                                | **5 geçti**                                                                 |
| Dağıtım / SMTP / özel deneme ayarı denetimi          | **6 geçti**                                                                 |
| PostgreSQL yedek ve geçici veritabanına geri yükleme | **6/6 geçti**                                                               |
| Compose yapılandırması / kabuk betikleri             | Geçti                                                                       |
| Caddy'nin kendi yapılandırma doğrulaması             | Geçti                                                                       |
| Gerçek MinIO kaynak derlemesi ve imza doğrulaması    | Geçti                                                                       |
| MinIO yeniden başlatılınca dosyanın korunması        | Geçti; aynı imzalı bağlantıdan aynı dosya alındı                            |
| Dışarıya kapalı yollar                               | Uygulama ve katalog 200; `/api/v1/internal/…` ve `/actuator/prometheus` 404 |

Java sayısı bağlam gerektirmeyen birim/sözleşme testlerini ve gerçek FFmpeg kontrollerini kapsar. `*ApplicationTests` seçilmedi. Bunun yerine uygulamalar ayrıca başlatıldı; gateway üzerinden gerçek PostgreSQL, Kafka ve yerel SMTP yakalayıcısıyla HTTP/tarayıcı akışları doğrulandı. Rastgele adlandırılmış geliştirme hesapları, kurslar ve dosyalar oluşturuldu.

Video yükleme, FFmpeg ile H.264/AAC HLS dönüştürmesi, Chrome'da gerçek görüntü çözme, WebVTT altyazısı, izleme ilerlemesi, özel ödev dosyası erişimi, test süresi/hakkı, taslak onayı, ders taşıma, parola sıfırlama e-postası, TOTP, refresh tekrar kullanımının reddi ve yönetici kapsamları kontrol edildi. İstanbul saat diliminde test son tarihinin 30 saniye ileride kaldığı ayrıca test edildi.

Bu kontrolde **gerçek MinIO** kullanıldı. Resmî imaj erişimi ve doğrudan ikili indirme sağlanamadığından resmî `RELEASE.2025-04-22T22-12-26Z` kaynağı (`0d7408fc9969caf07de6a8c3a84f9fbb10a6739e`) Go 1.24.2 ile derlendi. Engellenen Go bağımlılığı aynı sürümün resmî GitHub kaynak arşivinden sağlandı; TLS doğrulaması kapatılmadı. Derleyicinin geçici dosyaları bellek üzerinde tutuldu. İmzasız ve değiştirilmiş imzalı nesne istekleri 403 aldı, geçerli süreli bağlantı Caddy üzerinden açıldı. MinIO durdurulup aynı veri diziniyle başlatıldığında dosya SHA-256 değeri değişmedi. Önceki çalışmanın imza doğrulamayan S3 taklidi bu sonuçların kaynağı değildir.

Paylaşılan CI çıktısındaki iki video senaryosu dahil 37 senaryo Chrome for Testing 156 ile geçti. CI artık Chrome kanalını açıkça seçer. Video testleri H.264 desteğini, tamponlanmış görüntüyü, ilerleyen oynatmayı ve çözülen kareleri denetler. Yeni GitHub Actions çalışmasının sonucu henüz alınmadı.

Caddy konteynerine güncel Vite üretim çıktısı ve yerel deneme yapılandırması bağlandı; gerçek servislerle yedi tarayıcı akışı ve dışarıya kapalı yollar kontrol edildi. Konteynerdeki adresler yerel host servislerine yönlendirildi. Bütün Spring servislerini yeni imajlarla başlatan tam Compose kurulumu burada çalıştırılmadı. Önceki çalışmada frontend Dockerfile imajı üretilmişti; bu revizyonun yeni frontend imajı ve MinIO kaynak Dockerfile'ı ayrıca derlenmiş sayılmaz.

Derlenmiş MinIO ikilisiyle ek bir çalıştırma imajı üretildi; bu imajın açılış kontrolü ortamın diski dolduğu için başarısız oldu (`no space left on device`). Test konteyneri, ona ait boş veri birimi/imaj ve derleme önbelleği kaldırıldı. Bu ek konteyner kontrolü başarılı sayılmadı; kaynak Dockerfile'ın tam derlemesi ve çalıştırılması yeterli disk bulunan ortamda ayrıca doğrulanmalıdır. Host üzerinde gerçek MinIO ile yapılan video, imza ve yeniden başlatma kontrolleri yukarıdaki başarılı sonuçlardır.

Backend uygulama Docker imajlarının tamamı burada derlenmedi. Yedi Spring modülü Java 25 ile derlenip test edildi. Sunucu ve alan adı henüz sağlanmadığından kamuya açık yayın, gerçek alan adında TLS sertifikası verme/yenileme ve dış SMTP teslimi yapılmadı. ClamAV imza güncellemesi ve harici S3 yedeğinden medya geri dönüşü de doğrulanmadı. [Yerel deneme](../deploy/STAGING.md) ve [VPS dağıtım](../deploy/README.md) rehberleri hazırdır. Ödeme sağlayıcısında gerçek işlem yapılmadı.

Doğrulama sonunda geçici Caddy test konteyneri durdurulur; frontend, gerçek MinIO ve yedi Spring servisi geliştirme için açık bırakılır. Geliştirme veritabanında oluşturulan örnek hesaplar, kurslar ve dosyalar bulunur. Gerçek pilot kullanıcılarla test yapılmadı.

Hesap silme özelliği hesabı kapatan ve yönetici incelemesine başvuru oluşturan bir süreçtir. Fiziksel veri silme/anonimleştirme kurumun saklama prosedürüyle yürütülür; bu işlem otomatik tamamlanmış sayılmaz. Gerçek kullanıcı araştırması veya pilot gerçekleştirilmedi; [pilot planı](PILOT.md) hazırlandı.
