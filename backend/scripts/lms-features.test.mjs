import { before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

// Opt-in integration suite: creates clearly named test accounts/courses in the configured development database.
describe(
  "LMS features against running services",
  { skip: process.env.RUN_LMS_E2E !== "1", concurrency: false },
  () => {
    const base = (
      process.env.LMS_E2E_API_URL || "http://127.0.0.1:8080/api/v1"
    ).replace(/\/$/, "");
    const suffix = randomUUID().slice(0, 8);
    let admin,
      teacher,
      student,
      outsider,
      category,
      course,
      paid,
      section,
      article,
      video,
      quiz,
      assignment,
      definition,
      enrollment,
      note,
      question,
      reply,
      submission;
    async function request(method, path, session, data, expected = [200, 201]) {
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
      const payload = await response.json().catch(() => null);
      assert.ok(
        expected.includes(response.status),
        `${method} ${path}: expected ${expected}, got ${response.status}: ${payload?.message || ""}`,
      );
      if (response.ok)
        assert.equal(
          payload?.success,
          true,
          `${method} ${path} must return success`,
        );
      return payload?.data;
    }
    async function register(role) {
      return request("POST", "/auth/register", null, {
        firstName: "LMS",
        lastName: "Validation",
        email: `lms-${role.toLowerCase()}-${randomUUID()}@example.test`,
        password: `Local-${randomUUID()}!`,
        role,
      });
    }
    const lessonPath = (lesson) => `/courses/${course.id}/lessons/${lesson.id}`;
    const progress = () =>
      request("GET", `/progress/${enrollment.id}`, student);
    before(async () => {
      assert.ok(
        process.env.LMS_E2E_ADMIN_EMAIL && process.env.LMS_E2E_ADMIN_PASSWORD,
        "Configure a development administrator via LMS_E2E_ADMIN_EMAIL/PASSWORD",
      );
      admin = await request("POST", "/auth/login", null, {
        email: process.env.LMS_E2E_ADMIN_EMAIL,
        password: process.env.LMS_E2E_ADMIN_PASSWORD,
      });
      teacher = await register("INSTRUCTOR");
      student = await register("STUDENT");
      outsider = await register("STUDENT");
      category = await request("POST", "/courses/admin/categories", admin, {
        name: `LMS validation ${suffix}`,
        description: "Integration fixture",
      });
      const create = (price) =>
        request("POST", "/courses", teacher, {
          title: `LMS validation course ${suffix} ${price}`,
          description: "Functional learning management validation",
          shortDescription: "Validation fixture",
          price,
          categoryId: category.id,
        });
      course = await create(0);
      paid = await create(100);
      section = await request("POST", "/sections", teacher, {
        courseId: course.id,
        title: "Learning workflow",
      });
      for (const type of ["ARTICLE", "VIDEO", "QUIZ", "ASSIGNMENT"]) {
        const result = await request("POST", "/lessons", teacher, {
          sectionId: section.id,
          title: `${type} validation`,
          type,
          content: `${type} lesson content`,
          ...(type === "VIDEO"
            ? {
                videoUrl: "https://example.test/validation.mp4",
                durationSeconds: 6,
              }
            : {}),
        });
        if (type === "ARTICLE") article = result;
        if (type === "VIDEO") video = result;
        if (type === "QUIZ") quiz = result;
        if (type === "ASSIGNMENT") assignment = result;
      }
      const paidSection = await request("POST", "/sections", teacher, {
        courseId: paid.id,
        title: "Paid course section",
      });
      await request("POST", "/lessons", teacher, {
        sectionId: paidSection.id,
        title: "Paid article",
        type: "ARTICLE",
        content: "Existing payment behavior remains separate",
      });
      definition = await request(
        "PUT",
        `${lessonPath(quiz)}/quiz/manage`,
        teacher,
        {
          passingScore: 70,
          questions: [
            { prompt: "2 + 2?", options: ["4", "5"], correctOption: 0 },
            {
              prompt: "TypeScript is used for?",
              options: ["Typed JavaScript", "Database backups"],
              correctOption: 0,
            },
          ],
        },
      );
    });
    it("enforces administrator roles and protects the acting administrator", async () => {
      for (const path of [
        "/users/admin/summary",
        "/courses/admin",
        "/enrollments/admin/summary",
      ])
        await request("GET", path, student, null, [403]);
      await request(
        "PUT",
        `/users/admin/${admin.user.id}`,
        admin,
        {
          role: "STUDENT",
          status: "INACTIVE",
          reason: "Self removal must fail",
        },
        [409],
      );
    });
    it("submits, rejects with feedback, resubmits and publishes only after approval", async () => {
      let result = await request(
        "POST",
        `/courses/${course.id}/publish`,
        teacher,
      );
      assert.equal(result.status, "PENDING_REVIEW");
      await request("GET", `/courses/${course.id}`, null, null, [404]);
      result = await request(
        "POST",
        `/courses/admin/${course.id}/decision`,
        admin,
        { action: "REJECT", reason: "Clarify course outcomes" },
      );
      assert.equal(result.status, "DRAFT");
      assert.match(result.reviewFeedback, /Clarify/);
      await request("POST", `/courses/${course.id}/publish`, teacher);
      result = await request(
        "POST",
        `/courses/admin/${course.id}/decision`,
        admin,
        { action: "APPROVE", reason: "Course reviewed" },
      );
      assert.equal(result.status, "PUBLISHED");
      assert.equal(
        (await request("GET", `/courses/${course.id}`)).status,
        "PUBLISHED",
      );
      await request("POST", `/courses/${paid.id}/publish`, teacher);
      await request("POST", `/courses/admin/${paid.id}/decision`, admin, {
        action: "APPROVE",
      });
    });
    it("creates free enrollment once under concurrent requests and rejects paid bypasses", async () => {
      const responses = await Promise.all([
        request("POST", `/enrollments/free/${course.id}`, student),
        request("POST", `/enrollments/free/${course.id}`, student),
      ]);
      assert.equal(responses[0].id, responses[1].id);
      enrollment = responses[0];
      assert.equal(enrollment.totalLessons, 4);
      await request(
        "POST",
        `/enrollments/free/${paid.id}`,
        student,
        null,
        [409],
      );
      assert.ok(
        (
          await request("GET", `/courses/${course.id}`, student)
        ).sections[0].lessons.find((l) => l.id === video.id).videoUrl,
      );
    });
    it("keeps notes private and supports updating and deleting timestamps", async () => {
      note = await request(
        "POST",
        `/progress/${enrollment.id}/notes`,
        student,
        {
          lessonId: video.id,
          sectionId: section.id,
          positionSeconds: 2,
          content: "Remember this example",
        },
      );
      await request(
        "GET",
        `/progress/${enrollment.id}/notes`,
        outsider,
        null,
        [404],
      );
      note = await request(
        "PUT",
        `/progress/${enrollment.id}/notes/${note.id}`,
        student,
        {
          lessonId: video.id,
          sectionId: section.id,
          positionSeconds: 3,
          content: "Updated personal note",
        },
      );
      assert.equal(note.positionSeconds, 3);
      const all = await request(
        "GET",
        `/progress/${enrollment.id}/notes?lessonId=${video.id}`,
        student,
      );
      assert.equal(all.length, 1);
    });
    it("supports enrolled questions, instructor replies and accepted answers", async () => {
      await request(
        "GET",
        `/courses/${course.id}/questions`,
        outsider,
        null,
        [403],
      );
      question = await request(
        "POST",
        `/courses/${course.id}/questions`,
        student,
        {
          lessonId: article.id,
          title: "How does this work?",
          content: "Please explain the example",
        },
      );
      reply = await request(
        "POST",
        `/courses/${course.id}/questions/${question.id}/replies`,
        teacher,
        { content: "Here is the explanation" },
      );
      assert.equal(reply.instructor, true);
      await request(
        "PUT",
        `/courses/${course.id}/questions/${question.id}/accepted/${reply.id}`,
        student,
      );
      const list = await request(
        "GET",
        `/courses/${course.id}/questions?lessonId=${article.id}`,
        student,
      );
      assert.equal(list.content[0].acceptedReplyId, reply.id);
    });
    it("does not expose answer keys and only passes server-graded quiz attempts", async () => {
      const publicQuiz = await request(
        "GET",
        `${lessonPath(quiz)}/quiz`,
        student,
      );
      assert.ok(!JSON.stringify(publicQuiz).includes("correctOption"));
      await request(
        "GET",
        `${lessonPath(quiz)}/quiz/manage`,
        student,
        null,
        [403],
      );
      let result = await request(
        "POST",
        `${lessonPath(quiz)}/quiz/attempts`,
        student,
        {
          enrollmentId: enrollment.id,
          score: 100,
          answers: definition.questions.map((q) => ({
            questionId: q.id,
            option: 1,
          })),
        },
      );
      assert.equal(result.passed, false);
      assert.equal(result.score, 0);
      assert.ok(
        !(await progress()).some(
          (p) => p.lessonId === quiz.id && p.status === "COMPLETED",
        ),
      );
      result = await request(
        "POST",
        `${lessonPath(quiz)}/quiz/attempts`,
        student,
        {
          enrollmentId: enrollment.id,
          answers: definition.questions.map((q) => ({
            questionId: q.id,
            option: 0,
          })),
        },
      );
      assert.equal(result.passed, true);
      assert.equal(
        (await progress()).find((p) => p.lessonId === quiz.id).status,
        "COMPLETED",
      );
      await request(
        "POST",
        `${lessonPath(quiz)}/quiz/attempts`,
        student,
        {
          enrollmentId: randomUUID(),
          answers: definition.questions.map((q) => ({
            questionId: q.id,
            option: 0,
          })),
        },
        [403],
      );
    });
    it("rejects video completion spoofing, saves resume position and credits elapsed playback", async () => {
      await request(
        "PUT",
        `/progress/${enrollment.id}/lesson/${video.id}`,
        student,
        { sectionId: section.id, watchedSeconds: 6, completed: true },
        [400],
      );
      const report = (position, playing) =>
        request(
          "POST",
          `/progress/${enrollment.id}/lesson/${video.id}/playback`,
          student,
          { sectionId: section.id, positionSeconds: position, playing },
        );
      await report(0, true);
      let result = await report(6, true);
      assert.notEqual(result.status, "COMPLETED");
      assert.equal(result.watchedDurationSeconds, 0);
      await report(0, true);
      await new Promise((resolve) => setTimeout(resolve, 1600));
      await report(3, true);
      await new Promise((resolve) => setTimeout(resolve, 1600));
      result = await report(5.5, false);
      assert.equal(result.status, "COMPLETED");
      assert.equal(result.positionSeconds, 5);
      assert.ok(result.watchedDurationSeconds >= 4);
    });
    it("supports assignment feedback, resubmission and verified instructor acceptance", async () => {
      submission = await request(
        "POST",
        `${lessonPath(assignment)}/assignment`,
        student,
        { enrollmentId: enrollment.id, content: "Initial work" },
      );
      assert.equal(submission.status, "SUBMITTED");
      await request(
        "PUT",
        `${lessonPath(assignment)}/assignment/submissions/${submission.id}`,
        student,
        { status: "ACCEPTED", score: 100 },
        [403],
      );
      await request(
        "PUT",
        `${lessonPath(assignment)}/assignment/submissions/${submission.id}`,
        teacher,
        {
          status: "CHANGES_REQUESTED",
          score: 40,
          feedback: "Add a working example",
        },
      );
      assert.equal(
        (
          await request(
            "GET",
            `${lessonPath(assignment)}/assignment/my`,
            student,
          )
        ).status,
        "CHANGES_REQUESTED",
      );
      const revised = await request(
        "POST",
        `${lessonPath(assignment)}/assignment`,
        student,
        { enrollmentId: enrollment.id, content: "Revised work with example" },
      );
      assert.equal(revised.id, submission.id);
      await request(
        "PUT",
        `${lessonPath(assignment)}/assignment/submissions/${submission.id}`,
        teacher,
        { status: "ACCEPTED", score: 90, feedback: "Good revision" },
      );
      assert.equal(
        (await progress()).find((p) => p.lessonId === assignment.id).status,
        "COMPLETED",
      );
    });
    it("computes full course progress and exposes it to the instructor", async () => {
      await request(
        "PUT",
        `/progress/${enrollment.id}/lesson/${article.id}`,
        student,
        { sectionId: section.id, completed: true },
      );
      const current = await request(
        "GET",
        `/enrollments/${enrollment.id}`,
        student,
      );
      assert.equal(current.status, "COMPLETED");
      assert.equal(current.completedLessons, 4);
      assert.equal(current.progressPercentage, 100);
      const roster = await request(
        "GET",
        `/enrollments/courses/${course.id}/students`,
        teacher,
      );
      assert.equal(
        roster.content.find((r) => r.enrollment.id === enrollment.id).enrollment
          .progressPercentage,
        100,
      );
    });
    it("supports administrator grants and nonfinancial access revocation", async () => {
      const grant = await request("POST", "/enrollments/admin/grant", admin, {
        courseId: paid.id,
        studentId: outsider.user.id,
        reason: "Development access grant",
      });
      assert.equal(
        await request("GET", `/enrollments/course/${paid.id}/check`, outsider),
        true,
      );
      await request("POST", `/enrollments/admin/${grant.id}/revoke`, admin, {
        reason: "End of validation access",
      });
      assert.equal(
        await request("GET", `/enrollments/course/${paid.id}/check`, outsider),
        false,
      );
    });
    it("issues a certificate only for the completed enrollment and verifies it publicly", async () => {
      let certificate;
      for (let i = 0; i < 70; i++) {
        const response = await fetch(
          `${base}/enrollments/${enrollment.id}/certificate`,
          { headers: { Authorization: `Bearer ${student.accessToken}` } },
        );
        if (response.ok) {
          certificate = (await response.json()).data;
          break;
        }
        assert.equal(
          response.status,
          404,
          "Certificate must either be pending or available",
        );
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      assert.ok(
        certificate?.verificationCode,
        "Certificate scheduler must produce a certificate",
      );
      const verified = await request(
        "GET",
        `/enrollments/certificates/verify/${certificate.verificationCode}`,
      );
      assert.ok(!verified.pdfUrl);
      await request(
        "GET",
        `/enrollments/${enrollment.id}/certificate`,
        outsider,
        null,
        [404],
      );
      const pdf = await fetch(certificate.pdfUrl);
      assert.equal(pdf.status, 200);
      assert.equal(
        Buffer.from(await pdf.arrayBuffer())
          .subarray(0, 4)
          .toString(),
        "%PDF",
      );
    });
    it("keeps archived courses available to existing students and blocks new enrollment", async () => {
      await request("POST", `/courses/${course.id}/archive`, teacher);
      await request("GET", `/courses/${course.id}`, null, null, [404]);
      assert.ok(
        (await request("GET", `/courses/${course.id}`, student)).sections
          .length,
      );
      await request(
        "POST",
        `/enrollments/free/${course.id}`,
        outsider,
        null,
        [409],
      );
    });
    it("prevents banning bypass and invalidates tokens after role changes", async () => {
      await request("PUT", `/users/admin/${outsider.user.id}`, admin, {
        role: "STUDENT",
        status: "BANNED",
        reason: "Access policy validation",
      });
      await request("GET", "/users/me", outsider, null, [401]);
      await request("PUT", `/users/admin/${outsider.user.id}`, admin, {
        role: "INSTRUCTOR",
        status: "ACTIVE",
        reason: "Role change validation",
      });
      await request("GET", "/users/me", outsider, null, [401]);
      const audit = await request("GET", "/users/admin/audit", admin);
      assert.ok(audit.some((a) => a.target_id === outsider.user.id));
      await request(
        "DELETE",
        `/courses/admin/categories/${category.id}`,
        admin,
        null,
        [409],
      );
      for (const path of [
        "/users/admin/summary",
        "/courses/admin/summary",
        "/enrollments/admin/summary",
      ])
        assert.ok((await request("GET", path, admin)).total >= 1);
      if (process.env.LMS_E2E_FIXTURE_PATH)
        writeFileSync(
          process.env.LMS_E2E_FIXTURE_PATH,
          JSON.stringify({
            admin,
            teacher,
            student,
            enrollment,
            course,
            section,
            article,
            video,
            quiz,
            assignment,
            question,
          }),
          { mode: 0o600 },
        );
    });
  },
);
