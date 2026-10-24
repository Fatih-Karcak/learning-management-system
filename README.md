# Learning Management System

An English-language learning platform for students, instructors and administrators. The application uses **React 19 + TypeScript** in `frontend/` and **Java 25 + Spring Boot** services in `backend/`.

Students can enroll in free courses, watch protected videos, complete assessments and receive verifiable certificates. Instructors prepare versioned course drafts; administrators review the version that will become visible to learners. Existing payment integrations remain optional for free learning workflows.

## Application capabilities

| Area             | Features                                                                                                                                                                                               |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Learning         | Course search and filters, free enrollment, wishlist, private notes and bookmarks, study plans, progress and resume position, discussions, reviews and announcements.                                  |
| Learning paths   | Ordered collections of courses with real completion results; prerequisite courses, sequential lessons and scheduled lesson release.                                                                    |
| Video            | Checksummed resumable uploads, seven-day resume window, progress and pause/cancel controls, private HLS playback, automatic quality and manual selection where multiple renditions exist.              |
| Assessments      | Server-graded single/multiple answer questions, weighted scores, timed/randomized attempts, assignments with deadlines, late submission rules, grading rubrics and preserved submission/grade history. |
| Authoring        | Rich text/Markdown, lesson duplication, reusable templates, co-instructor invitations, searchable paginated media selection and durable course covers.                                                 |
| Draft protection | Idle autosave, save/discard navigation dialog, browser recovery and revision conflicts that prevent overwriting another author's changes.                                                              |
| Certificates     | Embedded Unicode fonts, PDF download and public certificate verification.                                                                                                                              |
| Organizations    | Owner-managed training groups, verified learner invitations with explicit acceptance, bulk email/CSV import, free course assignments, due dates, progress reports and CSV export.                      |
| Reminders        | Opt-in daily reminders in the learner's time zone; unique daily delivery records and the transactional notification outbox.                                                                            |
| Administration   | Publication review, scoped admin permissions, user/category/enrollment management, support, moderation and audit history.                                                                              |
| Accounts         | Email verification, password recovery, rotating sessions, optional TOTP, notification preferences, personal-data export and account closure requests.                                                  |

Account closure disables the account and opens an administrative review. Physical deletion and anonymization follow the existing [privacy operations procedure](docs/PRIVACY_OPERATIONS.md).

## How a course reaches learners

1. An instructor creates a course or starts from a template, then adds lessons, media, assessments and resources.
2. Changes are saved in the workspace draft. Autosave does not publish content.
3. The owner submits the saved revision for review. An administrator approves it or requests changes with a reason.
4. Approval publishes that revision, including its assessment policies. Existing learners continue using the approved course while a new draft is edited.
5. Learners enroll and complete the requirements. Progress is calculated by the backend; video completion requires elapsed playback evidence, quiz completion requires a passing grade, and assignments require acceptance.

Co-instructors can edit the draft and shared media. Publication, team management, grading and learner reporting stay with the owner. Public lesson previews remain available when explicitly enabled by the instructor.

## Repository layout

```text
frontend/       React application and browser/unit tests
backend/        Spring services, Maven parent, migrations and API tests
docs/           Application rules and verification guides
deploy/         Existing deployment resources
.github/        Existing CI workflows
```

Spring and React remain separate projects. There is no root Maven project. This application upgrade does not change Docker or deployment configuration.

## Development setup

Use Node.js **22.12+**, Java **25**, and FFmpeg/ffprobe with **H.264/libx264 and AAC** support. The backend also needs PostgreSQL, Kafka, S3-compatible storage and development SMTP. Configure existing infrastructure before starting the services; this README does not provision it.

From the repository root:

```bash
node backend/scripts/setup-local-env.mjs
cd backend
sh user-service/mvnw -B package
```

The environment generator creates ignored local settings and does not overwrite an existing file. Load the settings into each service's process environment. Build JARs do not automatically start the application. Run each required service, for example from `backend/`:

```bash
java --enable-preview -jar user-service/target/user-service-1.0.0.jar
```

| Service              | Default port | Responsibility                                                  |
| -------------------- | ------------ | --------------------------------------------------------------- |
| api-gateway          | 8080         | Public API and authorization boundary                           |
| user-service         | 8081         | Accounts, sessions, support and administration                  |
| course-service       | 8082         | Catalog, authoring, assessments, learning rules and paths       |
| enrollment-service   | 8083         | Enrollment, progress, certificates, reminders and organizations |
| payment-service      | 8084         | Existing optional payment integration                           |
| content-service      | 8085         | Media, resources, resumable upload and transcoding              |
| notification-service | 8086         | Inbox and email delivery                                        |

Start the frontend separately:

```bash
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

Open `http://localhost:5173`. Development API requests go through the Vite proxy to gateway port 8080. See the [backend guide](backend/README.md) and [frontend guide](frontend/README.md) for configuration. Existing deployment instructions remain in [deploy/](deploy/README.md).

Never distribute `.env` files, real credentials, uploads or database dumps with the source. All services must share the same JWT and internal API keys. Browser environment variables are public; keep backend secrets out of `VITE_` settings.

## Verification

```bash
cd frontend
npm test
npm run build
npm run test:e2e
```

Backend verification, isolated schema checks and opt-in real API/browser tests are documented in [application testing](docs/APPLICATION_TESTING.md). The real suites create accounts, courses and uploaded files; run them against a development database. Mock browser tests alone do not establish that video storage, mail, migrations or authorization work together.

The completed checks are recorded in [application verification](docs/APPLICATION_VALIDATION.md).

Read [authoring rules](docs/AUTHORING.md) and [product behavior](docs/PRODUCT_FEATURES.md) before changing publication, learning access or grading. Real hosting, load testing, mail deliverability and disaster recovery must still be validated in the target environment before a public launch.
