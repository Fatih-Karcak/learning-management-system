# Learning Management System

Uygulama iki bağımsız proje olarak düzenlenmiştir:

```text
learning-management-system/
├── frontend/                 # React + TypeScript + Vite
│   ├── src/
│   └── package.json
└── backend/                  # Java 25 + Spring Boot + Maven
    ├── pom.xml               # Yedi servisin ortak Maven projesi
    ├── docker-compose.yml    # PostgreSQL, Kafka, MinIO ve SMTP
    ├── init-db/
    ├── api-gateway/
    ├── user-service/
    ├── course-service/
    ├── enrollment-service/
    ├── payment-service/
    ├── content-service/
    └── notification-service/
```

Frontend'in Node bağımlılıkları ve derlemesi `frontend/` içinde; Spring servisleri, Maven dosyaları, veritabanı betikleri ve altyapı yapılandırması `backend/` içindedir. Depo kökünde Maven projesi bulunmaz.

Ödeme dışındaki LMS işlevleri: yönetici paneli ve kurs onayı, kullanıcı/kategori yönetimi, ücretsiz kayıt, video ilerlemesi ve kaldığın yerden devam etme, puanlanan testler, ödev teslimi/değerlendirme, eğitmen öğrenci takibi, soru-cevap, kişisel ders notları ve sertifika üretimi. İş kuralları, ilk yönetici kurulumu ve API listesi: [LMS özellikleri](backend/LMS_FEATURES.md).

## Frontend

Node.js 20.19+ veya 22.12+ ile:

```bash
cd frontend
npm ci
npm run dev
```

Arayüz 5173 portunda çalışır. `/api` istekleri varsayılan olarak 8080 portundaki backend gateway'ine yönlendirilir. Ayrıntılar: [Frontend README](frontend/README.md).

## Backend

Java 25, Docker Compose ve servislerin ortak ortam değişkenleri gerekir:

```bash
node backend/scripts/setup-local-env.mjs
cd backend
docker compose up -d
sh user-service/mvnw -B -DskipTests package
```

Servisleri ilgili modülün klasöründen Maven wrapper ile veya üretilen JAR dosyasını `java --enable-preview` ile başlatın. Ortam değişkenleri ve başlangıç adımları: [Backend README](backend/README.md). Test akışı: [Backend testleri](backend/TESTING.md).

## Gerçek kullanım ve bulut

- [Platform iş akışları ve kurallar](docs/PLATFORM.md)
- [VPS / Docker / HTTPS dağıtımı](deploy/README.md)
- [Pilot kullanıcı planı](docs/PILOT.md)
- [Veri saklama ve silme işletimi](docs/PRIVACY_OPERATIONS.md)

- [Kontrol sonuçları ve doğrulanmamış ortam adımları](docs/VALIDATION.md)
