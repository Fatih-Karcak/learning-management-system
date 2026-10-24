# Application verification record — 24 October 2026

The application upgrade was checked with Java 25, Node.js, existing development PostgreSQL/Kafka/MinIO/Mailhog services and Chrome for Testing with H.264 support. Docker and deployment configuration were unchanged.

| Check                           | Result                                                                                                                                               |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java tests across seven modules | 172 passed, no failures or skips, including Spring context tests and four real FFmpeg checks.                                                        |
| Frontend unit tests             | 17 passed.                                                                                                                                           |
| Environment/mail helper tests   | 7 passed.                                                                                                                                            |
| Real API workflow suites        | 48 passed; the 27 platform/product scenarios also passed after the final English-default migration.                                                  |
| Complete Playwright suite       | 46 passed, including real service and decoded video scenarios; none skipped.                                                                         |
| Empty-schema migrations         | All six service schemas migrated and validated in disposable databases. English category seeds and course language defaults were checked explicitly. |
| Builds and formatting           | Seven backend modules packaged; strict TypeScript/Vite build and frontend formatting checks passed.                                                  |
| Running application             | All seven service health endpoints returned UP after verification.                                                                                   |
| Source review                   | No matching credentials found by the changed-source credential-pattern scan; no staged changes or Docker/deployment edits.                           |

## Behaviors exercised

- Certificate PDF text extraction preserved accented learner names and a long title on one page.
- Draft navigation, idle autosave, browser recovery and protection of newer server revisions worked. Concurrent saves and accepted co-instructor access were checked against real services.
- A valid video larger than one upload chunk was uploaded to real MinIO, resumed from confirmed offsets, retried idempotently, composed and processed into multiple HLS qualities. Incorrect checksums, wrong owners and incomplete completion were rejected.
- Chrome decoded actual HLS frames, advanced playback and selected 720p. Private manifests, variants, segments and progress endpoints enforced enrollment/learning rules; explicitly enabled previews stayed public.
- Prerequisites, cycle rejection, sequential access and learning-path completion used actual enrollment data. Release timing and grandfathering also have isolated regression coverage. A curriculum now loads its enrollment snapshot once rather than once per lesson.
- Multiple answer grading returned weighted results without exposing answer keys. A submitted 80-point rubric stayed in effect after the published rubric changed to 40 points; awarding 60 original points still produced 75%.
- Group invitation acceptance controlled reporting access. Tests covered CSV import/export, unauthorized owners, overdue assignments and membership removal without losing the learner's enrollment.
- Reminder preference validation used real APIs; time-zone/DST calculations and duplicate daily scheduling were checked by isolated tests.
- Published cover references stayed accessible through stable URLs, private covers remained protected, media search/pagination worked, and a failed cover could recover after replacement.

Fresh-schema verification establishes the application's database installation path; it is not a clean operating-system or infrastructure installation test. Target-host deployment, representative load, external mail delivery and recovery exercises remain separate release work. The feature limits and product rules are described in [product behavior](PRODUCT_FEATURES.md); commands are in [application testing](APPLICATION_TESTING.md).
