# Product behavior and limits

The application interface, validation messages, notifications and certificate text are English. New courses and unchanged built-in categories default to English. Learner names, existing course languages and authored content retain their original values and Unicode characters.

## Drafts and media

The workspace document contains lessons, quizzes, materials and assignment policies. A saved draft stays separate from the approved version. Automatic saving uses the same optimistic revision checks as manual saving. A browser recovery draft is scoped to the signed-in user and course, kept in session storage for seven days, and cannot overwrite a newer server revision.

Video library selection supports file-name search, READY/VIDEO filtering and server pagination. Published covers use a stable application route, including resolution of legacy signed image links. Unreferenced and draft covers are not made public by that route.

Uploads use 8 MiB sequential chunks, SHA-256 verification and idempotent retries. The maximum file size is 2 GiB, with five open sessions per owner and a seven-day resume window. Select the same file and course on the original browser to resume. The backend rejects changed chunks, wrong offsets, incomplete completion and other owners' session access. Cancellation releases an open slot; scheduled cleanup removes expired parts. Closing the browser pauses transfer; it does not run a background upload.

New larger videos receive H.264/AAC HLS renditions at 360p, 720p and 1080p up to the source resolution. Smaller sources are not upscaled. Existing ready videos are not automatically retranscoded. HLS.js selects quality automatically and exposes manual selection for multiple renditions. Manifests, variants and segments all enforce access. Explicit lesson previews remain public. Protected playback requires a supported browser with H.264 decoding.

## Course access and paths

Owners and full administrators manage learning rules. A course can require completion of up to twenty published prerequisite courses; cyclic dependencies are rejected. Sequential rules require previous lessons to be completed. A release interval makes lesson index N available N × interval days after enrollment.

Rules apply to enrollments created after the current policy's effective time. Earlier enrollments retain access, and completed lessons/courses remain accessible. Editing a policy updates that effective time; review this grandfathering behavior before changing a live course. Preview lessons bypass these locks intentionally.

Gates protect lesson content, resources, HLS, assessments and progress writes. An unavailable enrollment service does not count as successful prerequisite verification. Learning paths are curated ordered lists with progress derived from enrollments. Adding a course to a path does not enroll the learner or itself create a prerequisite.

## Assessment and reporting

Single answer and multiple answer questions have weights from 1 to 100. Multiple answer questions use all-or-nothing grading for the exact correct set. The server calculates the weighted percentage, passing state and completion. Learner responses omit answer keys. Existing single-option request payloads remain supported.

Assignments can have a deadline, an explicit late-submission rule and up to twenty rubric criteria. Rubric percentages are derived from awarded/available points; a client-supplied percentage cannot override them. Missing or out-of-range criterion scores are rejected. Submissions keep their rubric snapshot, lateness marker, previous revisions and grading history. Accepted assignments count toward course completion.

Existing instructor reports cover enrollment, completion, inactivity, lesson engagement and assessment results. Group reports add learner/course status, deadlines, overdue markers and CSV export. Reports are paginated and scoped to the owner or an authorized administrator; CSV cells neutralize spreadsheet formulas.

## Organizations and consent

Instructors and authorized administrators create organizations and training groups. An organization supports up to 100 groups; each group supports up to 500 invited/active learners and 100 assigned courses. Imports accept at most 200 email addresses per request and require existing active, verified accounts. CSV import supports quoted fields and an email column.

An invitation does not enroll the learner or disclose their progress. The learner accepts or declines explicitly. Acceptance enrolls them in the group's assigned published free courses and opens reporting for those assignments. Removing membership ends group reporting but preserves the learner's course enrollment. This is an application-level group feature; it does not provide separate tenant databases, SAML/SCIM, enterprise billing or legal compliance certification.

## Reminders and certificates

Study reminders are disabled by default. Learners choose an IANA time zone and local delivery hour. A daily digest is queued when there are upcoming/overdue tasks or courses inactive for seven days. DST-aware scheduling calculates the next local delivery time. A unique daily delivery record prevents duplicate scheduling, and notification delivery respects account email preferences. The reminder scheduler is not an exact-time delivery guarantee.

PDF certificates embed licensed DejaVu fonts and support their Unicode repertoire. Completion and certificate access remain server-controlled; public verification exposes the certificate's intended verification information.
