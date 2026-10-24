
# Learning Management System

An educational platform where students can enroll in courses and track their learning progress, instructors can create content, and administrators can review and approve published materials. The frontend is built with React 19 + TypeScript, and the backend uses Java 25 + Spring Boot. The frontend and backend are separate projects.

The free course workflow does not require payment. The existing Stripe payment/refund integration in the repository is preserved; the payment service is optional for local staging deployments.

## Features

| User          | Capabilities                                                                                                                                                                                                                                                              |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Student       | Course search and filtering, free enrollment, wishlists, protected video playback, resuming lessons, lesson and course progress tracking, study plans, bookmarks, and timestamped personal notes.                                                                         |
| Student       | Timed and attempt-limited quizzes, assignment submissions with file attachments and revisions, Q&A, reviews/ratings, announcements, notifications, and verifiable PDF certificates for completed courses.                                                                 |
| Instructor    | Creating sections and lessons, text and video lessons, question banks, adding resources and subtitles, media library management, saving drafts, student previews, and submitting courses for administrator review.                                                        |
| Instructor    | Text formatting buttons and Markdown preview, lesson duplication, predefined and personal course templates, and invitation-based collaborative editing with co-instructors. Course owners can also track student progress, quiz results, assignments, and course reports. |
| Administrator | Course approval or requesting revisions with explanations, user/role/status and category management, granting/revoking course enrollment with justification, handling support requests and content reports, reviewing activity history, and monitoring failed jobs.       |
| Account       | Email verification, password reset, viewing/terminating sessions, optional TOTP authentication, notification preferences, personal data export, and account closure requests.                                                                                             |

An account closure request deactivates the account and forwards it for administrator review. Physical data deletion/anonymization is handled according to the [data retention procedure](https://docs/PRIVACY_OPERATIONS.md?utm_source=chatgpt.com).

### How does a course become available to students?

1. The instructor creates an empty course or selects a course template.
2. The instructor adds sections, lessons, text, videos, quizzes, and resources to the working draft. For collaboration, registered and verified instructors can be invited; access is granted once the invitation is accepted.
3. Changes are saved using Save Draft. This action does not modify the content currently published to students.
4. The course owner selects Submit for Review. An administrator previews the content and either approves it or requests revisions with an explanation. The approved version becomes available to students.
5. Students enroll in the free course and complete lessons, quizzes, and assignments. Progress is calculated by the backend, and certificate generation is initiated when the course is completed.

An accepted co-instructor can edit the draft and the course's media library. Submission for publication, team management, student reports, and grading remain the responsibility of the course owner. During concurrent editing, saving an outdated version does not overwrite another person's changes. Details: [Instructor authoring tools](https://docs/AUTHORING.md?utm_source=chatgpt.com).

## Directory Structure and Responsibilities

```
learning-management-system/
├── frontend/                 # React + TypeScript UI, npm dependencies, and browser tests
├── backend/                  # Spring services, shared Maven project, and database migrations
│   ├── pom.xml
│   ├── docker-compose.yml    # Development infrastructure: PostgreSQL, Kafka, MinIO, Mailhog
│   ├── init-db/
│   ├── scripts/              # Environment setup and real API validation scripts
│   ├── api-gateway/
│   ├── user-service/
│   ├── course-service/
│   ├── enrollment-service/
│   ├── payment-service/
│   ├── content-service/
│   └── notification-service/
├── deploy/                   # Local staging/VPS deployment, HTTPS, monitoring, and backups
├── docs/                     # Feature rules, pilot plan, and validation records
└── .github/workflows/        # GitHub Actions builds and automated checks
```

The `deploy/`, `docs/`, and `.github/` directories provide deployment, documentation, and testing support for the application code. Spring code remains in `backend/`, while React code remains in `frontend/`. There is no Maven project at the repository root.

## Local Testing Without Renting a Server

This approach allows you to test the application on your own computer as an isolated Docker project.

Requirements: Docker Engine, Docker Compose 2.24.4+, Node.js 22.12+, internet access for initial builds, and sufficient free disk space. A minimum of 16 GB RAM is recommended to get started. Java and FFmpeg are provided within the application images.

Run the following commands from the repository root:

```
bash deploy/scripts/staging.sh prepare
bash deploy/scripts/staging.sh config
bash deploy/scripts/staging.sh up
bash deploy/scripts/staging.sh status
bash deploy/scripts/staging.sh check
```

- `prepare`: Generates independent random secrets and the initial administrator password in the private `deploy/.env.staging` file. It does not overwrite an existing file.
- `config`: Validates the settings and Docker Compose configuration.
- `up`: Builds the images and starts the services. The initial startup may take longer due to downloads.
- `status`: Displays service statuses. `check`: Verifies application/API accessibility and confirms that internal endpoints are not publicly exposed.

| Address                  | Purpose                                        |
| ------------------------ | ---------------------------------------------- |
| `http://127.0.0.1:18088` | Application                                    |
| `http://127.0.0.1:18025` | Test emails; Mailhog does not send real emails |
| `http://127.0.0.1:19090` | Prometheus metrics                             |
| `http://127.0.0.1:19000` | Address used for MinIO file links              |

The initial administrator account is `admin@academy.localhost`. Retrieve the password from the `APP_BOOTSTRAP_ADMIN_PASSWORD` value in `deploy/.env.staging`. This file is excluded from Git and must not be shared. Further details, including removing the bootstrap administrator variables after the first login, are available in the [local staging guide](https://deploy/STAGING.md?utm_source=chatgpt.com).

This setup uses HTTP and allows local access only. For a publicly accessible deployment, follow the [VPS/HTTPS guide](https://deploy/README.md?utm_source=chatgpt.com).

During the initial startup, source file uploads may temporarily return HTTP 503 while ClamAV downloads its signature database.

```
bash deploy/scripts/staging.sh down
```

This command stops the containers without deleting data volumes. The staging environment runs under the name `lms-staging` with separate data volumes.

## Running the Application for Development

To work on the code, you can start the frontend and Spring services as separate processes. This approach is an alternative to the Docker application deployment described above.

### Backend

Java 25, Docker Compose, Node.js, and FFmpeg/ffprobe are required in the environment where the content service runs. FFmpeg must include H.264/libx264 and AAC support. The Maven wrapper is included in the repository.

```
# From the repository root:
node backend/scripts/setup-local-env.mjs
cd backend
docker compose up -d
sh user-service/mvnw -B -DskipTests package
```

`setup-local-env.mjs` creates a shared `backend/.env` file that is excluded from Git. All services must use the same `JWT_SECRET` and `INTERNAL_API_KEY`. These local settings are not substitutes for production configuration.

Here, `docker compose up -d` starts the infrastructure, while the Maven command generates the JAR files.

Next, start each Spring service in a separate terminal. For example, from the `backend/` directory:

```
java --enable-preview -jar user-service/target/user-service-1.0.0.jar
```

For other services, use the corresponding module's JAR file or run `sh mvnw spring-boot:run` from within the module.

The shared POM provides the preview configuration when running through Maven. Refer to the [backend guide](https://backend/README.md?utm_source=chatgpt.com) for IDE/Windows instructions and environment variables.

| Service              | Development Port | Responsibility                                           |
| -------------------- | ---------------- | -------------------------------------------------------- |
| API gateway          | 8080             | API entry point used by the frontend and access controls |
| user-service         | 8081             | Users, authentication, sessions, and support             |
| course-service       | 8082             | Courses, drafts, teams, quizzes, and assignments         |
| enrollment-service   | 8083             | Course enrollments, learning progress, and certificates  |
| payment-service      | 8084             | Existing payment and refund integration                  |
| content-service      | 8085             | Files, video transcoding, and protected media            |
| notification-service | 8086             | Email and in-app notifications                           |

The frontend sends API requests to the gateway. In production deployments, internal service and database ports are kept within a private network.

### Frontend

Node.js 20.19+ or 22.12+ is required. Using Node.js 22.12+ also ensures compatibility with the deployment utilities.

```
# From the repository root, in another terminal:
cd frontend
npm ci
npm run dev
```

The interface opens at `http://localhost:5173`.

By default, `/api` requests are forwarded to the gateway at `http://127.0.0.1:8080`. This can be changed using `API_TARGET` if needed.

Additional configuration options are available in the [frontend guide](https://frontend/README.md?utm_source=chatgpt.com).

### Why are video processing and MinIO required?

MinIO is an S3-compatible object storage system used to store videos, uploaded files, and certificate PDFs. The database does not store the actual files.

The content service uses FFmpeg to transcode uploaded videos into H.264/AAC HLS format. The student player accesses the videos through authorized manifest and segment requests.

Uploading a file is not the same as publishing a video to students.

Attach the video to a lesson in the corresponding course draft, wait until processing is complete, save the draft, submit it for review, and obtain administrator approval.

If students can only see the title or text, first check the video attachment and the approved course version.

In this development cloud environment, uploading, signed URLs, file persistence after restarts, and student video playback were verified using real MinIO, built from an official pinned source version.

On a new machine, storage must be started separately. If the MinIO image registry is inaccessible, you can use the [build-image-from-source option](https://deploy/STAGING.md?utm_source=chatgpt.com#minio-kay%C4%B1t-deposuna-eri%C5%9Filemiyorsa).

## Testing and Validation Status

Frontend checks:

```
cd frontend
npm test
npm run build
npm run format:check
npx playwright install chrome
PLAYWRIGHT_BROWSER_CHANNEL=chrome E2E_VIDEO=1 npm run test:e2e
```

Video browser scenarios require FFmpeg.

The browser command above runs scenarios using controlled API responses. Real backend scenarios require running services and dedicated fixture files. Details are available in the [testing guide](https://backend/TESTING.md?utm_source=chatgpt.com) and [local staging guide](https://deploy/STAGING.md?utm_source=chatgpt.com).

Java unit/contract checks and real FFmpeg tests:

```
# From the repository root:
cd backend
sh user-service/mvnw -B '-Dtest=*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dlms.ffmpeg.tests=true test
```

This selection excludes `*ApplicationTests`, which initialize the Spring application context.

For a complete `verify` run and real HTTP workflows, the required infrastructure must also be started.

During local validation on October 23, 2026, 140 Java tests, 39 real HTTP scenarios, 12 frontend unit tests, and 37 browser scenarios passed.

Seven of those same real browser scenarios were also verified through Caddy, and six database backups were successfully restored to temporary databases.

These results do not represent GitHub Actions results after a new push.

The complete application Compose deployment and all newly built Docker images were not run together in this environment.

The following items still require separate validation:

- Public server/DNS/HTTPS configuration
- Real SMTP email delivery
- ClamAV updates and scanning
- Restoration from external media backups

A pilot with real users has not yet been conducted.

The scope and limitations are documented in the [validation record](https://docs/VALIDATION.md?utm_source=chatgpt.com).

## Advanced Setup and Guides

| Document                                                                                                                    | Contents                                                                                                |
| --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| [Platform rules](https://docs/PLATFORM.md?utm_source=chatgpt.com)                                                           | Draft/publishing workflows, learning behavior, quizzes/assignments, notifications, and account behavior |
| [Instructor authoring tools](https://docs/AUTHORING.md?utm_source=chatgpt.com)                                              | Text editor, lesson duplication, templates, and co-instructor permissions                               |
| [Local staging](https://deploy/STAGING.md?utm_source=chatgpt.com)                                                           | Isolated testing environment without renting a server and building a MinIO image from source            |
| [VPS deployment](https://deploy/README.md?utm_source=chatgpt.com)                                                           | Domain configuration, HTTPS, SMTP, Docker, monitoring, backups, and updates                             |
| [Backend](https://backend/README.md?utm_source=chatgpt.com) / [Frontend](https://frontend/README.md?utm_source=chatgpt.com) | Detailed configuration for the separate development projects                                            |
| [Backend testing](https://backend/TESTING.md?utm_source=chatgpt.com)                                                        | Requirements for Java and real API checks                                                               |
| [LMS business rules](https://backend/LMS_FEATURES.md?utm_source=chatgpt.com)                                                | Initial administrator, free enrollment, progress tracking, and core APIs                                |
| [Pilot plan](https://docs/PILOT.md?utm_source=chatgpt.com)                                                                  | Real student/instructor scenarios and metrics to monitor                                                |
| [Data operations](https://docs/PRIVACY_OPERATIONS.md?utm_source=chatgpt.com)                                                | Data export, retention, account closure, and administrator review                                       |
| [Validation record](https://docs/VALIDATION.md?utm_source=chatgpt.com)                                                      | Completed checks and deployment steps that have not yet been tested                                     |
