# Veri saklama ve silme işletimi

Bu belge teknik işletim prosedürüdür. Kurumun saklama yükümlülükleri, aydınlatma metni ve kullanıcı başvuru süreleri yayından önce belirlenmelidir.

1. Kullanıcı **Güvenlik ve tercihler** ekranından verisini indirebilir veya parolasıyla hesap silme talebi açabilir. Talep hesabı `INACTIVE` yapar ve bütün oturumlarını kapatır.
2. Tam yetkili yönetici veya destek kapsamındaki yönetici talepleri inceler. Başvurucunun kimliğini kurum prosedürüyle doğrular. Kurs sahipliği, sertifika ve varsa mevcut finansal kayıtlar için saklama gerekçesini değerlendirir.
3. Saklanması gerekmeyen profil, telefon, avatar, kişisel not, yer imi, çalışma planı, ödev dosyası, destek yazışması ve bildirim verileri için silme/anonimleştirme işlemini ilgili servis ve S3 kaynağında yürütür. Kullanıcının ders ve kurslarını başkalarının öğrenimini bozacak şekilde zincirleme silmez. Tutulması gereken kayıtları mümkün olan ölçüde kimlikten ayırır.
4. Hangi kaynağın silindiğini, hangi kaydın hangi gerekçeyle tutulduğunu, işlem tarihini ve sorumluyu talebin gerekçesine yazar. Yönetim ekranındaki **İncelemeyi kaydet** yalnızca bu kaydı tutar; veri silme komutu çalıştırmaz.
5. Şifreli yedekler için ayrı saklama süresi ve erişim sınırı uygular. Silme tamamlandıktan sonra eski yedek geri yüklenirse silme talepleri listesini yeniden uygulamadan sistemi kullanıcılara açmaz.

Servis dağılımı: profil/oturum/MFA/destek `user-service`; not/plan/ilerleme/sertifika `enrollment-service`; soru/yanıt/ödev/test/yorum `course-service`; yüklenmiş dosyalar `content-service` ve S3; gelen kutusu/teslim kayıtları `notification-service`. Mevcut ödeme kayıtlarını kendi saklama/iade prosedüründen değerlendirin.

Uygulama süresi dolmuş oturumları ek yedi günlük sürenin ardından; giriş ve bağlantı talebi sınır kayıtlarını iki gün sonra temizler. Diğer kayıtlar için kuruma özgü saklama sürelerini belirlemeden otomatik toplu silme açılmamıştır. Yedeklere, MFA şifreleme anahtarına ve üretim veritabanına erişimi sınırlandırın; talepleri log veya e-posta içinde token/parola paylaşarak işlemeyin.
