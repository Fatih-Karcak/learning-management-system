# Gerçek kullanıcı pilotu

Hedef: 3–5 eğitmen ve 30–50 öğrenciyle iki haftalık kullanım. Bu bir davet veya yürütülmüş kullanıcı araştırması değildir; hesap/davet listesi ve iletişim izni kurum tarafından sağlanır.

## Yayından önce

- Ayrı test ve üretim ortamı kullanın. Gerçek DNS/TLS, SMTP, MinIO imzalı URL, ClamAV ve harici yedekleme kontrollerini tamamlayın.
- Veritabanı yedeklerini ayrı ortama geri yükleyin. Video, kaynak ve sertifika için S3 yedeğinden geri dönüşü deneyin.
- Bir eğitmen, bir öğrenci ve sınırlı yetkili destek hesabıyla aşağıdaki akışı deneyin. Ödeme bu pilotun kapsamında değildir; ücretsiz kurs kullanın.
- Destek sorumlusu, ulaşılabilir destek kanalı, veri talebi prosedürü ve takip tarihlerini belirleyin.

## Kullanıcı akışları ve kabul ölçütleri

| Kullanıcı       | Senaryo                                                          | Beklenen sonuç                                                                      |
| --------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Eğitmen         | Kurs, bölüm, yazı, video, test, ödev, kaynak ve altyazı hazırlar | Yardım almadan taslak kaydeder ve incelemeye gönderir                               |
| Yönetici        | İçeriği ön izler, düzeltme ister, yeniden gönderileni onaylar    | Onaydan önce katalog/öğrenci eski sürümü görür; onaydan sonra güncel içerik görünür |
| Öğrenci         | Kursu bulur, ücretsiz kaydolur ve videoyu izler                  | Gerçek görüntü/ses açılır; altyazı görünür; izleme ilerlemesi sunucuda kaydedilir   |
| Öğrenci         | Başka cihazda yeniden giriş yapar                                | Son çalıştığı ders ve video konumu geri gelir                                       |
| Öğrenci         | Not, yer imi ve çalışma planı oluşturur                          | Başka öğrenci bu verileri okuyamaz                                                  |
| Öğrenci         | Süreli/sınırlı testi başlatır ve gönderir                        | Süre/hak sunucuda uygulanır; doğru yanıtlar testten önce açığa çıkmaz               |
| Eğitmen/öğrenci | Ödeve düzeltme, yeniden teslim ve kabul yapar                    | Önceki teslim/geri bildirim korunur; kabul ilerlemeye yansır                        |
| Öğrenci         | Bütün zorunlu dersleri tamamlar                                  | Sertifika oluşturulur, indirilir ve kodla doğrulanır                                |
| Eğitmen         | Kursta bölüm sırası değiştirir, dersi taşır, yeni ders ekler     | Onaylı değişiklik görünür; önceki ilerleme ve kazanılmış sertifika korunur          |
| Destek          | Şikâyete yanıt verir ve kapatır                                  | Kullanıcı yazışmayı görür; destek kullanıcısı rol yükseltemez veya kurs onaylayamaz |
| Kullanıcı       | Oturumu iptal eder, parolasını sıfırlar                          | Eski erişim/refresh token'ları reddedilir                                           |

## Ölçümler

Eğitmen raporlarından ders bazında başlayan/tamamlayan sayıları, test ortalamaları, ödev bekleme sayısı ve çözülmemiş soruları izleyin. Destek ekranından sorun türlerini ve tekrar eden engelleri kaydedin. Prometheus üzerinden servis kesintisi ve 5xx oranını takip edin. Yedek kontrol tarihini, video hata sayısını ve başarısız bildirimleri günlük inceleyin.

Önerilen pilot hedefleri: bütün eğitmenlerin yardım almadan kurs gönderebilmesi; öğrencilerin en az %90'ının yardım almadan kayıt ve ilk dersi açabilmesi; izinsiz veri erişimi olmaması; sertifika ve yedekten dönüş senaryolarının tamamlanması. Bunlar ölçülmüş sonuç değildir.

Hafta 1 sonunda kayıt/oynatma engellerini, hafta 2 sonunda ödev/test ve devam etme deneyimini kullanıcılarla değerlendirin. Her bulguya tekrar adımı, beklenen/gerçek sonuç, etkilenen rol, sıklık ve sorumlu ekleyin. Teknik veya erişim engeli çözülmeden daha büyük kullanıcı grubuna geçmeyin.
