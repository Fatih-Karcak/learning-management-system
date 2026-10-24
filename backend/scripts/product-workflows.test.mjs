import { decodeMailhogMessage } from './mailhog.mjs';
import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomUUID, createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";

// Run only against development services. Creates uniquely named accounts, courses and files.
describe(
  "Product learning, media and organization workflows",
  {
    skip: process.env.RUN_LMS_E2E !== "1",
    concurrency: false,
  },
  () => {
    const base = (
      process.env.LMS_E2E_API_URL || "http://127.0.0.1:8080/api/v1"
    ).replace(/\/$/, "");
    let admin,
      teacher,
      outsider,
      student,
      teammate,
      course,
      prerequisite,
      enrollment,
      media,
      workspace,
      group,
      organization,
      path;
    let directory;
    const ids = {
      section: randomUUID(),
      article: randomUUID(),
      video: randomUUID(),
      quiz: randomUUID(),
      assignment: randomUUID(),
      single: randomUUID(),
      multiple: randomUUID(),
      criterion: randomUUID(),
    };
    const credentials = new Map();
    async function request(
      method,
      endpoint,
      actor,
      data,
      statuses = [200, 201],
    ) {
      const response = await fetch(base + endpoint, {
        method,
        headers: {
          ...(actor ? { Authorization: `Bearer ${actor.accessToken}` } : {}),
          ...(data !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: data !== undefined ? JSON.stringify(data) : undefined,
      });
      const payload = await response.json().catch(() => null);
      assert.ok(
        statuses.includes(response.status),
        `${method} ${endpoint.split("?")[0]} returned ${response.status}: ${payload?.message || ""}`,
      );
      if (response.ok) assert.equal(payload?.success, true);
      return payload?.data;
    }
    async function raw(endpoint, actor, options = {}) {
      return fetch(
        new URL(
          endpoint.startsWith("/api/") ? endpoint : base + endpoint,
          base,
        ),
        {
          ...options,
          headers: {
            ...(actor ? { Authorization: `Bearer ${actor.accessToken}` } : {}),
            ...options.headers,
          },
        },
      );
    }
    async function register(role, firstName = "Product") {
      const email = `product+${randomUUID()}@example.test`,
        password = `Test-${randomUUID()}!`;
      const actor = await request("POST", "/auth/register", null, {
        email,
        password,
        firstName,
        lastName: role,
        role,
      });
      credentials.set(actor.user.id, password);
      let token;
      for (let attempt = 0; attempt < 120 && !token; attempt++) {
        const response = await fetch(
          (process.env.LMS_E2E_MAILHOG_URL || "http://127.0.0.1:8025") +
            "/api/v2/messages?limit=100",
        );
        const messages = await response.json();
        for (const message of messages.items || []) {
          if (
            !message.Content?.Headers?.To?.some((value) =>
              value.includes(email),
            )
          )
            continue;
          const content = decodeMailhogMessage(message);
          const match = content.match(/verify-email\?token=([^"\s<]+)/);
          if (match)
            token = decodeURIComponent(match[1].replace(/&amp;.*/, ""));
        }
        if (!token) await new Promise((resolve) => setTimeout(resolve, 500));
      }
      assert.ok(token, "Verification email must reach the local SMTP catcher");
      await request(
        "GET",
        `/auth/verify-email?token=${encodeURIComponent(token)}`,
      );
      return request("POST", "/auth/login", null, { email, password });
    }
    async function createCourse(title) {
      const categories = await request("GET", "/courses/categories");
      const created = await request("POST", "/courses", teacher, {
        title: `${title} ${randomUUID()}`,
        description: "A complete product verification course.",
        shortDescription: "Application workflow verification",
        price: 0,
        categoryId: categories[0].id,
        level: "BEGINNER",
        requirements: ["No prior knowledge required"],
        outcomes: ["Apply the learning objectives"],
      });
      assert.equal(created.language, "English", "New courses must default to English");
      return created;
    }
    async function publish(target, document) {
      let draft = await request(
        "GET",
        `/courses/${target.id}/workspace`,
        teacher,
      );
      draft = await request("PUT", `/courses/${target.id}/workspace`, teacher, {
        revision: draft.revision,
        document,
      });
      draft = await request(
        "POST",
        `/courses/${target.id}/workspace/submit?revision=${draft.revision}`,
        teacher,
      );
      return request(
        "POST",
        `/courses/admin/${target.id}/workspace/decision?revision=${draft.revision}`,
        admin,
        { action: "APPROVE" },
      );
    }
    async function oneArticleCourse(title) {
      const target = await createCourse(title),
        draft = await request(
          "GET",
          `/courses/${target.id}/workspace`,
          teacher,
        );
      const section = randomUUID(),
        lesson = randomUUID();
      draft.document.course.sections = [
        {
          id: section,
          title: "Foundation",
          orderIndex: 0,
          lessons: [
            {
              id: lesson,
              title: "Foundation lesson",
              type: "ARTICLE",
              content: "Read this foundation before moving forward.",
              orderIndex: 0,
              previewAllowed: false,
            },
          ],
        },
      ];
      await publish(target, draft.document);
      return { ...target, section, lesson };
    }
    before(async () => {
      assert.ok(
        process.env.LMS_E2E_ADMIN_EMAIL && process.env.LMS_E2E_ADMIN_PASSWORD,
        "Development administrator credentials are required",
      );
      admin = await request("POST", "/auth/login", null, {
        email: process.env.LMS_E2E_ADMIN_EMAIL,
        password: process.env.LMS_E2E_ADMIN_PASSWORD,
      });
      teacher = await register("INSTRUCTOR");
      outsider = await register("INSTRUCTOR");
      student = await register("STUDENT");
      teammate = await register("STUDENT", "=CSV");
      directory = mkdtempSync(join(tmpdir(), "lms-product-video-"));
      course = await createCourse("Product workflow");
      prerequisite = await oneArticleCourse("Foundation course");
    });
    after(() => {
      if (directory) rmSync(directory, { recursive: true, force: true });
    });

    it(
      "resumes checksummed chunks, rejects other owners and publishes an adaptive video",
      { timeout: 60000 },
      async () => {
        const source = join(directory, "adaptive.mp4");
        execFileSync("ffmpeg", [
          "-hide_banner",
          "-loglevel",
          "error",
          "-y",
          "-f",
          "lavfi",
          "-i",
          "testsrc2=size=1280x720:rate=24",
          "-t",
          "3",
          "-an",
          "-c:v",
          "mpeg4",
          source,
        ]);
        // Valid MP4 plus trailing padding forces a real multi-part compose.
        const bytes = Buffer.concat([readFileSync(source), Buffer.alloc(8 * 1024 * 1024)]);
        const upload = await request("POST", "/media/uploads", teacher, {
          fileName: "adaptive.mp4",
          contentType: "video/mp4",
          totalBytes: bytes.length,
          courseId: course.id,
        });
        await request(
          "GET",
          `/media/uploads/${upload.id}`,
          outsider,
          undefined,
          [404],
        );
        await request(
          "POST",
          `/media/uploads/${upload.id}/complete`,
          teacher,
          undefined,
          [409],
        );
        const firstChunk = bytes.subarray(0, upload.chunkBytes);
        const bad = await raw(`/media/uploads/${upload.id}/parts/0`, teacher, {
          method: "PUT",
          headers: {
            "Content-Type": "application/octet-stream",
            "Upload-Checksum": "0".repeat(64),
          },
          body: firstChunk,
        });
        assert.equal(bad.status, 400);
        for (let offset = 0; offset < bytes.length; offset += upload.chunkBytes) {
          const chunk = bytes.subarray(offset, Math.min(offset + upload.chunkBytes, bytes.length));
          const checksum = createHash("sha256").update(chunk).digest("hex");
          for (let retry = 0; retry < 2; retry++) {
            const response = await raw(`/media/uploads/${upload.id}/parts/${Math.floor(offset / upload.chunkBytes)}`, teacher, {
              method: "PUT", headers: {"Content-Type": "application/octet-stream", "Upload-Checksum": checksum}, body: chunk,
            });
            assert.equal(response.status, 200);
            assert.equal((await response.json()).data.receivedBytes, offset + chunk.length);
          }
          const resumed = await request("GET", `/media/uploads/${upload.id}`, teacher);
          assert.equal(resumed.receivedBytes, offset + chunk.length);
        }
        media = await request(
          "POST",
          `/media/uploads/${upload.id}/complete`,
          teacher,
        );
        assert.equal(
          (
            await request(
              "POST",
              `/media/uploads/${upload.id}/complete`,
              teacher,
            )
          ).id,
          media.id,
        );
        for (let attempt = 0; attempt < 40; attempt++) {
          media = await request("GET", `/media/${media.id}`, teacher);
          assert.notEqual(
            media.status,
            "FAILED",
            "Video processing must succeed",
          );
          if (media.status === "READY") break;
          await new Promise((resolve) => setTimeout(resolve, 500));
        }
        assert.equal(media.status, "READY");
        const response = await raw(media.streamUrl, teacher);
        assert.equal(response.status, 200);
        assert.match(
          await response.text(),
          /quality-360\.m3u8[\s\S]*quality-720\.m3u8/,
        );
      },
    );

    it("saves weighted questions and rubric policies inside the approved draft", async () => {
      workspace = await request(
        "GET",
        `/courses/${course.id}/workspace`,
        teacher,
      );
      workspace.document.course.sections = [
        {
          id: ids.section,
          title: "Learning sequence",
          orderIndex: 0,
          lessons: [
            {
              id: ids.article,
              title: "Read first",
              type: "ARTICLE",
              content: "Read the full introduction.",
              previewAllowed: false,
              orderIndex: 0,
            },
            {
              id: ids.video,
              title: "Adaptive video",
              type: "VIDEO",
              videoUrl: media.streamUrl,
              durationSeconds: media.durationSeconds,
              previewAllowed: false,
              orderIndex: 1,
            },
            {
              id: ids.quiz,
              title: "Weighted assessment",
              type: "QUIZ",
              previewAllowed: false,
              orderIndex: 2,
            },
            {
              id: ids.assignment,
              title: "Practical work",
              type: "ASSIGNMENT",
              content: "Explain your solution and submit your work.",
              previewAllowed: false,
              orderIndex: 3,
            },
          ],
        },
      ];
      workspace.document.quizzes[ids.quiz] = {
        passingScore: 70,
        maxAttempts: 5,
        timeLimitSeconds: 120,
        questions: [
          {
            id: ids.single,
            prompt: "Choose A",
            options: ["A", "B"],
            correctOption: 0,
            correctOptions: [0],
            questionType: "SINGLE_CHOICE",
            weight: 1,
          },
          {
            id: ids.multiple,
            prompt: "Choose A and C",
            options: ["A", "B", "C"],
            correctOption: 0,
            correctOptions: [0, 2],
            questionType: "MULTIPLE_CHOICE",
            weight: 3,
          },
        ],
      };
      workspace.document.assignments = {
        [ids.assignment]: {
          revision: 0,
          dueAt: new Date(Date.now() + 86400000).toISOString(),
          allowLate: false,
          rubric: [{ id: ids.criterion, label: "Accuracy", maxPoints: 80 }],
        },
      };
      await publish(course, workspace.document);
      const rules = await request(
        "PUT",
        `/courses/${course.id}/learning-rules`,
        teacher,
        {
          revision: 0,
          sequential: true,
          releaseDays: 0,
          prerequisites: [prerequisite.id],
        },
      );
      assert.equal(rules.revision, 1);
      await request(
        "PUT",
        `/courses/${prerequisite.id}/learning-rules`,
        teacher,
        {
          revision: 0,
          sequential: true,
          releaseDays: 0,
          prerequisites: [course.id],
        },
        [400],
      );
      enrollment = await request(
        "POST",
        `/enrollments/free/${course.id}`,
        student,
      );
    });

    it("enforces prerequisites and lesson order on content, progress, quizzes and HLS", async () => {
      let visible = await request("GET", `/courses/${course.id}`, student);
      assert.equal(visible.sections[0].lessons[0].locked, true);
      assert.equal(visible.sections[0].lessons[0].content, null);
      await request(
        "PUT",
        `/progress/${enrollment.id}/lesson/${ids.article}`,
        student,
        { sectionId: ids.section, completed: true },
        [403],
      );
      assert.equal((await raw(media.streamUrl, student)).status, 403);
      const foundation = await request(
        "POST",
        `/enrollments/free/${prerequisite.id}`,
        student,
      );
      await request(
        "PUT",
        `/progress/${foundation.id}/lesson/${prerequisite.lesson}`,
        student,
        { sectionId: prerequisite.section, completed: true },
      );
      visible = await request("GET", `/courses/${course.id}`, student);
      assert.equal(visible.sections[0].lessons[0].locked, false);
      assert.equal(visible.sections[0].lessons[1].locked, true);
      await request(
        "GET",
        `/courses/${course.id}/lessons/${ids.quiz}/quiz`,
        student,
        undefined,
        [403],
      );
      await request(
        "PUT",
        `/progress/${enrollment.id}/lesson/${ids.article}`,
        student,
        { sectionId: ids.section, completed: true },
      );
      assert.equal((await raw(media.streamUrl, student)).status, 200);
      assert.equal(
        (await raw(`/media/${media.id}/quality-360.m3u8`, student)).status,
        200,
      );
      assert.equal(
        (await raw(`/media/${media.id}/quality-720-00000.ts`, student)).status,
        200,
      );
      assert.equal(
        (await raw(`/media/${media.id}/quality-720-00000.ts`, teammate)).status,
        403,
      );
      assert.equal((await raw(media.streamUrl)).status, 403);
      await request(
        "POST",
        `/progress/${enrollment.id}/lesson/${ids.video}/playback`,
        student,
        { sectionId: ids.section, positionSeconds: 0, playing: true },
      );
      await new Promise((resolve) => setTimeout(resolve, 1100));
      await request(
        "POST",
        `/progress/${enrollment.id}/lesson/${ids.video}/playback`,
        student,
        { sectionId: ids.section, positionSeconds: 1, playing: true },
      );
      await new Promise((resolve) => setTimeout(resolve, 1100));
      await request(
        "POST",
        `/progress/${enrollment.id}/lesson/${ids.video}/playback`,
        student,
        { sectionId: ids.section, positionSeconds: 2, playing: false },
      );
    });

    it("grades multiple answers and question weights on the server without exposing answer keys", async () => {
      const endpoint = `/courses/${course.id}/lessons/${ids.quiz}/quiz`;
      const active = await request("POST", endpoint + "/start", student, {
        enrollmentId: enrollment.id,
      });
      for (const question of active.quiz.questions) {
        assert.equal(question.correctOptions, undefined);
        assert.equal(question.correctOption, undefined);
      }
      const result = await request("POST", endpoint + "/attempts", student, {
        enrollmentId: enrollment.id,
        sessionId: active.id,
        answers: [
          { questionId: ids.single, options: [0] },
          { questionId: ids.multiple, options: [0] },
        ],
      });
      assert.equal(result.score, 25);
      assert.equal(result.passed, false);
      const retry = await request("POST", endpoint + "/start", student, {
        enrollmentId: enrollment.id,
      });
      const passed = await request("POST", endpoint + "/attempts", student, {
        enrollmentId: enrollment.id,
        sessionId: retry.id,
        answers: [
          { questionId: ids.single, options: [0] },
          { questionId: ids.multiple, options: [0, 2] },
        ],
      });
      assert.equal(passed.score, 100);
      assert.equal(passed.passed, true);
    });

    it("calculates rubric grades from awarded points and preserves the grading snapshot", async () => {
      const endpoint = `/courses/${course.id}/lessons/${ids.assignment}/assignment`;
      const policy = await request("GET", endpoint + "/policy", student);
      assert.equal(policy.rubric[0].maxPoints, 80);
      const submission = await request("POST", endpoint, student, {
        enrollmentId: enrollment.id,
        content: "A complete explanation of the solution.",
      });
      assert.equal(submission.rubric[0].maxPoints, 80);
      const revised = await request("GET", `/courses/${course.id}/workspace`, teacher);
      revised.document.assignments[ids.assignment].rubric[0].maxPoints = 40;
      await publish(course, revised.document);
      assert.equal((await request("GET", endpoint + "/policy", student)).rubric[0].maxPoints, 40);
      await request(
        "PUT",
        endpoint + `/submissions/${submission.id}`,
        outsider,
        {
          status: "ACCEPTED",
          score: 100,
          criterionScores: { [ids.criterion]: 80 },
        },
        [403],
      );
      await request(
        "PUT",
        endpoint + `/submissions/${submission.id}`,
        teacher,
        {
          status: "ACCEPTED",
          score: 100,
          criterionScores: { [ids.criterion]: 81 },
        },
        [400],
      );
      const graded = await request(
        "PUT",
        endpoint + `/submissions/${submission.id}`,
        teacher,
        {
          status: "ACCEPTED",
          score: 100,
          criterionScores: { [ids.criterion]: 60 },
          feedback: "Clear work with room for improvement.",
        },
      );
      assert.equal(graded.score, 75);
      assert.equal(graded.rubric[0].maxPoints, 80);
      assert.equal(graded.criterionScores[ids.criterion], 60);
      assert.equal(
        (await request("GET", `/enrollments/${enrollment.id}`, student)).status,
        "COMPLETED",
      );
    });

    it("creates learning paths with real completion results and prevents unrelated edits", async () => {
      const created = await request("POST", "/courses/paths", teacher, {
        revision: 0,
        title: "Complete product learning path",
        description: "A foundation followed by practical learning.",
        published: true,
        courses: [prerequisite.id, course.id],
      });
      path = created.path.id;
      const detail = await request("GET", `/courses/paths/${path}`, student);
      assert.ok(detail.steps.every((step) => step.completed));
      await request(
        "PUT",
        `/courses/paths/${path}`,
        outsider,
        {
          revision: 1,
          title: "Unauthorized edit",
          description: "",
          published: true,
          courses: [course.id],
        },
        [403],
      );
    });

    it("keeps group reporting private until an invitation is accepted and isolates organization owners", async () => {
      organization = await request(
        "POST",
        "/enrollments/organizations",
        teacher,
        { name: "Product verification organization" },
      );
      group = await request(
        "POST",
        `/enrollments/organizations/${organization}/groups`,
        teacher,
        { name: "Pilot learners" },
      );
      await request(
        "POST",
        `/enrollments/organizations/groups/${group}/members`,
        teacher,
        { emails: [teammate.user.email] },
      );
      await request(
        "POST",
        `/enrollments/organizations/groups/${group}/courses`,
        teacher,
        {
          courseId: prerequisite.id,
          dueAt: new Date(Date.now() - 86400000).toISOString(),
        },
      );
      assert.equal(
        (
          await request(
            "GET",
            `/enrollments/organizations/groups/${group}/report`,
            teacher,
          )
        ).total,
        0,
      );
      await request(
        "GET",
        `/enrollments/organizations/groups/${group}/report`,
        outsider,
        undefined,
        [403],
      );
      assert.equal(
        await request(
          "GET",
          `/enrollments/course/${prerequisite.id}/check`,
          teammate,
        ),
        false,
      );
      await request(
        "POST",
        `/enrollments/organizations/groups/${group}/reply`,
        teammate,
        { accept: true },
      );
      const report = await request(
        "GET",
        `/enrollments/organizations/groups/${group}/report`,
        teacher,
      );
      assert.equal(report.total, 1);
      assert.equal(report.content[0].overdue, true);
      const response = await raw(
        `/enrollments/organizations/groups/${group}/report.csv`,
        teacher,
      );
      assert.equal(response.status, 200);
      assert.match(await response.text(), /"'=CSV STUDENT"/);
      await request(
        "DELETE",
        `/enrollments/organizations/groups/${group}/members/${teammate.user.id}`,
        teacher,
      );
      assert.equal(
        (
          await request(
            "GET",
            `/enrollments/organizations/groups/${group}/report`,
            teacher,
          )
        ).total,
        0,
      );
      assert.equal(
        await request(
          "GET",
          `/enrollments/course/${prerequisite.id}/check`,
          teammate,
        ),
        true,
      );
    });

    it("saves opt-in reminder settings and rejects invalid time zones", async () => {
      assert.equal(
        (await request("GET", "/enrollments/learning/reminders", student))
          .enabled,
        false,
      );
      await request(
        "PUT",
        "/enrollments/learning/reminders",
        student,
        { enabled: true, timeZone: "invalid/timezone", hour: 18 },
        [400],
      );
      const result = await request(
        "PUT",
        "/enrollments/learning/reminders",
        student,
        { enabled: true, timeZone: "Europe/Istanbul", hour: 18 },
      );
      assert.equal(result.timeZone, "Europe/Istanbul");
      assert.equal(result.enabled, true);
    });

    it("publishes durable course covers and searches later media pages", async () => {
      const form = new FormData();
      form.set(
        "file",
        new Blob(
          [
            Buffer.from(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
              "base64",
            ),
          ],
          { type: "image/png" },
        ),
        "product-cover.png",
      );
      form.set("courseId", course.id);
      const response = await raw("/media/image", teacher, {
        method: "POST",
        body: form,
      });
      assert.equal(response.status, 201);
      const image = (await response.json()).data;
      assert.equal((await raw(image.publicUrl)).status, 404);
      const draft = await request(
        "GET",
        `/courses/${course.id}/workspace`,
        teacher,
      );
      draft.document.course.thumbnailUrl = image.publicUrl;
      await publish(course, draft.document);
      const cover = await raw(image.publicUrl);
      assert.equal(cover.status, 200);
      assert.equal(cover.headers.get("content-type"), "image/png");
      await request("DELETE", `/media/${image.id}`, teacher, undefined, [409]);
      const found = await request(
        "GET",
        `/media/my?courseId=${course.id}&type=VIDEO&query=adaptive&page=0&size=1`,
        teacher,
      );
      assert.equal(found.totalElements, 1);
      assert.equal(found.content[0].id, media.id);
      const next = await request(
        "GET",
        `/media/my?courseId=${course.id}&type=VIDEO&query=adaptive&page=1&size=1`,
        teacher,
      );
      assert.equal(next.content.length, 0);
      await request(
        "GET",
        `/media/my?courseId=${course.id}&query=adaptive`,
        outsider,
        undefined,
        [403],
      );
    });

    after(() => {
      if (process.env.LMS_PRODUCT_FIXTURE_PATH && teacher && student && media)
        writeFileSync(
          process.env.LMS_PRODUCT_FIXTURE_PATH,
          JSON.stringify({
            teacher,
            student,
            outsider,
            teammate,
            course,
            prerequisite,
            enrollment,
            media,
            ids,
            path,
            organization,
            group,
          }),
          { mode: 0o600 },
        );
    });
  },
);
