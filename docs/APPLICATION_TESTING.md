# Application verification

Run these checks against development dependencies. This workflow does not build containers, change deployment settings or prove that a new host is ready for production.

## Build and isolated checks

Use Java 25 and the checked-in Maven wrappers. FFmpeg/ffprobe must support H.264/libx264 and AAC.

```bash
cd backend
sh user-service/mvnw -B -Dlms.ffmpeg.tests=true verify
node --test scripts/setup-local-env.test.mjs scripts/mailhog.test.mjs
```

Spring context tests need configured development infrastructure. To run only named regression classes without those context tests:

```bash
sh user-service/mvnw -B '-Dtest=*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dlms.ffmpeg.tests=true test
```

The video processor tests decode generated segments, verify variant dimensions and cover malformed media. Certificate tests extract PDF text to verify accented names. Learning tests cover access ordering, prerequisite failures, time zones, rubric grading and owner isolation. These isolated tests do not substitute for real storage or API checks.

Frontend:

```bash
cd frontend
npm ci
npm test
npm run build
npm run format:check
npm run test:e2e
```

Mocked browser tests cover authoring, unsaved navigation, autosave, recovery, stale revisions and existing application flows. The tests marked as real service workflows skip unless explicitly enabled.

## Fresh application schemas

After building the backend, configure a **development** PostgreSQL account that can create/drop temporary databases:

```bash
node backend/scripts/verify-application-schemas.mjs
```

The script reads ignored `backend/.env` settings (or `LMS_ENV_FILE`), with process environment taking precedence. It applies and validates every migration for all six service databases in newly created uniquely named temporary databases, then drops those databases. The existing application databases are not reset. This checks application migrations on empty schemas; it does not provision a clean operating system or infrastructure.

## Real API suites

Start the gateway and all relevant services with PostgreSQL, Kafka, real MinIO/S3 and Mailhog. Supply a development full administrator through `LMS_E2E_ADMIN_EMAIL` and `LMS_E2E_ADMIN_PASSWORD` using your secure environment. Do not put its password into a committed script or terminal history.

From the repository root:

```bash
RUN_LMS_E2E=1 node --test --test-concurrency=1 \
  backend/scripts/lms-features.test.mjs \
  backend/scripts/platform-workflows.test.mjs \
  backend/scripts/course-authoring.test.mjs \
  backend/scripts/product-workflows.test.mjs
```

Optional settings:

| Variable                     | Purpose                                                    |
| ---------------------------- | ---------------------------------------------------------- |
| `LMS_E2E_API_URL`            | Gateway API base, default `http://127.0.0.1:8080/api/v1`.  |
| `LMS_E2E_MAILHOG_URL`        | Development mail capture, default `http://127.0.0.1:8025`. |
| `LMS_E2E_FIXTURE_PATH`       | Private platform browser fixture.                          |
| `LMS_AUTHORING_FIXTURE_PATH` | Private collaborative authoring browser fixture.           |
| `LMS_PRODUCT_FIXTURE_PATH`   | Private adaptive video/learning path browser fixture.      |

These suites create uniquely named users, courses, media, notifications and enrollment records in the development environment. Fixtures are written with private permissions and contain authentication tokens. Keep them outside the repository and delete them afterward. Mailhog helpers respect MIME transfer encoding rather than corrupting tokens that happen to contain hexadecimal text.

The product suite exercises real multi-part uploads, checksums, retry/completion idempotency, adaptive processing, unauthorized HLS access, prerequisites, sequential lessons, multiple answer scoring, rubric grades, path progress, invitation consent, report isolation, CSV export, reminders and durable covers.

## Real browser checks

Provide the private fixture paths generated above, set `E2E_REAL_BACKEND=1` and point `E2E_BASE_URL` at the running frontend. Set `E2E_VIDEO=1` to include codec-dependent video cases. Use Chrome with H.264 support, such as `PLAYWRIGHT_BROWSER_CHANNEL=chrome`, or an explicit `PLAYWRIGHT_CHROMIUM_PATH`. A Chromium build without proprietary codec support cannot verify actual video playback.

```bash
cd frontend
E2E_REAL_BACKEND=1 E2E_VIDEO=1 npm run test:e2e
```

The real adaptive player test requires decoded frames, advancing playback and a selectable 720p rendition; a visible video element alone is insufficient. Real browser traces and screenshots can contain session or personal data. Delete their generated output before distributing source code.

Before a public launch, separately validate the intended hosting environment, representative concurrent load, mail delivery, storage durability, backup restoration and incident handling. No result from this application test workflow certifies those operational properties.
