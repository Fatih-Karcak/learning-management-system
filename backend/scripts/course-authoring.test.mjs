import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";

describe(
  "Collaborative course authoring and real S3 storage",
  { skip: process.env.RUN_LMS_E2E !== "1", concurrency: false },
  () => {
    const base = (
      process.env.LMS_E2E_API_URL || "http://127.0.0.1:8080/api/v1"
    ).replace(/\/$/, "");
    let owner,
      editor,
      outsider,
      student,
      admin,
      course,
      workspace,
      video,
      resource,
      template,
      copy;
    const quizId = randomUUID(),
      videoId = randomUUID(),
      sectionId = randomUUID();
    let dir;
    const passwords = new Map();
    async function request(method, path, session, data, status = 200) {
      const r = await fetch(base + path, {
        method,
        headers: {
          ...(session
            ? { Authorization: `Bearer ${session.accessToken}` }
            : {}),
          ...(data ? { "Content-Type": "application/json" } : {}),
        },
        body: data ? JSON.stringify(data) : undefined,
      });
      const result = await r.json().catch(() => null);
      assert.ok(
        (Array.isArray(status) ? status : [status]).includes(r.status),
        `${method} ${path.split("?")[0]}: ${r.status} ${result?.message || ""}`,
      );
      return result?.data;
    }
    async function register(role) {
      const password = `Test-${randomUUID()}!`;
      const result = await request(
        "POST",
        "/auth/register",
        null,
        {
          email: `author+${randomUUID()}@example.test`,
          password,
          firstName: "Author",
          lastName: role,
          role,
        },
        201,
      );
      passwords.set(result.user.id, password);
      return result;
    }
    async function verifyEmail(user) {
      let token;
      for (let n = 0; n < 30; n++) {
        const r = await fetch(
          (process.env.LMS_E2E_MAILHOG_URL || "http://127.0.0.1:8025") +
            "/api/v2/messages?limit=100",
        );
        const messages = await r.json();
        for (const m of messages.items || []) {
          if (
            !m.Content?.Headers?.To?.some((to) => to.includes(user.user.email))
          )
            continue;
          const decoded = (m.Content.Body || "")
            .replace(/=\r?\n/g, "")
            .replace(/=([0-9A-F]{2})/gi, (_, hex) =>
              String.fromCharCode(parseInt(hex, 16)),
            );
          const match = decoded.match(/verify-email\?token=([^"\s<]+)/);
          if (match)
            token = decodeURIComponent(match[1].replace(/&amp;.*/, ""));
        }
        if (token) break;
        await new Promise((r) => setTimeout(r, 500));
      }
      assert.ok(
        token,
        "Instructor verification email must arrive at the local SMTP catcher",
      );
      await request(
        "GET",
        `/auth/verify-email?token=${encodeURIComponent(token)}`,
      );
    }
    async function upload(path, session, form) {
      const r = await fetch(base + path, {
        method: "POST",
        headers: { Authorization: `Bearer ${session.accessToken}` },
        body: form,
      });
      const value = await r.json();
      assert.equal(
        r.status,
        path === "/media/resources" ? 200 : 201,
        value.message,
      );
      return value.data;
    }
    before(async () => {
      assert.ok(
        process.env.LMS_E2E_ADMIN_EMAIL && process.env.LMS_E2E_ADMIN_PASSWORD,
        "A development administrator is required",
      );
      admin = await request("POST", "/auth/login", null, {
        email: process.env.LMS_E2E_ADMIN_EMAIL,
        password: process.env.LMS_E2E_ADMIN_PASSWORD,
      });
      owner = await register("INSTRUCTOR");
      editor = await register("INSTRUCTOR");
      outsider = await register("INSTRUCTOR");
      student = await register("STUDENT");
      await verifyEmail(editor);
      const categories = await request("GET", "/courses/categories");
      course = await request(
        "POST",
        "/courses",
        owner,
        {
          title: `Collaborative course ${randomUUID()}`,
          description: "A practical course",
          shortDescription: "Learn through practice",
          price: 0,
          categoryId: categories[0].id,
        },
        201,
      );
      workspace = await request(
        "GET",
        `/courses/${course.id}/workspace`,
        owner,
      );
      workspace.document.course.requirements = ["No prerequisites"];
      workspace.document.course.outcomes = ["Build a project"];
      workspace.document.course.sections = [
        {
          id: sectionId,
          title: "Practice",
          orderIndex: 0,
          lessons: [
            {
              id: quizId,
              title: "Knowledge check",
              type: "QUIZ",
              orderIndex: 0,
              previewAllowed: false,
            },
            {
              id: videoId,
              title: "Video lesson",
              type: "VIDEO",
              orderIndex: 1,
              previewAllowed: false,
            },
          ],
        },
      ];
      workspace.document.quizzes[quizId] = {
        passingScore: 70,
        maxAttempts: 2,
        timeLimitSeconds: 30,
        randomize: true,
        questionCount: 1,
        questions: [
          {
            id: randomUUID(),
            prompt: "Which answer is correct?",
            options: ["A", "B"],
            correctOption: 0,
            explanation: "A is correct",
          },
        ],
      };
      dir = mkdtempSync(join(tmpdir(), "lms-author-video-"));
      execFileSync("ffmpeg", [
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-f",
        "lavfi",
        "-i",
        "testsrc2=size=320x180:rate=24",
        "-t",
        "3",
        "-an",
        "-c:v",
        "mpeg4",
        "-threads",
        "2",
        join(dir, "lesson.mp4"),
      ]);
    });
    after(() => {
      if (process.env.LMS_AUTHORING_FIXTURE_PATH && owner && course) {
        writeFileSync(
          process.env.LMS_AUTHORING_FIXTURE_PATH,
          JSON.stringify({
            owner,
            editor,
            outsider,
            student,
            admin,
            course,
            template,
            copy,
          }),
          { mode: 0o600 },
        );
      }
      if (dir) rmSync(dir, { recursive: true, force: true });
    });

    it("requires invitation acceptance and prevents outsiders from granting course access", async () => {
      await request(
        "GET",
        `/courses/${course.id}/workspace`,
        editor,
        null,
        403,
      );
      await request(
        "POST",
        `/courses/${course.id}/team`,
        outsider,
        { email: editor.user.email },
        403,
      );
      await request(
        "POST",
        `/courses/${course.id}/team`,
        owner,
        { email: student.user.email },
        400,
      );
      const team = await request("POST", `/courses/${course.id}/team`, owner, {
        email: editor.user.email,
      });
      assert.equal(team.members[0].status, "INVITED");
      await request(
        "GET",
        `/courses/${course.id}/workspace`,
        editor,
        null,
        403,
      );
      const invitations = await request("GET", "/courses/invitations", editor);
      assert.ok(invitations.some((x) => x.course_id === course.id));
      await request(
        "POST",
        `/courses/${course.id}/team/reply`,
        outsider,
        { accept: true },
        404,
      );
      await request("POST", `/courses/${course.id}/team/reply`, editor, {
        accept: true,
      });
      await request("GET", `/courses/${course.id}/workspace`, editor);
      const mine = await request("GET", "/courses/mine", editor);
      assert.ok(mine.content.some((x) => x.id === course.id));
    });

    it("allows an accepted editor to upload video and private course material to real MinIO", async () => {
      const form = new FormData();
      form.set("courseId", course.id);
      form.set(
        "file",
        new Blob([readFileSync(join(dir, "lesson.mp4"))], {
          type: "video/mp4",
        }),
        "lesson.mp4",
      );
      video = await upload("/media/video", editor, form);
      for (let i = 0; i < 60 && video.status === "PROCESSING"; i++) {
        await new Promise((r) => setTimeout(r, 250));
        video = await request("GET", `/media/${video.id}`, editor);
      }
      assert.equal(video.status, "READY");
      assert.ok(video.durationSeconds > 0);
      workspace.document.course.sections[0].lessons[1].videoUrl =
        video.streamUrl;
      const material = new FormData();
      material.set("courseId", course.id);
      material.set("purpose", "MATERIAL");
      material.set(
        "file",
        new Blob(["%PDF-1.7\nPilot material\n%%EOF"], {
          type: "application/pdf",
        }),
        "guide.pdf",
      );
      resource = await upload("/media/resources", editor, material);
      workspace.document.materials[videoId] = [
        { title: "Guide", url: resource.url, type: "RESOURCE" },
      ];
      const library = await request(
        "GET",
        `/media/my?courseId=${course.id}&size=100`,
        owner,
      );
      assert.ok(library.content.some((x) => x.id === video.id));
      await request(
        "GET",
        `/media/my?courseId=${course.id}`,
        outsider,
        null,
        403,
      );
    });

    it("verifies S3 signatures instead of accepting unsigned or tampered object downloads", async () => {
      assert.ok(video.thumbnailUrl, "An actual FFmpeg thumbnail is required");
      const signed = new URL(video.thumbnailUrl);
      const valid = await fetch(signed);
      assert.equal(valid.status, 200);
      assert.ok((await valid.arrayBuffer()).byteLength > 0);
      const unsigned = new URL(signed);
      unsigned.search = "";
      assert.equal((await fetch(unsigned)).status, 403);
      const tampered = new URL(signed);
      tampered.searchParams.set("X-Amz-Signature", "0".repeat(64));
      assert.equal((await fetch(tampered)).status, 403);
    });

    it("keeps edits in the shared workspace and rejects stale writers and editor publication requests", async () => {
      workspace = await request(
        "PUT",
        `/courses/${course.id}/workspace`,
        editor,
        { revision: 0, document: workspace.document },
      );
      await request(
        "PUT",
        `/courses/${course.id}/workspace`,
        owner,
        { revision: 0, document: workspace.document },
        409,
      );
      await request(
        "POST",
        `/courses/${course.id}/workspace/submit?revision=${workspace.revision}`,
        editor,
        null,
        403,
      );
      await request("POST", `/courses/${course.id}/archive`, editor, null, 403);
      await request(
        "POST",
        `/courses/${course.id}/team`,
        editor,
        { email: outsider.user.email },
        403,
      );
      await request(
        "GET",
        `/enrollments/courses/${course.id}/students`,
        editor,
        null,
        403,
      );
    });

    it("stops shared draft access when an editor is demoted and checks the current account role", async () => {
      const path = `/users/admin/${editor.user.id}`;
      await request("PUT", path, admin, {
        role: "STUDENT",
        status: "ACTIVE",
        reason: "Development authorization regression",
      });
      const account = await request("POST", "/auth/login", null, {
        email: editor.user.email,
        password: passwords.get(editor.user.id),
      });
      await request("GET", `/courses/${course.id}`, account, null, 404);
      await request(
        "GET",
        `/courses/${course.id}/workspace`,
        account,
        null,
        403,
      );
      await request("PUT", path, admin, {
        role: "INSTRUCTOR",
        status: "ACTIVE",
        reason: "Restore development editor",
      });
      editor = await request("POST", "/auth/login", null, {
        email: editor.user.email,
        password: passwords.get(editor.user.id),
      });
      await request("GET", `/courses/${course.id}/workspace`, editor);
    });

    it("saves private templates and creates an independent draft with fresh quiz and lesson identities", async () => {
      template = await request(
        "POST",
        `/courses/${course.id}/templates`,
        owner,
        { name: "My course structure", revision: workspace.revision },
      );
      assert.ok(
        (await request("GET", "/courses/templates", owner)).some(
          (x) => x.id === template.id,
        ),
      );
      assert.ok(
        !(await request("GET", "/courses/templates", outsider)).some(
          (x) => x.id === template.id,
        ),
      );
      const categoryId = workspace.document.categoryId;
      await request(
        "POST",
        `/courses/templates/${template.id}/create`,
        outsider,
        { title: "Unauthorized course copy", categoryId },
        404,
      );
      copy = await request(
        "POST",
        `/courses/templates/${template.id}/create`,
        owner,
        { title: "Independent training course", categoryId },
      );
      const draft = await request(
        "GET",
        `/courses/${copy.id}/workspace`,
        owner,
      );
      assert.equal(draft.status, "DRAFT");
      assert.equal(draft.revision, 1);
      const lessons = draft.document.course.sections[0].lessons;
      assert.notEqual(lessons[0].id, quizId);
      assert.notEqual(lessons[1].id, videoId);
      assert.equal(lessons[1].videoUrl, null);
      assert.equal(draft.document.materials[lessons[1].id].length, 0);
      const q = draft.document.quizzes[lessons[0].id];
      assert.equal(q.timeLimitSeconds, 30);
      assert.notEqual(
        q.questions[0].id,
        workspace.document.quizzes[quizId].questions[0].id,
      );
      assert.equal(
        (await request("GET", `/courses/${copy.id}/team`, owner)).members
          .length,
        0,
      );
    });

    it("revokes editor access immediately while preserving the course-owned uploaded media for approval", async () => {
      await request(
        "DELETE",
        `/courses/${course.id}/team/${editor.user.id}`,
        owner,
      );
      await request(
        "GET",
        `/courses/${course.id}/workspace`,
        editor,
        null,
        403,
      );
      await request(
        "PUT",
        `/courses/${course.id}/workspace`,
        editor,
        { revision: workspace.revision, document: workspace.document },
        403,
      );
      await request(
        "GET",
        `/media/my?courseId=${course.id}`,
        editor,
        null,
        403,
      );
      workspace = await request(
        "POST",
        `/courses/${course.id}/workspace/submit?revision=${workspace.revision}`,
        owner,
      );
      workspace = await request(
        "POST",
        `/courses/admin/${course.id}/workspace/decision?revision=${workspace.revision}`,
        admin,
        { action: "APPROVE" },
      );
      assert.equal(workspace.status, "PUBLISHED");
      const manifest = await fetch(new URL(video.streamUrl, base), {
        headers: { Authorization: `Bearer ${owner.accessToken}` },
      });
      assert.equal(manifest.status, 200);
      assert.match(await manifest.text(), /#EXTM3U/);
    });

    it("creates all built-in templates and allows only the owner to delete a private template", async () => {
      for (const name of ["starter", "workshop", "assessment"]) {
        const created = await request(
          "POST",
          `/courses/templates/${name}/create`,
          owner,
          {
            title: `Built-in ${name} learning course`,
            categoryId: workspace.document.categoryId,
          },
        );
        assert.ok(
          (await request("GET", `/courses/${created.id}/workspace`, owner))
            .document.course.sections.length > 0,
        );
      }
      await request(
        "DELETE",
        `/courses/templates/${template.id}`,
        outsider,
        null,
        404,
      );
      await request("DELETE", `/courses/templates/${template.id}`, owner);
      await request("GET", "/courses/templates", student, null, 403);
    });
  },
);
