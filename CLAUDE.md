# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Internal annual-leave (연차) management system: Spring Boot 4.1 / Java 21 backend + React 18 / TypeScript / Vite frontend, PostgreSQL 16. The UI, docs, commit messages and test names are Korean. `C:\projects\AGENTS.md` (parent folder) also applies.

## Commands

Run from the repo root on Windows (use `./gradlew` on Unix).

```powershell
docker compose up -d postgres                 # local DB: localhost:5432, annual_leave / leave / leave1234
.\gradlew.bat :backend:bootRun                 # API on 8080; bootRun forces --spring.profiles.active=local
.\gradlew.bat :backend:test                    # all backend tests (Testcontainers ones need Docker)
.\gradlew.bat :backend:test --tests "*LeaveRequestServiceSickAndPartialTest*"   # one class
.\gradlew.bat :backend:bootJar                 # runs `npm run build` and bundles frontend/dist into the jar
cd frontend; npm ci; npm run dev               # Vite on 5173, proxies /api → 8080
cd frontend; npm run build                     # `tsc && vite build`; this is the frontend type check
```

- There is no frontend test runner, and no ESLint config even though `npm run lint` exists.
- `scripts/smoke-test.ps1` / `smoke-cleanup.ps1` run an end-to-end smoke test against a running app. Use them only on local or test DBs.
- **Default profile is `prod`** (`application.yml`). Running the jar directly without `SPRING_PROFILES_ACTIVE=local` uses the prod config.
  - Local secrets (holiday API key, SMTP) live in the gitignored `backend/application-secret.yml`.
  - The local admin login is `admin` / `admin1234!`.
- To run a second backend next to the user's 8080 for verification, use `SERVER_PORT=18080` and set `CORS_ORIGINS` to include the extra Vite origin.
- Shell edits can leave the user's long-running Vite (5173) on stale code. This happens with `sed -i`, `mv`, or several quick edits to one file. Re-save the file afterwards, then check what is actually served with `curl http://localhost:5173/src/...`.

## Architecture

### Backend (`backend/src/main/java/com/company/leave/`, packaged by domain)

**Request/response conventions**
- Every endpoint returns `common/dto/ApiResponse` (`{success, data, error}`).
- Failures throw `BusinessException(ErrorCode, message?)`. `GlobalExceptionHandler` maps them to the HTTP status in `ErrorCode`.
- The frontend shows `error.message` to users, so messages are user-facing Korean sentences.

**Security**
- Server sessions are stored in the DB via Spring Session JDBC (`spring_session` tables), not JWT.
- The CSRF cookie is `XSRF-TOKEN`.
- Authorization uses `@PreAuthorize` per controller or method. Roles: `SYSTEM_ADMIN`, `HR_ADMIN`, `TEAM_LEAD`, `EMPLOYEE`.
  - Roles do not inherit; each role is granted explicitly.
  - `SYSTEM_ADMIN` belongs only to the `admin` system account (`system_account`). For leave approval it acts like HR.
  - The admin can take leave like an employee, but is excluded from report Excel, the user Excel export and license counts.
  - The README's `SUPER_ADMIN` is outdated.
- Use `SecurityUtils.currentEmployeeId()` in controllers.

**Audit log**
- `audit/AuditAspect` records every POST/PUT/PATCH/DELETE under `/api/**`, plus GET `.../export`.
- `AuditLabels` maps actions and entities to the Korean labels shown in the event-log screen. Add labels when you add endpoints.

**Leave domain (`leave/`)**
- `LeaveRequestService` is the core: apply, approve, reject, cancel, HR force-register, blackout handling.
- `plan(...)` is the single validation path. Both the preview (`GET /leave-requests/eligibility`) and the real submit go through it. Change rules there so the preview and submit stay identical.
  - Same-day overlap rules: cancel-requested leave blocks everything; half days AM+PM only; half + hourly not allowed; hourly total ≤ 1 day.
- `accrual/`:
  - `LeaveAccrualCalculator` computes the entitlement.
  - `LeavePeriodCalculator` handles leave periods: hire-date or fiscal-year anniversaries, and leave that spans an anniversary (`nextPeriodDeduction`).
  - `WorkdayCalculator` computes days (weekends and holidays excluded) and deductions.
- `LeaveCharges` charges and restores balances per period. A leave can split between `appliedYear` and `appliedYear + 1` (`next_period_deducted_days`).
- `LeaveMessenger` creates in-app notifications (`notification/`). Mail is sent after commit via events and `@TransactionalEventListener` in `mail/*MailService`.
  - Notification `type` and `link` drive the frontend bell (action button, title color).

**Policy (`policy/`)**
- One `leave_policy` row.
- Special leave rules (경조사, e.g. the birthday half day with an annual limit).
- Blackout periods. Saving one auto-rejects or cancels overlapping leave depending on `BlackoutConflictMode`.

**Scheduled jobs (`batch/`, `backup/`)**
- Jobs: holiday sync 00:10, accrual 01:00, promotion 09:00, and auto backup.
- Auto backup is registered dynamically on `TaskScheduler` from `backup_settings`; it is not a fixed `@Scheduled` job.
- Every job records runs through `JobRunRecorder` and must appear in `AutomationService`, which is the 자동화 tab.

**Backup and restore (`backup/`)**
- Uses `pg_dump` / `pg_restore` / `psql`. The tools come from `app.backup.pg-bin`.
- Restore drops and recreates the `public` schema, then runs Flyway.
- `MaintenanceFilter` returns 503 `MAINTENANCE` for `/api/**` while a restore runs. `MaintenanceSchedulingAspect` skips scheduled jobs during that time.

**Startup**
- Seed data (admin, policy, leave types, holidays) is inserted by `config/init/*Initializer` (ApplicationRunner), not by migrations.

**Database migrations**
- Flyway lives in `resources/db/migration`. History was squashed into `V1__baseline.sql`; there is no production DB yet. Add new `V{n}__*.sql` files.
- Never edit an applied migration.
- `ddl-auto: validate` stays on.
- QueryDSL Q-classes are generated; don't edit them.

### Frontend (`frontend/src/`)

- `api/*.ts`: one axios client per domain. Each call is wrapped in `unwrap()` to return `data`.
  - `api/client.ts` handles CSRF, sends a lost session (401) to `/login`, and turns a 503 `MAINTENANCE` into a full-screen overlay (`store/maintenance.ts`).
- `features/<domain>/`: pages and dialogs. `layouts/AppLayout.tsx` holds the sidebar, header, notification bell and unread notice. `components/ui/` holds shadcn/Radix primitives.
- State:
  - TanStack Query handles server state. After mutations, invalidate the related keys, e.g. `["dashboard"]`, `["notifications"]`, `["leavePreview"]`.
  - zustand `store/auth.ts` holds the user and `hasAnyRole`.
- Shared Korean formatting lives in `lib/leaveFormat.ts` (days, half-day labels, period split).
- `types/index.ts` mirrors backend DTOs. Update both sides together.

## Testing conventions

- **Unit tests**:
  - JUnit 5 + AssertJ + Mockito.
  - Korean method names with `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` and a Korean `@DisplayName`.
  - Example: `void 취소_요청_중인_반차가_있는_날에는_반차_시간차도_신청할_수_없다()`.
- **Authorization tests** (`*ControllerAccessTest`) use no DB:
  - Build an `AnnotationConfigApplicationContext` with `@EnableMethodSecurity`, register the controller and its mocked services, and drive it with `MockMvcBuilders.standaloneSetup`.
  - Authenticate by putting a `UserPrincipal` in `SecurityContextHolder`.
- **Real-DB tests** (e.g. `RestoreIntegrationTest`) use Testcontainers PostgreSQL.
- Leave-calculation changes need regression tests for anniversaries, fiscal boundaries, carryover, cancellation and concurrent state transitions.

## Docs to keep in sync

- `docs/policy-overview.md`: plain-language Korean summary of every leave rule. Update it when rules change.
- `docs/decisions/YYYY-MM-DD-topic.md`: decision records (❓ marks open items).
- `DEPLOY.md`: production install, backup and restore. Also `SECURITY.md`, `README.md`, `RUN.md`.
- `SESSION.md`, if present, is a temporary hand-off note for continuing work.

## Repo rules

- Never commit `backend/application-secret.yml`, `.env*` files (including `.env.prod.example`), dumps (`*.dump`), or real employee data.
- Commits: grouped by feature, Korean message, ending with the `Co-Authored-By` line. The user runs `git push` themselves.
- Ask before dependency changes, new DB migrations, or anything touching shared or production state.
