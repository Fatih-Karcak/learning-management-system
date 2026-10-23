# Doğrulama kaydı — 23 Ekim 2026

| Kontrol                                              | Sonuç                                                                       |
| ---------------------------------------------------- | --------------------------------------------------------------------------- |
| Yedi modülde Java `*Test` seçimi                     | **133 geçti**, hata/atlama yok                                              |
| Eski LMS HTTP entegrasyonları                        | **13 geçti**                                                                |
| Yeni platform HTTP entegrasyonları                   | **18 geçti**                                                                |
| API istemcisi / oturum yenileme                      | **11 geçti**                                                                |
| Chrome tarayıcı senaryoları                          | **30 geçti**                                                                |
| Üretim React çıktısı + Caddy imajı                   | Aynı gerçek platform senaryolarının **5'i ayrıca geçti**                    |
| TypeScript / Vite üretim derlemesi                   | Geçti                                                                       |
| Frontend biçim kontrolü / Git boşluk kontrolü        | Geçti                                                                       |
| Yerel ortam üreticisi                                | **5 geçti**                                                                 |
| Dağıtım / SMTP yapılandırma denetimi                 | **5 geçti**                                                                 |
| PostgreSQL yedek ve geçici veritabanına geri yükleme | **6/6 geçti**                                                               |
| Compose yapılandırması / kabuk betikleri             | Geçti                                                                       |
| Caddy'nin kendi yapılandırma doğrulaması             | Geçti                                                                       |
| Frontend Dockerfile ile imaj üretimi                 | Geçti; imaj yerel kaldı                                                     |
| Dışarıya kapalı yollar                               | Uygulama ve katalog 200; `/api/v1/internal/…` ve `/actuator/prometheus` 404 |

Java sayısı bağlam gerektirmeyen birim/sözleşme testlerini ve gerçek FFmpeg kontrollerini kapsar. `*ApplicationTests` seçilmedi. Bunun yerine uygulamalar ayrıca başlatıldı; gateway üzerinden gerçek PostgreSQL, Kafka ve yerel SMTP yakalayıcısıyla HTTP/tarayıcı akışları doğrulandı. Rastgele adlandırılmış geliştirme hesapları, kurslar ve dosyalar oluşturuldu.

Video yükleme, FFmpeg ile H.264/AAC HLS dönüştürmesi, Chrome'da gerçek görüntü çözme, WebVTT altyazısı, izleme ilerlemesi, özel ödev dosyası erişimi, test süresi/hakkı, taslak onayı, ders taşıma, parola sıfırlama e-postası, TOTP, refresh tekrar kullanımının reddi ve yönetici kapsamları kontrol edildi. İstanbul saat diliminde test son tarihinin 30 saniye ileride kaldığı ayrıca test edildi.

Depolama testlerinde **yerel, AWS imza doğrulaması yapmayan bir S3 test sunucusu** kullanıldı. MinIO'nun Docker Hub ve Quay imajlarına bu ortamda erişim reddedildi. Bu sonuç gerçek MinIO kurulumu, S3 imza uyumluluğu, ClamAV imza güncellemesi veya harici S3 yedeğinin doğrulandığı anlamına gelmez.

Frontend imajının bağımlılık kurulumu bu bulutun proxy adı ve CA sertifikasıyla yapıldı; TLS veya paket bütünlüğü doğrulaması kapatılmadı. Son Caddy yapılandırması aday imaja eklendi ve tarayıcı kontrolünde kullanıldı. Konteyner testi yerel HTTP ile yapıldı; gerçek alan adında sertifika verme/yenileme ve kamuya açık HTTPS ayrıca doğrulanmalıdır.

Backend uygulama Docker imajlarının tamamı burada derlenmedi. Yedi Spring modülü Java 25 ile derlenip test edildi; üretim Dockerfile ve Compose yapısı incelemeye hazırdır. Gerçek sunucu dağıtımı, SMTP teslimi, MinIO/ClamAV ve harici yedekten medya dönüşü için [dağıtım rehberini](../deploy/README.md) uygulayın. Ödeme sağlayıcısında gerçek işlem yapılmadı.

Geçici S3 test sunucusu ve Caddy test konteyneri doğrulama sonunda durduruldu. Mevcut geliştirme ortamında frontend, gateway, kullanıcı, kurs, kayıt, ödeme ve bildirim servisleri erişilebilir durumda bırakıldı. Gerçek MinIO bulunmadığından içerik servisi kapalıdır; video yükleme/oynatmayı yeniden kullanmak için önce gerçek depolamayı yapılandırıp içerik servisini başlatın. Geliştirme veritabanında doğrulama için oluşturulan örnek hesaplar ve kurslar bulunur.

Hesap silme özelliği hesabı kapatan ve yönetici incelemesine başvuru oluşturan bir süreçtir. Fiziksel veri silme/anonimleştirme kurumun saklama prosedürüyle yürütülür; bu işlem otomatik tamamlanmış sayılmaz. Gerçek kullanıcı araştırması veya pilot gerçekleştirilmedi; [pilot planı](PILOT.md) hazırlandı.
