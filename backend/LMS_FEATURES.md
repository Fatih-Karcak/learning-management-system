# Ödeme dışındaki LMS özellikleri

Öğrenci, eğitmen ve yönetici iş akışları backend yetki ve kayıt kontrolleriyle uygulanır. Mevcut ödeme/Stripe/iade akışı bu değişiklikte yeniden tasarlanmadı.

## İş akışları

| Alan             | Davranış                                                                                                                                                                                                                                                                                        |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Yönetici         | Kullanıcı arama; rol ve aktif/pasif/yasaklı durumunu değiştirme; gerekçeli işlem kaydı; kullanıcı/kurs/kayıt istatistikleri. Yönetici kendi rolünü veya durumunu değiştiremez. Rol değişiminden önce verilmiş token sonraki gateway isteğinde reddedilir. E-posta doğrulaması yasağı kaldırmaz. |
| Kurs onayı       | Eğitmen **İncelemeye gönder** ile `PENDING_REVIEW` oluşturur. Yönetici onaylarsa `PUBLISHED`, gerekçeli düzeltme isterse `DRAFT` olur. Hazır olmayan videolar, soruları tanımlanmamış testler ve boş metin/ödev içerikleri incelemeye gönderilemez.                                             |
| Kategoriler      | Yönetici kategori oluşturur/düzenler/siler. Kurs veya alt kategori içeren kategori silinemez.                                                                                                                                                                                                   |
| Ücretsiz kayıt   | Yayındaki ücretsiz kursa doğrudan kayıt; aynı öğrenci/kurs için eşzamanlı istekler tek kayıt ve tek olay üretir. Ücretli kurs için bu yol kullanılamaz. Yönetici gerekçeyle ayrıca kayıt verebilir. Ödemeli kayıtların erişimi mevcut iade akışıyla yönetilir.                                  |
| Arşiv            | Eğitmen veya yönetici kursu yeni kayıtlara kapatabilir. Mevcut aktif/tamamlanmış kayıtların ders erişimi korunur.                                                                                                                                                                               |
| Video ilerlemesi | Beş saniyelik oynatma bildirimleri, duraklatma ve ileri/geri alma olayları kaydedilir. Sunucu zaman sınırı ve izlenen aralıkların birleşimini kullanır; son saniyeye atlamak veya aynı aralığı tekrar izlemek fazladan tamamlama kredisi vermez. En fazla 2× hız desteklenir.                   |
| Tamamlama        | Video yaklaşık %90 benzersiz izleme kapsamıyla (tam saniye süreleri için en fazla 0,9 sn tolerans), makale **Okudum**, test sunucuda hesaplanan geçme puanıyla, ödev eğitmen kabulüyle tamamlanır. Kayıt yüzdesi ders listesinden hesaplanır.                                                   |
| Test             | Eğitmen soru, 2–8 seçenek ve doğru yanıt belirler; geçme puanı 1–100 arasıdır. Öğrenciye yanıt anahtarı gönderilmez. Denemeler ve puanlar saklanır, öğrenci tekrar deneyebilir. İstemcinin gönderdiği bir puan tamamlama kaynağı değildir.                                                      |
| Ödev             | Öğrenci metin ve isteğe bağlı çalışma bağlantısı teslim eder. Eğitmen puan/geri bildirim verir, düzeltme ister veya kabul eder. Düzeltilen ödev yeniden gönderilebilir; kabul edilen teslim tekrar gönderilemez veya tamamlanması geri alınamaz.                                                |
| Eğitmen takibi   | Eğitmen kendi kursunun öğrencilerini, kayıt durumlarını, tamamlanan ders sayılarını, yüzdelerini ve son erişim zamanlarını görür. Diğer eğitmenler bu listeye erişemez.                                                                                                                         |
| Soru-cevap       | Kayıtlı öğrenciler/eğitmen kurs veya ders bazlı soru sorar, yanıtlar ve çözülen yanıtı işaretler. Arama ve yanıtsız filtreleri vardır. Soru sahibi/eğitmen/yönetici soruyu kaldırabilir.                                                                                                        |
| Ders notları     | Öğrencinin kayıt/derse bağlı kişisel notları; video zamanına not ekleme, düzenleme, silme ve zaman damgasına gitme. Başka öğrencinin notları okunamaz.                                                                                                                                          |
| Sertifika        | Tüm dersleri tamamlanan kayıt `COMPLETED` olur. Mevcut zamanlanmış sertifika üretimi PDF oluşturur; öğrenci indirir ve doğrulama kodu herkese açık sorgulanır. Başka öğrenci PDF bağlantısını alamaz. Gerçek PDF saklama için MinIO/S3 gereklidir.                                              |

## İlk yönetici

Herkese açık kayıt formu `ADMIN` oluşturmaz. İlk yönetici için yalnızca kullanıcı servisini çalıştırdığınız güvenli ortamda veya Git tarafından yok sayılan `backend/.env` içinde şu iki değişkeni sağlayın:

```properties
APP_BOOTSTRAP_ADMIN_EMAIL=yonetici@kurumunuz.example
APP_BOOTSTRAP_ADMIN_PASSWORD=<en az 12 karakterlik benzersiz ve güçlü parola>
```

Kullanıcı servisi ilk başlangıçta hesabı oluşturur. Aynı e-posta zaten yönetici ise parolası veya durumu değiştirilmez; e-posta farklı roldeki bir hesaba aitse işlem açık hata verir. İlk girişten sonra bu başlangıç değişkenlerini kaldırın. Parolayı kaynak koda, `VITE_` değişkenlerine veya komut çıktısına koymayın. Sonraki yöneticiler yönetici panelinden atanabilir.

## Başlıca API'ler

Tüm yollar `/api/v1` altındadır; normal işlemlerde `Authorization: Bearer …` gerekir.

| API                                                                           | Amaç                                                             |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `GET /users/admin/summary`, `PUT /users/admin/{id}`, `GET /users/admin/audit` | Yönetici istatistik, rol/durum ve gerekçeli işlem geçmişi.       |
| `GET /courses/admin`, `POST /courses/admin/{id}/decision`                     | Tüm kurslar ve `APPROVE` / `REJECT` / `ARCHIVE` kararı.          |
| `POST /courses/admin/categories`, `PUT/DELETE /courses/admin/categories/{id}` | Kategori yönetimi.                                               |
| `POST /courses/{id}/publish`, `POST /courses/{id}/archive`                    | Sahibi tarafından incelemeye gönderme ve arşivleme.              |
| `POST /enrollments/free/{courseId}`                                           | Ödeme oluşturmadan ücretsiz kayıt.                               |
| `POST /enrollments/admin/grant`, `POST /enrollments/admin/{id}/revoke`        | Gerekçeli yönetici kaydı ve ödeme içermeyen kaydı iptal etme.    |
| `GET /enrollments/summary`, `GET /enrollments/admin/summary`                  | Kişisel ve yönetici kayıt istatistikleri.                        |
| `GET /enrollments/courses/{courseId}/students`                                | Yetkili eğitmen/yönetici öğrenci ve ilerleme listesi.            |
| `POST /progress/{enrollmentId}/lesson/{lessonId}/playback`                    | `sectionId`, `positionSeconds`, `playing` ile oynatma bildirimi. |
| `GET/POST /progress/{enrollmentId}/notes`, `PUT/DELETE …/notes/{id}`          | Kişisel zaman damgalı notlar; isteğe bağlı `lessonId` filtresi.  |
| `GET /courses/{courseId}/lessons/{lessonId}/quiz`                             | Kayıtlı öğrenci için yanıt anahtarı içermeyen test.              |
| `GET/PUT …/quiz/manage`, `GET/POST …/quiz/attempts`                           | Eğitmen test hazırlama; öğrenci denemeleri.                      |
| `GET …/assignment/my`, `POST …/assignment`                                    | Öğrenci ödev durumu ve teslimi.                                  |
| `GET …/assignment/submissions`, `PUT …/assignment/submissions/{id}`           | Eğitmen teslim listesi ve değerlendirme.                         |
| `GET/POST /courses/{courseId}/questions`, `GET/POST …/{id}/replies`           | Kurs/ders soru-cevap.                                            |
| `PUT …/{questionId}/accepted/{replyId}`, `DELETE …/{questionId}`              | Çözüm seçme ve yetkili silme.                                    |

Test ve ödev işlemleri `enrollmentId` ister; sunucu öğrenci/kurs eşleşmesini iç kayıt servisinden doğrular. Başarıyla geçen test ve kabul edilen ödev, iç ilerleme endpoint'ini ortak servis anahtarıyla çağırır. Gateway `/internal/**` yollarını dışarıya açmaz. Kurs ve içerik iç metadata uçları da aynı anahtarı ister.

## Güncelleme ve doğrulama

Yeni Flyway dosyaları mevcut verileri silmeden uygulanır. Kullanıcı, kurs, kayıt, içerik ve gateway servislerini güncel JAR'larla yeniden başlatın; arayüzü yeniden derleyin/yenileyin. Eski `publish` çağrısının anında yayın yapması artık beklenmez; yönetici onayı gerekir.

```bash
# backend/ dizininden, mevcut Java 25/Maven ortamınızla:
sh user-service/mvnw -B -Dtest=AdminUserTest,AccountRegressionTest,CourseRegressionTest,AssessmentTest,PlaybackProgressTest,FreeEnrollmentPolicyTest,LessonVideoTest,MediaPlaybackTest,InternalMediaAccessTest,LearningRateLimitTest,GatewaySecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Çalışan geliştirme servisleriyle gerçek API entegrasyon testi:

```bash
# Yalnızca geliştirme ortamında; örnek öğrenci/eğitmen/kurs kayıtları oluşturur.
# LMS_E2E_ADMIN_EMAIL ve LMS_E2E_ADMIN_PASSWORD değerlerini güvenli ortamdan verin.
RUN_LMS_E2E=1 node --test scripts/lms-features.test.mjs
```

Bu 13 senaryo gerçek gateway/kullanıcı/kurs/kayıt servislerini, veritabanı işlemlerini, sertifika üretimini ve yetki retlerini sınar. `LMS_E2E_API_URL` varsayılan olarak `http://127.0.0.1:8080/api/v1` olur. Sertifika senaryosu erişilebilir nesne deposu ve çalışan sertifika zamanlayıcısı ister. Bu bulut doğrulamasında S3 test sunucusu kullanıldı; gerçek MinIO dağıtımı veya gerçek Stripe işlemi doğrulanmış sayılmaz.
