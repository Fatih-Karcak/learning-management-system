# Eğitmen ve öğrenci platformu

Ödeme dışındaki geliştirmeler mevcut Spring servislerinin sorumluluklarına dağıtılmıştır. React/TypeScript arayüzü `frontend/`, Java servisleri `backend/` altındadır.

## Kurs üretimi ve yayın

Eğitmen kursu oluşturur ve çalışma taslağında ders/bölüm ekler, düzenler, sıralar ve taşır. Yeni taslakta henüz yayımlanmamış dersler kaldırılabilir. Sorular, kaynaklar ve altyazılar aynı taslağa bağlıdır. Medya kitaplığında 20 dosyaya kadar sıralı toplu yükleme yapılabilir.

**Taslağı kaydet** öğrencilere görünür içeriği değiştirmez. **İncelemeye gönder** başvuruyu kilitler; düzenlemek için geri çekmek gerekir. Yönetici taslağın tamamını ön izler, onaylar veya gerekçeyle düzeltme ister. Onay tek veritabanı işlemiyle dersleri, kurs bilgilerini, testleri ve kaynakları yayımlar; yayın geçmişi saklanır. Eşzamanlı düzenlemede eski `revision` ile gelen kayıt 409 alır ve diğer değişikliği ezmez.

Ders kimlikleri korunur. Yayımlanmış ders kaldırılamaz veya türü değiştirilemez. Metin, video, kaynak, başlık, sıra ve bölüm değişebilir. Taşınan derste eski ilerleme yeni bölümle eşleştirilir. Eski doğrudan düzenleme API'leri yayımlanmış/incelemedeki kursu değiştiremez.

Daha önce tamamlanmış derslerin tamamlanma kaydı korunur. Kursa yeni ders eklemek kazanılmış sertifikayı ve tamamlanma durumunu geri almaz; tamamlamış öğrencinin eski yüzde/sayıları yeni dersleri de tamamlayana kadar korunur. Devam eden öğrencinin yüzdesi güncel müfredata göre bir sonraki ilerleme kaydında hesaplanır. Yayımlanmış videonun yerine başka video konulduğunda eski ders tamamlamasını yeniden değerlendirme zorunluluğu yoktur; zorunlu yeniden eğitim için yeni kurs oluşturun.

Arşivleme kursu yeni kayıtlara kapatır, mevcut öğrencinin erişimini korur ve bekleyen taslak başvurusunu geri çeker. Yayın geçmişi önceki taslağı otomatik geri yükleyen bir araç değildir; geçmiş onay belgeleri veritabanında tutulur.

## Öğrenme

Öğrenci son çalıştığı tamamlanmamış derse döner. Video konumu, benzersiz izleme kapsamı, metin/test/ödev tamamlaması ayrı kurallarla kaydedilir. Not, zaman damgalı yer imi, haftalık video hedefi ve tarihli çalışma planı vardır. Çalışma süresi UTC günlerine göre doğrulanmış video kapsamından hesaplanır; aynı aralığı yeniden izlemek yeni süre kazandırmaz. Bu sayı toplam ekran süresi değildir.

Yayımlanmış ve **herkese açık ön izlenebilir** işaretlenmiş video kayıt olmadan açılır. Bu izin taslaktayken uygulanmaz. Diğer videolar aktif/tamamlanmış kayıt veya kurs sahibi/yönetici yetkisi ister.

Kaynaklar HTTPS bağlantısı veya korunan PDF/TXT/WebVTT dosyası olabilir. Dosya üst sınırı 25 MB, altyazı sınırı 1 MB'dır. İzin verilen biçimler içerik imzası/UTF-8 kontrolünden geçer; üretimde ClamAV taraması zorunludur. Ödev dosyasını sahibi, kurs eğitmeni ve yetkili yönetici açabilir; aynı sınıftaki başka öğrenci açamaz. Kaynak dosyaları kurs erişimiyle korunur. Altyazı dosyası kimlik doğrulamayla indirilip tarayıcıda geçici blob üzerinden oynatıcıya bağlanır.

## Test ve ödev

Soru bankası, rastgele sıra/seçim, soru sayısı, süre ve deneme sınırı belirlenebilir. Sınır bulunan testin soruları sunucuda **Başlat** işleminde seçilir ve o oturuma sabitlenir. Aynı öğrenci devam eden oturumuna döner. Süre sunucuda kontrol edilir; son tarih UTC bilgisiyle iletilir. Başlatılmış ve süresi bitmiş deneme de hakkı tüketir. Doğru yanıt anahtarı test görünümünde verilmez. Sunucu puanı hesaplar; sonuçta soru bazında doğru/tekrar çalış ve açıklama gösterilir.

Ödev yönergesi ve değerlendirme ölçütleri ders metnindedir. Öğrenci metin, bağlantı veya dosya teslim eder. Eğitmen kabul eder veya geri bildirimle düzeltme ister. Yeniden teslimde önceki metin ve değerlendirme geçmişe alınır. Kabul edilen ödevin tamamlanması geri alınmaz. Ödev kabulü ve test başarısı iç servis çağrısıyla ilerlemeyi günceller.

## Keşif, iletişim ve işletim

Katalog arama, kategori, dil, seviye, ücretsiz kurs ve puan filtrelerini birlikte kullanabilir. İstek listesi ve kategoriye göre ilgili kurs önerileri vardır. Eğitmen duyuruları, soru yanıtları, ödev değerlendirmeleri ve yayın onayı uygulama içi bildirim oluşturur. Öğrenme/duyuru e-postaları kullanıcı tercihlerine uyar; güvenlik e-postaları bağımsızdır.

Yeni bildirimler işlemsel outbox üzerinden gider, alıcı başına olay kimliğiyle tekilleşir. E-posta teslimi saklanır, otomatik yeniden denenir ve başarısız kayıt yönetici tarafından tekrar kuyruğa alınabilir. SMTP gönderildikten hemen sonra süreç kesilirse e-postanın tekrar gönderilmesi mümkündür; SMTP ile tam bir kez teslim garantisi yoktur. Gelen kutusu aynı olay için tek kayıt tutar.

Eğitmen kendi kursunda ders başına başlayan/tamamlayan sayısını, test puanlarını, bekleyen ödevleri, çözülmemiş soruları ve uzun süre çalışmayan öğrenci sayısını görür. Rapor, öğrenme kaydı olan davranışları ölçer; bir öğrencinin dersten neden ayrıldığını tahmin etmez.

Destek talepleri ve içerik şikâyetleri öğrenci/yönetici yazışmasıyla izlenir. Yorumlar gerekçeyle kaldırılabilir. Tam yönetim, kurs moderasyonu ve destek kapsamları gateway'de sınırlandırılır; hassas kullanıcı yönetimi ayrıca kullanıcı servisinde tam yönetici ister. Spring ve veri servislerini gateway dışından internete açmayın.

## Hesap güvenliği ve veriler

- Parolalar BCrypt ile saklanır. E-posta normalize edilir. Başarısız girişler hesap bazında sınırlandırılır.
- Erişim token'ı kayıtlı oturuma bağlıdır. Oturum yenilemede refresh token değişir; eski refresh token'ın tekrar kullanılması o oturumu iptal eder. Kullanıcı açık oturumlarını tek tek veya topluca kapatabilir.
- Rol, durum, kapsam, parola değişikliği ve parola sıfırlama oturumları iptal eder. Eski sürümden kalan oturumsuz token'lar kabul edilmez; güncellemeden sonra tekrar giriş gerekir.
- İsteğe bağlı TOTP, şifreli saklanan anahtar, kullanılmış kod kontrolü ve hatalı kod deneme sınırı içerir. Kurulum anahtarını güvenli saklayın. Cihaz ve anahtar birlikte kaybolursa kimlik doğrulamalı destek prosedürü uygulanmalıdır; e-posta sıfırlama MFA'yı kaldırmaz.
- E-posta doğrulama bağlantısı 24 saat, parola sıfırlama bağlantısı 30 dakika geçerlidir. Sıfırlama tek kullanımlıdır. Kullanıcı bulunup bulunmadığı sıfırlama cevabından öğrenilemez.
- Kişisel veriler dört servisten JSON olarak indirilebilir. Hesap silme talebi hesabı kapatıp oturumları iptal eder; saklama ve silme işlemi yönetici sürecidir. **İncelendi** durumu otomatik fiziksel silme anlamına gelmez. [Veri prosedürünü](PRIVACY_OPERATIONS.md) uygulayın.

## Başlıca yeni API'ler

Yollar `/api/v1` altındadır. Liste/görünüm işlemleri GET, değişiklikler ilgili POST/PUT/DELETE yöntemleriyle yapılır.

| Alan                | Yollar                                                                                       |
| ------------------- | -------------------------------------------------------------------------------------------- |
| Taslak              | `/courses/{id}/workspace`, `…/submit?revision=…`, `…/withdraw`                               |
| İnceleme            | `/courses/admin/workspaces`, `/courses/admin/{id}/workspace/decision?revision=…`             |
| Geçmiş ve kaynak    | `/courses/{id}/versions`, `/courses/{id}/lessons/{lesson}/materials`                         |
| Kaynak yükleme      | `POST /media/resources` (`file`, `courseId`, `purpose=MATERIAL/ASSIGNMENT`)                  |
| Test oturumu        | `POST /courses/{id}/lessons/{lesson}/quiz/start`, ardından `…/attempts` içinde `sessionId`   |
| Çalışma             | `/enrollments/learning/dashboard`, `…/tasks`, `/enrollments/{id}/bookmarks`                  |
| Rapor               | `/enrollments/courses/{id}/analytics`, `/courses/{id}/analytics`                             |
| Keşif               | `/courses/discover`, `/courses/wishlist`, `/courses/{id}/recommendations`                    |
| Duyuru/gelen kutusu | `/courses/{id}/announcements`, `/notifications/inbox`, `…/{id}/read`                         |
| Oturum/MFA          | `/auth/refresh`, `/users/me/sessions`, `/users/me/mfa/setup`, `…/confirm`                    |
| Kurtarma            | `/auth/forgot-password`, `/auth/reset-password`, `/auth/resend-verification`                 |
| Destek/veriler      | `/users/me/tickets`, `…/{id}/replies`, `/users/me/export`, `/users/me/erasure`               |
| İşletim             | `/media/operations`, `…/{id}/retry`, `/notifications/admin/failures`, `/users/admin/privacy` |

[Bulut kurulumu](../deploy/README.md), [pilot planı](PILOT.md), [test yönergeleri](../backend/TESTING.md).
