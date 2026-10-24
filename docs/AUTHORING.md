# Course authoring

The workspace is a revision-controlled draft. Saving, autosaving or recovering a draft does not change published lessons. Only administrator approval promotes the submitted document and its assessment policies.

## Editing safely

- Changes autosave after three seconds of idle time. Saving temporarily locks editing controls.
- Leaving with pending changes opens Save and leave, Discard changes and Keep editing actions. Closing/reloading the tab uses the browser's unsaved-change prompt.
- The same browser tab keeps a recovery draft for up to seven days. Matching revisions can be restored. If the server revision changed, download the local draft for comparison; recovery cannot overwrite the newer server document.
- Server revision checks reject concurrent stale saves. Resolve the conflict before resubmitting.

## Lessons and media

Use Markdown formatting and its sanitized preview for lesson text. Duplicate lessons receive new identities and independently copied assessment definitions. Course templates create new course/section/lesson/question identities; assignment deadlines are cleared so old deadlines are not inherited.

Upload videos through the media library. Interrupted videos resume from confirmed checksummed chunks when the original file and course are selected again. Cancel unfinished uploads to free a quota slot. Select READY videos using the course editor's search and pagination controls, then save and submit the draft.

Published cover images use a durable application URL. Signed legacy image links are resolved through the referenced course cover rather than being relied on after expiration. A bound published cover cannot be deleted from the library.

## Assessments and learning rules

Quiz questions support single or multiple correct options and individual weights. Multiple answer questions award their weight only for the exact correct set. Answer keys are never included in learner question responses.

Assignment deadlines, late submission settings and rubrics are part of the approved workspace. Rubric grades are calculated from awarded criterion points, and each submitted revision retains its grading policy snapshot. Change requests allow resubmission without losing earlier versions.

The owner can configure prerequisite courses, sequential lessons and release intervals in Learning rules. These settings enforce access immediately for applicable enrollments; review the grandfathering rules in [product behavior](PRODUCT_FEATURES.md). Learning paths organize published courses but do not automatically enroll learners.

## Collaboration and review

Owners invite registered, verified instructors. Access begins after the invitation is accepted. Accepted co-instructors edit drafts and shared course media; the owner retains publication, team management, grading and learner reports. Leaving or removing a team member revokes editing access.

The owner submits a saved revision. Reviewers inspect that revision and either approve or request changes with a reason. Public previews are enabled explicitly per lesson and intentionally remain accessible without enrollment. Archiving removes new catalog access while preserving existing learners' access according to the application's archive rules.
