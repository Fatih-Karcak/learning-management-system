# LMS backend

Seven Java 25 / Spring Boot services behind the API gateway. Each service owns its schema and Flyway migrations. The React application lives separately in [frontend/](../frontend/).

## Local application setup

Install Java 25 and FFmpeg/ffprobe with H.264/libx264 and AAC. Provision PostgreSQL, Kafka, S3-compatible storage and development SMTP using your existing environment. Docker and deployment resources are unchanged by this application work.

From the repository root:

```bash
node backend/scripts/setup-local-env.mjs
cd backend
sh user-service/mvnw -B package
```

The setup script creates ignored development environment files without overwriting existing files. Load `backend/.env` into the environment of every service (or configure your IDE). In PowerShell, set variables in the process environment before starting Java. The Spring services require the same `JWT_SECRET` and `INTERNAL_API_KEY`; database and storage credentials must match the available infrastructure.

Run each service JAR from `backend/`, for example:

```bash
java --enable-preview -jar course-service/target/course-service-1.0.0.jar
```

Alternatively run the module's Maven wrapper with `spring-boot:run`. Ports and service responsibilities are listed in the [root README](../README.md). Internal service URLs must resolve from the service process, including course, enrollment, content and user lookups.

Set `APP_FRONTEND_BASE_URL` for browser-facing email links. Use an explicitly configured bootstrap administrator for initial review workflows; remove bootstrap credentials from the process environment after initialization. Never commit local environment files or include them in source distributions.

## Media and certificates

Content uploads require real S3-compatible storage. A successful upload initially creates a processing video. The processor generates H.264/AAC HLS; larger sources receive a 360p/720p/1080p ladder capped at the source size. Small sources retain one rendition. Upload sessions have bounded quotas and seven-day expiration, and stale parts are cleaned by a scheduled job.

Set `VIDEO_FFMPEG` and `VIDEO_FFPROBE` to executable paths when they are not on PATH. Windows examples:

```properties
VIDEO_FFMPEG=C:/ffmpeg/bin/ffmpeg.exe
VIDEO_FFPROBE=C:/ffmpeg/bin/ffprobe.exe
```

Attach ready media to a lesson in a workspace and publish the approved revision. Course membership, learning rules and preview settings protect HLS manifests, variants and segments. Durable cover endpoints serve only a referenced published cover; draft library images remain private.

Certificate PDF generation embeds the bundled DejaVu Sans fonts; their license is included beside the fonts. This preserves Turkish and other supported Unicode characters in learner names and titles without depending on fonts installed on the host.

## Tests

```bash
sh user-service/mvnw -B verify
node --test scripts/setup-local-env.test.mjs scripts/mailhog.test.mjs
```

Some Spring context tests require configured development dependencies. The [application testing guide](../docs/APPLICATION_TESTING.md) describes fresh-schema checks and real API/browser suites, including opt-in flags and fixture handling. See [product behavior](../docs/PRODUCT_FEATURES.md) for learning-rule, grading and organization boundaries.
