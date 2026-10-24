# LMS frontend

English-language React 19 + TypeScript application using Vite, React Router, TanStack Query and HLS.js. Spring services are maintained separately in [backend/](../backend/). Product screens use real API data; mock data is confined to tests.

## Start locally

Use Node.js 22.12 or later. From this directory:

```bash
npm ci
cp .env.example .env.local
npm run dev
```

The development server listens on port 5173. Requests to `/api` are proxied to the gateway at port 8080. Backend services and infrastructure must already be running.

| Setting                       | Purpose                                                                                                                   |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `API_TARGET`                  | Development/preview proxy target, default `http://127.0.0.1:8080`.                                                        |
| `VITE_API_BASE_URL`           | Optional API base for a separately hosted gateway; its CORS policy must allow the frontend origin. Applied at build time. |
| `VITE_STRIPE_PUBLISHABLE_KEY` | Existing optional Stripe publishable key. Free enrollment does not require it.                                            |

Never put JWT, database, internal API, SMTP or storage secrets into `VITE_` variables. Browser build variables are public.

## Important workflows

- The catalog shows approved courses. Enabled previews are intentionally public.
- The instructor workspace keeps live content separate from a revision-controlled draft. It autosaves after idle time, protects navigation and can recover an interrupted draft from the same browser tab.
- Select a ready video from the searchable, paginated course media picker, save the draft and submit it for review. Uploading a video to the library alone does not publish a lesson.
- Video uploads use checksummed chunks, pause/resume and cancellation. To resume, select the same file and course on the same browser/device within seven days. File contents and authentication tokens are not stored in upload metadata.
- Protected HLS uses authenticated requests for manifests, variants and segments. A browser with H.264 support is required. Multiple renditions expose Auto and manual quality options.
- Learning paths show completion from enrollments. Course rules explain locked lessons; backend enforcement also protects media, assessments and progress endpoints.
- Multiple answer questions use checkboxes and server grading. Assignment policies support due dates, late submission rules and point-based rubrics.
- Organization managers invite learners before accessing their assigned-course reports. Learners accept or decline invitations on the Organizations page.
- Study reminders are optional and configured on the study plan page.

Access and refresh tokens are stored per tab in `sessionStorage`. Refresh is deduplicated and rotates the refresh token. Signing out clears authenticated query data. Role-aware menus improve navigation; the backend remains responsible for authorization.

## Checks

```bash
npm test
npm run build
npm run format:check
npm run test:e2e
```

Playwright uses the installed Chromium when available. For real H.264 playback, use a browser with the codec, for example `PLAYWRIGHT_BROWSER_CHANNEL=chrome`, or set `PLAYWRIGHT_CHROMIUM_PATH` to a compatible Chrome executable. See [application testing](../docs/APPLICATION_TESTING.md) for real service fixtures and exact commands.
