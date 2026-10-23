import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomUUID, createHmac } from "node:crypto";
import { writeFileSync, readFileSync, mkdtempSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";
// Creates isolated, uniquely named fixtures. Run only against a development database.
describe(
  "Production learning workflows",
  { skip: process.env.RUN_LMS_E2E !== "1", concurrency: false },
  () => {
    const base = (
      process.env.LMS_E2E_API_URL || "http://127.0.0.1:8080/api/v1"
    ).replace(/\/$/, "");
    let admin,
      teacher,
      student,
      other,
      course,
      workspace,
      enrollment,
      article,
      quiz,
      assignment,
      section,
      second,
      attempt,
      file;
    const passwords = new Map();
    async function req(method, path, session, data, status = [200, 201]) {
      const response = await fetch(base + path, {
        method,
        headers: {
          ...(session
            ? { Authorization: `Bearer ${session.accessToken}` }
            : {}),
          ...(data ? { "Content-Type": "application/json" } : {}),
        },
        body: data ? JSON.stringify(data) : undefined,
      });
      const result = await response.json().catch(() => null);
      assert.ok(
        status.includes(response.status),
        `${method} ${path}: ${response.status} ${result?.message || ""}`,
      );
      if (response.ok) assert.equal(result?.success, true);
      return result?.data;
    }
    async function register(role) {
      const email = `pilot-${randomUUID()}@example.test`,
        password = `Test-${randomUUID()}!`;
      const user = await req("POST", "/auth/register", null, {
        email,
        password,
        firstName: "Pilot",
        lastName: role,
        role,
      });
      passwords.set(user.user.id, password);
      return user;
    }
    async function save() {
      workspace = await req("PUT", `/courses/${course.id}/workspace`, teacher, {
        revision: workspace.revision,
        document: workspace.document,
      });
    }
    async function publish() {
      workspace = await req(
        "POST",
        `/courses/${course.id}/workspace/submit?revision=${workspace.revision}`,
        teacher,
      );
      workspace = await req(
        "POST",
        `/courses/admin/${course.id}/workspace/decision?revision=${workspace.revision}`,
        admin,
        { action: "APPROVE" },
      );
    }
    const lessonPath = () => `/courses/${course.id}/lessons/${quiz}/quiz`;
    before(async () => {
      assert.ok(
        process.env.LMS_E2E_ADMIN_EMAIL && process.env.LMS_E2E_ADMIN_PASSWORD,
        "Development administrator required",
      );
      admin = await req("POST", "/auth/login", null, {
        email: process.env.LMS_E2E_ADMIN_EMAIL,
        password: process.env.LMS_E2E_ADMIN_PASSWORD,
      });
      teacher = await register("INSTRUCTOR");
      student = await register("STUDENT");
      other = await register("STUDENT");
      const categories = await req("GET", "/courses/categories");
      course = await req("POST", "/courses", teacher, {
        title: `Pilot course ${randomUUID()}`,
        description: "A complete learning journey",
        shortDescription: "Pilot learning course",
        price: 0,
        categoryId: categories[0].id,
      });
      workspace = await req("GET", `/courses/${course.id}/workspace`, teacher);
      section = randomUUID();
      second = randomUUID();
      article = randomUUID();
      quiz = randomUUID();
      assignment = randomUUID();
      const question1 = randomUUID(),
        question2 = randomUUID();
      workspace.document.course.targetAudience =
        "Students starting their first project";
      workspace.document.course.level = "BEGINNER";
      workspace.document.course.language = "Türkçe";
      workspace.document.course.outcomes = ["Complete a practical project"];
      workspace.document.course.requirements = ["No prerequisites"];
      workspace.document.course.sections = [
        {
          id: section,
          title: "Foundations",
          orderIndex: 0,
          lessons: [
            {
              id: article,
              title: "Read first",
              type: "ARTICLE",
              content: "Article version one",
              orderIndex: 0,
              previewAllowed: false,
            },
            {
              id: quiz,
              title: "Practice test",
              type: "QUIZ",
              orderIndex: 1,
              previewAllowed: false,
            },
            {
              id: assignment,
              title: "Final project",
              type: "ASSIGNMENT",
              content: "Submit a project. Criteria: accuracy 60%, clarity 40%.",
              orderIndex: 2,
              previewAllowed: false,
            },
          ],
        },
        { id: second, title: "Next steps", orderIndex: 1, lessons: [] },
      ];
      workspace.document.quizzes[quiz] = {
        passingScore: 70,
        maxAttempts: 1,
        timeLimitSeconds: 30,
        questionCount: 1,
        randomize: true,
        questions: [
          {
            id: question1,
            prompt: "Choose the first option",
            options: ["Correct", "Wrong"],
            correctOption: 0,
            explanation: "The first option is correct.",
          },
          {
            id: question2,
            prompt: "Choose the first option again",
            options: ["Correct", "Wrong"],
            correctOption: 0,
            explanation: "Apply the same principle.",
          },
        ],
      };
    });
    it("keeps workspaces private and rejects stale writes", async () => {
      await req("GET", `/courses/${course.id}/workspace`, student, null, [403]);
      await save();
      await req(
        "PUT",
        `/courses/${course.id}/workspace`,
        teacher,
        { revision: workspace.revision - 1, document: workspace.document },
        [409],
      );
      await req("GET", `/courses/${course.id}`, null, null, [403, 404]);
    });
    it("publishes the exact reviewed document and protects old mutation endpoints", async () => {
      await publish();
      const visible = await req("GET", `/courses/${course.id}`);
      assert.equal(visible.title, course.title);
      assert.equal(
        visible.targetAudience,
        "Students starting their first project",
      );
      assert.equal(visible.sections[0].lessons[0].content, null);
      await req(
        "PUT",
        `/courses/${course.id}`,
        teacher,
        { title: "Unauthorized live change" },
        [409],
      );
      await req(
        "POST",
        "/sections",
        teacher,
        { courseId: course.id, title: "Live change" },
        [409],
      );
      const versions = await req(
        "GET",
        `/courses/${course.id}/versions`,
        teacher,
      );
      assert.equal(versions.length, 1);
    });
    it("enrolls for free and preserves the approved version during another edit", async () => {
      enrollment = await req("POST", `/enrollments/free/${course.id}`, student);
      workspace.document.course.title = "Pilot revised title";
      workspace.document.course.sections[0].lessons[0].content =
        "Article version two";
      await save();
      const visible = await req("GET", `/courses/${course.id}`, student);
      assert.equal(visible.title, course.title);
      assert.equal(
        visible.sections[0].lessons[0].content,
        "Article version one",
      );
      await req("GET", `/courses/${course.id}/workspace`, other, null, [403]);
    });
    it("records article completion and moves the lesson without losing its progress", async () => {
      await req(
        "PUT",
        `/progress/${enrollment.id}/lesson/${article}`,
        student,
        { sectionId: section, completed: true },
      );
      const lesson = workspace.document.course.sections[0].lessons.shift();
      workspace.document.course.sections[1].lessons.push(lesson);
      await save();
      await publish();
      await req(
        "PUT",
        `/progress/${enrollment.id}/lesson/${article}`,
        student,
        { sectionId: second, completed: true },
      );
      const progress = await req("GET", `/progress/${enrollment.id}`, student);
      assert.equal(
        progress.find((p) => p.lessonId === article).sectionId,
        second,
      );
      workspace.document.course.sections[1].lessons = [];
      await req(
        "PUT",
        `/courses/${course.id}/workspace`,
        teacher,
        { revision: workspace.revision, document: workspace.document },
        [409],
      );
      workspace = await req("GET", `/courses/${course.id}/workspace`, teacher);
    });
    it("starts a timed random test, protects answer keys and enforces one attempt", async () => {
      const publicQuiz = await req("GET", lessonPath(), student);
      assert.equal(publicQuiz.questions.length, 0);
      attempt = await req("POST", `${lessonPath()}/start`, student, {
        enrollmentId: enrollment.id,
      });
      assert.equal(attempt.quiz.questions.length, 1);
      assert.ok(attempt.expiresAt.endsWith("Z"));
      assert.ok(
        Date.parse(attempt.expiresAt) - Date.now() > 20000 &&
          Date.parse(attempt.expiresAt) - Date.now() < 31000,
        "Quiz expiry must be 30 seconds away regardless of server timezone",
      );
      assert.equal(attempt.quiz.questions[0].correctOption, undefined);
      const resumed = await req("POST", `${lessonPath()}/start`, student, {
        enrollmentId: enrollment.id,
      });
      assert.equal(resumed.id, attempt.id);
      await req(
        "POST",
        `${lessonPath()}/attempts`,
        other,
        {
          enrollmentId: enrollment.id,
          sessionId: attempt.id,
          answers: [{ questionId: attempt.quiz.questions[0].id, option: 0 }],
        },
        [403],
      );
      const result = await req("POST", `${lessonPath()}/attempts`, student, {
        enrollmentId: enrollment.id,
        sessionId: attempt.id,
        answers: [{ questionId: attempt.quiz.questions[0].id, option: 0 }],
      });
      assert.equal(result.passed, true);
      assert.ok(result.feedback[0].explanation);
      await req(
        "POST",
        `${lessonPath()}/start`,
        student,
        { enrollmentId: enrollment.id },
        [409],
      );
      await req(
        "POST",
        `${lessonPath()}/attempts`,
        student,
        {
          enrollmentId: enrollment.id,
          answers: [{ questionId: attempt.quiz.questions[0].id, option: 0 }],
        },
        [400],
      );
    });
    it("keeps plans and bookmarks private and supports their full lifecycle", async () => {
      const day = new Date().toISOString().slice(0, 10);
      await req("POST", "/enrollments/learning/tasks", student, {
        title: "Review foundations",
        dueDate: day,
        enrollmentId: enrollment.id,
      });
      let dash = await req("GET", "/enrollments/learning/dashboard", student);
      const task = dash.tasks.find((t) => t.title === "Review foundations");
      assert.ok(task);
      await req(
        "PUT",
        `/enrollments/learning/tasks/${task.id}`,
        other,
        { done: true },
        [404],
      );
      await req("PUT", `/enrollments/learning/tasks/${task.id}`, student, {
        done: true,
      });
      await req("POST", `/enrollments/${enrollment.id}/bookmarks`, student, {
        lessonId: article,
        sectionId: second,
        title: "Important concept",
        positionSeconds: 0,
      });
      const bookmarks = await req(
        "GET",
        `/enrollments/${enrollment.id}/bookmarks`,
        student,
      );
      assert.equal(bookmarks.length, 1);
      await req(
        "GET",
        `/enrollments/${enrollment.id}/bookmarks`,
        other,
        null,
        [403],
      );
      await req(
        "DELETE",
        `/enrollments/${enrollment.id}/bookmarks/${bookmarks[0].id}`,
        student,
      );
      await req("DELETE", `/enrollments/learning/tasks/${task.id}`, student);
    });
    it("uploads a private assignment file and blocks classmates from downloading it", async () => {
      const form = new FormData();
      form.set(
        "file",
        new Blob(["A private assignment\n"], { type: "text/plain" }),
        "project.txt",
      );
      form.set("courseId", course.id);
      form.set("purpose", "ASSIGNMENT");
      const response = await fetch(base + "/media/resources", {
        method: "POST",
        headers: { Authorization: `Bearer ${student.accessToken}` },
        body: form,
      });
      assert.equal(response.status, 200);
      file = (await response.json()).data;
      const own = await fetch(new URL(file.url, base), {
        headers: { Authorization: `Bearer ${student.accessToken}` },
      });
      assert.equal(own.status, 200);
      assert.match(await own.text(), /private assignment/);
      const outsider = await fetch(new URL(file.url, base), {
        headers: { Authorization: `Bearer ${other.accessToken}` },
      });
      assert.equal(outsider.status, 403);
      const instructor = await fetch(new URL(file.url, base), {
        headers: { Authorization: `Bearer ${teacher.accessToken}` },
      });
      assert.equal(instructor.status, 200);
    });
    it("preserves assignment revisions and sends a grade notification", async () => {
      const path = `/courses/${course.id}/lessons/${assignment}/assignment`;
      let submitted = await req("POST", path, student, {
        enrollmentId: enrollment.id,
        content: "First project",
        attachmentUrl: file.url,
      });
      await req("PUT", `${path}/submissions/${submitted.id}`, teacher, {
        status: "CHANGES_REQUESTED",
        feedback: "Improve the explanation",
      });
      submitted = await req("POST", path, student, {
        enrollmentId: enrollment.id,
        content: "Revised project",
        attachmentUrl: file.url,
      });
      const history = await req("GET", `${path}/history`, student);
      assert.ok(history.some((h) => h.feedback === "Improve the explanation"));
      await req("PUT", `${path}/submissions/${submitted.id}`, teacher, {
        status: "ACCEPTED",
        score: 95,
        feedback: "Well done",
      });
      const e = await req("GET", `/enrollments/${enrollment.id}`, student);
      assert.equal(e.status, "COMPLETED");
      assert.equal(e.progressPercentage, 100);
    });
    it("provides owner-only learning reports, wishlist and combined course filters", async () => {
      await req(
        "GET",
        `/enrollments/courses/${course.id}/analytics`,
        other,
        null,
        [403],
      );
      const learning = await req(
        "GET",
        `/enrollments/courses/${course.id}/analytics`,
        teacher,
      );
      assert.ok(learning.summary.completed >= 1);
      const assessments = await req(
        "GET",
        `/courses/${course.id}/analytics`,
        teacher,
      );
      assert.equal(Number(assessments.quizResults[0].passed), 1);
      await req("PUT", `/courses/${course.id}/wishlist`, student);
      const wishes = await req("GET", "/courses/wishlist", student);
      assert.ok(wishes.some((c) => c.id === course.id));
      assert.equal((await req("GET", "/courses/wishlist", other)).length, 0);
      await req("DELETE", `/courses/${course.id}/wishlist`, student);
      const found = await req(
        "GET",
        "/courses/discover?query=Pilot%20revised&level=BEGINNER&language=Türkçe&free=true",
      );
      assert.ok(found.content.some((c) => c.id === course.id));
    });
    it("fans course announcements out into student inboxes without duplicates", async () => {
      const subject = `Pilot announcement ${randomUUID()}`;
      await req("POST", `/courses/${course.id}/announcements`, teacher, {
        title: subject,
        content: "New learning materials are available.",
      });
      let notices = [];
      for (let n = 0; n < 25; n++) {
        notices = await req("GET", "/notifications/inbox", student);
        if (notices.some((n) => n.subject === subject)) break;
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
      assert.equal(notices.filter((n) => n.subject === subject).length, 1);
      const notice = notices.find((n) => n.subject === subject);
      await req("PUT", `/notifications/inbox/${notice.id}/read`, student);
      const read = await req("GET", "/notifications/inbox", student);
      assert.ok(read.find((n) => n.id === notice.id).read_at);
    });
    it("authorizes support threads and preserves staff responses", async () => {
      const ticket = await req("POST", "/users/me/tickets", student, {
        subject: "A question about course content",
        category: "CONTENT_REPORT",
        content: "Please review this lesson",
        courseId: course.id,
      });
      await req(
        "GET",
        `/users/me/tickets/${ticket.id}/replies`,
        other,
        null,
        [403],
      );
      await req("POST", `/users/me/tickets/${ticket.id}/replies`, admin, {
        content: "We reviewed your report",
        close: true,
      });
      const replies = await req(
        "GET",
        `/users/me/tickets/${ticket.id}/replies`,
        student,
      );
      assert.equal(replies[0].staff, true);
      assert.match(replies[0].content, /reviewed/);
    });
    it("enforces scoped administrator permissions and revokes old sessions", async () => {
      let staff = await register("STUDENT");
      const password = passwords.get(staff.user.id);
      await req("PUT", `/users/admin/${staff.user.id}`, admin, {
        role: "ADMIN",
        status: "ACTIVE",
        reason: "Pilot support staff",
      });
      await req("GET", "/users/me", staff, null, [401]);
      await req("PUT", `/users/admin/${staff.user.id}/scope`, admin, {
        scope: "SUPPORT",
        reason: "Support only",
      });
      staff = await req("POST", "/auth/login", null, {
        email: staff.user.email,
        password,
      });
      await req("GET", "/users/admin/tickets", staff);
      await req("GET", "/users/search?query=Pilot", staff, null, [403]);
      await req("GET", "/courses/admin", staff, null, [403]);
      await req(
        "PUT",
        `/users/admin/${teacher.user.id}`,
        staff,
        { role: "ADMIN", status: "ACTIVE", reason: "Forbidden escalation" },
        [403],
      );
    });
    it("rotates refresh tokens, detects replay and revokes access immediately", async () => {
      const temp = await register("STUDENT");
      const rotated = await req("POST", "/auth/refresh", null, {
        refreshToken: temp.refreshToken,
      });
      assert.notEqual(rotated.refreshToken, temp.refreshToken);
      await req(
        "POST",
        "/auth/refresh",
        null,
        { refreshToken: temp.refreshToken },
        [401],
      );
      await req("GET", "/users/me", rotated, null, [401]);
      const session = await register("STUDENT");
      const open = await req("GET", "/users/me/sessions", session);
      assert.equal(open.length, 1);
      await req("DELETE", `/users/me/sessions/${open[0].id}`, session);
      await req("GET", "/enrollments/my", session, null, [401]);
    });
    function otp(secret, stepOffset = 0) {
      const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
      let bits = "";
      for (const c of secret)
        bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
      const key = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
      const counter = Buffer.alloc(8);
      counter.writeBigUInt64BE(
        BigInt(Math.floor(Date.now() / 30000) + stepOffset),
      );
      const h = createHmac("sha1", key).update(counter).digest();
      const offset = h[h.length - 1] & 15;
      return String((h.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(
        6,
        "0",
      );
    }
    it("enables TOTP, requires it at login and rejects reused codes", async () => {
      const temp = await register("STUDENT");
      const password = passwords.get(temp.user.id);
      const setup = await req("POST", "/users/me/mfa/setup", temp, {
        password,
      });
      assert.ok(setup.secret);
      const code = otp(setup.secret);
      await req("POST", "/users/me/mfa/confirm", temp, { code });
      await req(
        "POST",
        "/auth/login",
        null,
        { email: temp.user.email, password },
        [401],
      );
      await req(
        "POST",
        "/auth/login",
        null,
        { email: temp.user.email, password, otp: code },
        [401],
      );
      const valid = await req("POST", "/auth/login", null, {
        email: temp.user.email,
        password,
        otp: otp(setup.secret, 1),
      });
      assert.equal(valid.user.id, temp.user.id);
    });
    it("exports personal data without password or MFA secrets and records erasure requests", async () => {
      const exported = await req("GET", "/users/me/export", student);
      assert.ok(exported.profile.email);
      assert.equal(exported.profile.password, undefined);
      assert.equal(exported.profile.secret, undefined);
      const temp = await register("STUDENT");
      await req("POST", "/users/me/erasure", temp, {
        password: passwords.get(temp.user.id),
      });
      await req("GET", "/users/me", temp, null, [401]);
      const requests = await req("GET", "/users/admin/privacy", admin);
      assert.ok(
        requests.some((r) => r.user_id === temp.user.id && r.status === "OPEN"),
      );
    });
    it("delivers a password reset email, consumes it once and revokes previous access", async () => {
      const temp = await register("STUDENT");
      await req("POST", "/auth/forgot-password", null, {
        email: temp.user.email,
      });
      await req("POST", "/auth/forgot-password", null, {
        email: `absent-${randomUUID()}@example.test`,
      });
      let token;
      for (let n = 0; n < 30; n++) {
        const response = await fetch(
          (process.env.LMS_E2E_MAILHOG_URL || "http://127.0.0.1:8025") +
            "/api/v2/messages?limit=100",
        );
        const messages = await response.json();
        for (const m of messages.items || []) {
          if (
            !m.Content?.Headers?.To?.some((to) => to.includes(temp.user.email))
          )
            continue;
          const decoded = (m.Content.Body || "")
            .replace(/=\r?\n/g, "")
            .replace(/=([0-9A-F]{2})/gi, (_, hex) =>
              String.fromCharCode(parseInt(hex, 16)),
            );
          const match = decoded.match(/reset-password\?token=([^"\s<]+)/);
          if (match)
            token = decodeURIComponent(match[1].replace(/&amp;.*/, ""));
        }
        if (token) break;
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
      assert.ok(token, "Password reset email did not arrive");
      const next = `New-${randomUUID()}!`;
      await req("POST", "/auth/reset-password", null, {
        token,
        password: next,
      });
      await req(
        "POST",
        "/auth/reset-password",
        null,
        { token, password: next },
        [400],
      );
      await req("GET", "/users/me", temp, null, [401]);
      const signed = await req("POST", "/auth/login", null, {
        email: temp.user.email,
        password: next,
      });
      assert.equal(signed.user.id, temp.user.id);
    });
    it("transcodes an uploaded video, publishes subtitles and enforces public preview policy", async () => {
      const directory = mkdtempSync(join(tmpdir(), "lms-platform-video-"));
      try {
        const source = join(directory, "lesson.mp4");
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
          source,
        ]);
        const form = new FormData();
        form.set(
          "file",
          new Blob([readFileSync(source)], { type: "video/mp4" }),
          "lesson.mp4",
        );
        form.set("courseId", course.id);
        const upload = await fetch(base + "/media/video", {
          method: "POST",
          headers: { Authorization: `Bearer ${teacher.accessToken}` },
          body: form,
        });
        assert.equal(upload.status, 201);
        let media = (await upload.json()).data;
        for (let n = 0; n < 30; n++) {
          media = await req("GET", `/media/${media.id}`, teacher);
          if (media.status === "READY") break;
          assert.notEqual(media.status, "FAILED");
          await new Promise((resolve) => setTimeout(resolve, 1000));
        }
        assert.equal(media.status, "READY");
        assert.ok(media.durationSeconds >= 3);
        const resourceForm = new FormData();
        resourceForm.set(
          "file",
          new Blob(["WEBVTT\n\n00:00.000 --> 00:02.500\nPilot subtitle"], {
            type: "text/vtt",
          }),
          "captions.vtt",
        );
        resourceForm.set("courseId", course.id);
        resourceForm.set("purpose", "MATERIAL");
        const resourceResponse = await fetch(base + "/media/resources", {
          method: "POST",
          headers: { Authorization: `Bearer ${teacher.accessToken}` },
          body: resourceForm,
        });
        assert.equal(resourceResponse.status, 200);
        const resource = (await resourceResponse.json()).data;
        workspace = await req(
          "GET",
          `/courses/${course.id}/workspace`,
          teacher,
        );
        const video = randomUUID();
        workspace.document.course.sections[1].lessons.push({
          id: video,
          title: "Pilot video",
          type: "VIDEO",
          videoUrl: media.streamUrl,
          previewAllowed: true,
          orderIndex: 1,
        });
        workspace.document.materials[video] = [
          {
            title: "Türkçe altyazı",
            url: resource.url,
            type: "SUBTITLE",
            language: "tr",
          },
        ];
        await save();
        await publish();
        const publicMetadata = await req("GET", `/media/${media.id}`);
        assert.equal(publicMetadata.status, "READY");
        const manifest = await fetch(new URL(media.streamUrl, base));
        assert.equal(manifest.status, 200);
        assert.match(await manifest.text(), /#EXT-X-ENDLIST/);
        await req("DELETE", `/media/${media.id}`, teacher, null, [409]);
        workspace.document.course.sections[1].lessons.find(
          (l) => l.id === video,
        ).previewAllowed = false;
        await save();
        await publish();
        await req("GET", `/media/${media.id}`, null, null, [403]);
        const privateManifest = await fetch(new URL(media.streamUrl, base), {
          headers: { Authorization: `Bearer ${student.accessToken}` },
        });
        assert.equal(privateManifest.status, 200);
        const materials = await req(
          "GET",
          `/courses/${course.id}/lessons/${video}/materials`,
          student,
        );
        assert.equal(materials[0].type, "SUBTITLE");
        const earned = await req(
          "GET",
          `/enrollments/${enrollment.id}`,
          student,
        );
        assert.equal(earned.status, "COMPLETED");
        assert.equal(earned.progressPercentage, 100);
        course.video = video;
      } finally {
        rmSync(directory, { recursive: true, force: true });
      }
    });
    it("archives a course, withdraws pending changes and preserves existing access", async () => {
      workspace = await req("GET", `/courses/${course.id}/workspace`, teacher);
      workspace.document.course.shortDescription =
        "Pending change before archive";
      await save();
      workspace = await req(
        "POST",
        `/courses/${course.id}/workspace/submit?revision=${workspace.revision}`,
        teacher,
      );
      await req("POST", `/courses/${course.id}/archive`, teacher);
      workspace = await req("GET", `/courses/${course.id}/workspace`, teacher);
      assert.equal(workspace.status, "DRAFT");
      const existing = await req("GET", `/courses/${course.id}`, student);
      assert.equal(existing.status, "ARCHIVED");
      await req("GET", `/courses/${course.id}`, null, null, [404]);
      await req("POST", `/enrollments/free/${course.id}`, other, null, [409]);
      const earned = await req("GET", `/enrollments/${enrollment.id}`, student);
      assert.equal(earned.status, "COMPLETED");
    });
    after(() => {
      if (process.env.LMS_E2E_FIXTURE_PATH)
        writeFileSync(
          process.env.LMS_E2E_FIXTURE_PATH,
          JSON.stringify({
            admin,
            teacher,
            student,
            other,
            courseId: course.id,
            enrollmentId: enrollment.id,
            article,
            quiz,
            assignment,
            video: course.video,
          }),
          { mode: 0o600 },
        );
    });
  },
);
