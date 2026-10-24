import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

const baseCourse = {
  id: 'course-1',
  instructorId: 'owner-1',
  instructorName: 'Course Owner',
  title: 'A practical training course',
  description: 'Course description',
  shortDescription: 'Practical learning',
  price: 0,
  status: 'PUBLISHED',
  level: 'BEGINNER',
  language: 'Turkish',
  publishedAt: '2026-10-20T10:00:00',
  requirements: ['None'],
  outcomes: ['Practice'],
  sections: [
    {
      id: 'section-1',
      title: 'Getting started',
      orderIndex: 0,
      lessons: [
        {
          id: 'article-1',
          title: 'Introduction',
          type: 'ARTICLE',
          orderIndex: 0,
          content: 'Original lesson',
          previewAllowed: false,
        },
      ],
    },
  ],
};
const envelope = (data: unknown) => ({ success: true, data, message: 'OK' });
const pageData = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  size: 9,
  number: 0,
});
async function setup(page: Page, editor = false) {
  const user = {
    id: editor ? 'editor-1' : 'owner-1',
    firstName: 'Course',
    lastName: editor ? 'Editor' : 'Owner',
    email: 'teacher@academy.test',
    role: 'INSTRUCTOR',
    status: 'ACTIVE',
  };
  await page.addInitScript(
    (user) =>
      sessionStorage.setItem(
        'ileri.session',
        JSON.stringify({
          user,
          accessToken: 'test-token',
          refreshToken: 'test-refresh',
          expiresIn: 3600000,
        }),
      ),
    user,
  );
  let workspace = {
    revision: 1,
    status: 'DRAFT',
    checklist: [],
    document: { course: structuredClone(baseCourse), categoryId: 1, quizzes: {}, materials: {} },
  };
  let members: { user_id: string; display_name: string; status: string }[] = editor
    ? [{ user_id: 'editor-1', display_name: 'Course Editor', status: 'ACCEPTED' }]
    : [];
  let saved: any;
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/learning-rules'))
      data = { revision: 0, sequential: false, releaseDays: 0, prerequisites: [] };
    else if (path.endsWith('/assignment/policy'))
      data = { revision: 0, allowLate: true, rubric: [] };
    else if (path.endsWith('/learning/reminders'))
      data = { enabled: false, timeZone: 'UTC', hour: 18 };
    else if (path === '/api/v1/users/me') data = user;
    else if (path === '/api/v1/courses/discover') data = pageData([baseCourse]);
    else if (path === '/api/v1/courses/categories') data = [{ id: 1, name: 'Software' }];
    else if (path === '/api/v1/courses/mine') data = pageData([baseCourse]);
    else if (path === '/api/v1/courses/templates')
      data = [{ id: 'starter', name: 'Starter course', builtIn: true }];
    else if (path.endsWith('/templates/starter/create')) {
      const input = request.postDataJSON();
      expect(input.title).toBe('My template training course');
      expect(input.categoryId).toBe(1);
      data = { ...baseCourse, id: 'new-course', title: input.title };
    } else if (path.endsWith('/workspace')) {
      if (request.method() === 'PUT') {
        saved = request.postDataJSON();
        workspace = { ...workspace, revision: workspace.revision + 1, document: saved.document };
      }
      data = workspace;
    } else if (path.endsWith('/team')) {
      if (request.method() === 'POST') {
        expect(request.postDataJSON().email).toBe('editor@academy.test');
        members = [{ user_id: 'editor-1', display_name: 'Course Editor', status: 'INVITED' }];
      }
      data = { ownerId: 'owner-1', ownerName: 'Course Owner', canManage: !editor, members };
    } else if (path.startsWith('/api/v1/media/my')) data = pageData([]);
    else if (path === '/api/v1/courses/course-1' || path === '/api/v1/courses/new-course')
      data = baseCourse;
    await route.fulfill({ json: envelope(data) });
  });
  return { saved: () => saved };
}

test('formatting tools save Markdown and preview never executes raw HTML', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/instructor/course-1');
  await page.getByRole('button', { name: 'Introduction · Article' }).click();
  const dialog = page.getByRole('dialog'),
    text = dialog.getByLabel('Lesson text');
  await text.fill('Formatted lesson');
  await text.press('ControlOrMeta+A');
  await dialog.getByRole('button', { name: 'Bold', exact: true }).click();
  await expect(text).toHaveValue('**Formatted lesson**');
  await dialog.getByRole('button', { name: 'Preview text' }).click();
  await expect(dialog.getByRole('region', { name: 'Formatted text' }).locator('strong')).toHaveText(
    'Formatted lesson',
  );
  await text.fill('<img src="x" onerror="window.badHtml=true">\n**Safe text**');
  await expect(dialog.getByRole('region').locator('img')).toHaveCount(0);
  expect(await page.evaluate(() => Boolean((window as any).badHtml))).toBe(false);
  await dialog.getByRole('button', { name: 'Finish editing' }).click();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect
    .poll(() => state.saved()?.document.course.sections[0].lessons[0].content)
    .toContain('**Safe text**');
});

test('a published lesson can be copied without changing its original identity', async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto('/instructor/course-1');
  await page.getByRole('button', { name: 'Introduction duplicate lesson' }).click();
  await expect(page.getByRole('button', { name: 'Introduction (copy) · Article' })).toBeVisible();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect.poll(() => state.saved()?.document.course.sections[0].lessons.length).toBe(2);
  const lessons = state.saved().document.course.sections[0].lessons;
  expect(lessons[0].id).toBe('article-1');
  expect(lessons[1].id).not.toBe('article-1');
});

test('course owners can invite a verified instructor from the team panel', async ({ page }) => {
  await setup(page);
  await page.goto('/instructor/course-1');
  await page.getByRole('button', { name: 'Course team', exact: true }).click();
  await page.getByLabel("Instructor's registered email address").fill('editor@academy.test');
  await page.getByRole('button', { name: 'Invite instructor' }).click();
  await expect(page.getByText('Course Editor · Awaiting acceptance')).toBeVisible();
});

test('shared editors can edit drafts while publication and student management stay with the owner', async ({
  page,
}) => {
  await setup(page, true);
  await page.goto('/instructor/course-1');
  await expect(
    page.getByText('You are a co-instructor on this course.', { exact: false }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Archive course', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Learners', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Course team', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Leave team', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Invite instructor' })).toHaveCount(0);
});

test('the instructor studio creates a draft from a selected template', async ({ page }) => {
  await setup(page);
  await page.goto('/instructor');
  await page.getByRole('button', { name: 'Create course from template', exact: true }).click();
  await page.getByLabel('New course title').fill('My template training course');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page).toHaveURL(/\/instructor\/new-course$/);
});

test.describe('real collaborative authoring', () => {
  test.skip(
    process.env.E2E_REAL_BACKEND !== '1' || !process.env.LMS_AUTHORING_FIXTURE_PATH,
    'Requires real services and the course-authoring API fixture.',
  );
  test('owners create, format and copy lessons, invite an editor and save a reusable template', async ({
    page,
    browser,
  }) => {
    const fixture = JSON.parse(readFileSync(process.env.LMS_AUTHORING_FIXTURE_PATH!, 'utf8'));
    async function signIn(target: Page, auth: any) {
      await target.addInitScript(
        (auth) => sessionStorage.setItem('ileri.session', JSON.stringify(auth)),
        auth,
      );
    }
    await signIn(page, fixture.owner);
    await page.goto('/instructor');
    await page.getByRole('button', { name: 'Create course from template', exact: true }).click();
    const title = `Browser authoring ${Date.now()}`;
    await page.getByLabel('New course title').fill(title);
    await page.getByRole('button', { name: 'Create draft', exact: true }).click();
    await expect(page).toHaveURL(/\/instructor\/[0-9a-f-]{36}$/);
    const url = page.url();
    await page.getByRole('button', { name: 'Welcome duplicate lesson', exact: true }).click();
    await page.getByRole('button', { name: 'Welcome (copy) · Article', exact: true }).click();
    const dialog = page.getByRole('dialog'),
      text = dialog.getByLabel('Lesson text');
    await text.fill('Learning with examples');
    await text.press('ControlOrMeta+A');
    await dialog.getByRole('button', { name: 'Bold', exact: true }).click();
    await expect(text).toHaveValue('**Learning with examples**');
    await dialog.getByRole('button', { name: 'Finish editing' }).click();
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Course team', exact: true }).click();
    await page.getByLabel("Instructor's registered email address").fill(fixture.editor.user.email);
    await page.getByRole('button', { name: 'Invite instructor' }).click();
    await expect(page.getByText('Author INSTRUCTOR · Awaiting acceptance')).toBeVisible();
    const context = await browser.newContext();
    try {
      const editor = await context.newPage();
      await signIn(editor, fixture.editor);
      await editor.goto('/instructor');
      await editor.getByRole('button', { name: 'Accept invitation', exact: true }).click();
      await expect(
        editor.getByRole('button', { name: 'Accept invitation', exact: true }),
      ).toHaveCount(0);
      await editor.goto(url);
      await expect(
        editor.getByText('You are a co-instructor on this course.', { exact: false }),
      ).toBeVisible();
      await expect(
        editor.getByRole('button', { name: 'Submit for review', exact: true }),
      ).toHaveCount(0);
      await editor
        .getByLabel('Description', { exact: true })
        .fill('Course description edited by a shared instructor');
      await editor.getByRole('button', { name: 'Save draft', exact: true }).click();
      await expect(editor.getByText('Changes saved.', { exact: true })).toBeVisible();
    } finally {
      await context.close();
    }
    await page.goto(url);
    await expect(page.getByLabel('Description', { exact: true })).toHaveValue(
      'Course description edited by a shared instructor',
    );
    const templateName = `Browser template ${Date.now()}`;
    page.once('dialog', (dialog) => void dialog.accept(templateName));
    await page.getByRole('button', { name: 'Save as template', exact: true }).click();
    await expect(page.getByText('Personal template saved.', { exact: false })).toBeVisible();
    await page.goto('/instructor');
    await page.getByRole('button', { name: 'Create course from template', exact: true }).click();
    await expect(page.getByLabel('Course template')).toContainText(templateName);
  });
});

test('unsaved navigation keeps edits and saves before leaving', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/instructor/course-1');
  await page.getByLabel('Course title', { exact: true }).fill('A recovered title');
  await page.getByRole('link', { name: 'My courses', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Unsaved changes' });
  await expect(dialog).toBeVisible();
  await page.waitForTimeout(3200);
  expect(state.saved()).toBeUndefined();
  await dialog.getByRole('button', { name: 'Keep editing' }).click();
  await expect(page.getByLabel('Course title', { exact: true })).toHaveValue('A recovered title');
  await page.getByRole('link', { name: 'My courses', exact: true }).click();
  await dialog.getByRole('button', { name: 'Save and leave' }).click();
  await expect(page).toHaveURL(/\/instructor$/);
  expect(state.saved()?.document.course.title).toBe('A recovered title');
});

test('idle changes are saved automatically with the current revision', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/instructor/course-1');
  await page.getByLabel('Course title', { exact: true }).fill('Automatically saved title');
  await expect
    .poll(() => state.saved()?.document.course.title, { timeout: 7000 })
    .toBe('Automatically saved title');
  expect(state.saved().revision).toBe(1);
  await expect
    .poll(() => page.evaluate(() => sessionStorage.getItem('lms.draft.owner-1.course-1')))
    .toBeNull();
});

for (const stale of [false, true]) {
  test(`browser recovery ${stale ? 'protects a newer server revision' : 'restores an interrupted draft'}`, async ({
    page,
  }) => {
    await setup(page);
    await page.addInitScript(
      ({ course, stale }) => {
        course.title = 'Interrupted browser draft';
        sessionStorage.setItem(
          'lms.draft.owner-1.course-1',
          JSON.stringify({
            savedAt: Date.now(),
            workspace: {
              revision: stale ? 0 : 1,
              status: 'DRAFT',
              checklist: [],
              document: { course, categoryId: 1, quizzes: {}, materials: {} },
            },
          }),
        );
      },
      { course: baseCourse, stale },
    );
    await page.goto('/instructor/course-1');
    const dialog = page.getByRole('dialog', { name: 'Recover your draft' });
    await expect(dialog).toBeVisible();
    if (stale) {
      await expect(dialog.getByRole('button', { name: 'Recover draft', exact: true })).toHaveCount(
        0,
      );
      await expect(
        dialog.getByText('The server version has changed.', { exact: false }),
      ).toBeVisible();
      await dialog.getByRole('button', { name: 'Use server version' }).click();
      await expect(page.getByLabel('Course title', { exact: true })).toHaveValue(baseCourse.title);
    } else {
      await dialog.getByRole('button', { name: 'Recover draft', exact: true }).click();
      await expect(page.getByLabel('Course title', { exact: true })).toHaveValue(
        'Interrupted browser draft',
      );
    }
  });
}
