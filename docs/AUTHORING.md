# Eğitmen içerik araçları

## Metin hazırlama

Kurs açıklaması ve ders/ödev metninde kalın, italik, başlık, liste, alıntı, kod ve HTTPS bağlantısı için düğmeler vardır. Metni seçip biçim uygulayın; **Metni ön izle** ile öğrenciye nasıl görüneceğini kontrol edin. İçerik mevcut kurslarla uyumlu Markdown biçiminde saklanır. Ön izleme ham HTML çalıştırmaz.

## Ders kopyalama

Müfredatta **Dersi kopyala** düğmesi aynı bölüme bağımsız bir ders ekler. Metin, test ayarları, kaynaklar ve aynı kursun videosu korunur; ders ve test sorusu kimlikleri yeniden üretilir. Kopya herkese açık ön izlemeye kendiliğinden açılmaz. Öğrenci yanıtları, ödev teslimleri ve ilerlemeler kopyalanmaz. Değişikliği **Taslağı kaydet** ile saklayıp mevcut inceleme sürecinden geçirin.

## Kurs şablonları

Eğitmen stüdyosunda **Şablondan kurs oluştur** ile temel eğitim, uygulamalı atölye veya test/değerlendirme yapısını seçin. Başlık ve kategori verildiğinde yeni, ücretsiz bir kurs ve çalışma taslağı oluşur. Örnek metinleri ve soruları kendi içeriğinizle değiştirin.

Kaydedilmiş çalışma taslağını **Şablon olarak kaydet** ile kişisel listenize ekleyebilirsiniz. Kişisel şablonlar diğer hesaplara görünmez; en fazla 25 tane saklanabilir. Şablon sonraki kurs düzenlemelerinden bağımsız bir anlık kopyadır. Şablonu silmek oluşturulmuş kursları değiştirmez.

Yeni kursta bölüm, ders ve soru kimlikleri yenidir. Üyeler, öğrenciler, ilerlemeler, sertifikalar ve değerlendirme geçmişi taşınmaz. Videolar, kapak görseli ve korunan kaynak dosyaları yeni kursa yeniden yüklenir; harici HTTPS kaynak bağlantıları korunur. Şablondan oluşan taslak kendiliğinden yayımlanmaz.

## Ortak eğitmenler

Kurs sahibi **Kurs ekibi** bölümünden kayıtlı, e-posta adresini doğrulamış aktif eğitmeni e-posta adresiyle davet eder. Davet edilen kişi stüdyosundaki daveti kabul edene kadar kursa erişemez. Bir kursta bekleyenler dahil en fazla 20 ortak eğitmen olabilir.

Kabul eden eğitmen kursu kendi stüdyosunda görür; taslak metinlerini, müfredatı, soru bankasını ve kaynakları düzenleyebilir. Kursun ortak medya kitaplığını kullanıp bu kursa video/dosya yükleyebilir. Aynı anda düzenlemede eski taslak sürümünü kaydetmeye çalışan kişi 409 hatası alır; diğerinin değişikliği ezilmez.

Yayın başvurusu, arşivleme, ekip yönetimi, öğrenci listeleri, notlandırma ve öğrenci raporları kurs sahibinde kalır. Yetkili yönetici mevcut inceleme görevlerini sürdürür. Sınırlı destek/moderasyon yöneticileri ekip veya kişisel şablon yönetemez.

Kurs sahibi üyeliği kaldırabilir; ortak eğitmen ekipten ayrılabilir. Sonraki istekten itibaren düzenleme izni kaldırılır. Ortak hesabın güncel eğitmen rolü ve aktif/doğrulanmış durumu da kontrol edilir. Daha önce kursta kullanılan ortak yüklemeler öğrencilerin erişimi bozulmasın diye kurs sahibi tarafından kullanılmaya devam edebilir.

## API

Yollar `/api/v1/courses` altındadır:

| İşlem                  | Yol                                                         |
| ---------------------- | ----------------------------------------------------------- |
| Ekip / davet           | `GET /{id}/team`, `POST /{id}/team` (`email`)               |
| Davetlerim             | `GET /invitations`                                          |
| Davet yanıtı           | `POST /{id}/team/reply` (`accept`)                          |
| Üye kaldırma / ayrılma | `DELETE /{id}/team/{user}`                                  |
| Şablonlarım            | `GET /templates`                                            |
| Taslaktan şablon       | `POST /{id}/templates` (`name`, `revision`)                 |
| Şablondan kurs         | `POST /templates/{template}/create` (`title`, `categoryId`) |
| Kişisel şablon silme   | `DELETE /templates/{template}`                              |

Ortak medya kitaplığı: `GET /api/v1/media/my?courseId={id}`. Üyelik ve kurs erişimi sunucuda doğrulanır.
