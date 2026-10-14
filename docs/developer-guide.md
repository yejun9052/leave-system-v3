# 개발자 문서 (초본)

연차관리 시스템을 처음 맡는 개발자가 **구조를 이해하고, 로컬에서 띄우고, 규칙대로 고치는 데** 필요한 내용을 모은 문서다.
초본이라 빠진 곳이 있다. 고치거나 채우면서 맨 아래 "고칠 곳"을 줄여 나간다. 마지막 정리: 2026-10-14.

| 알고 싶은 것 | 볼 문서 |
|---|---|
| 휴가 규칙(연차 계산·결재·취소 등)을 쉬운 말로 | `docs/policy-overview.md` |
| 자동 작업 시각, 세션·로그인 숫자, 실시간 갱신 부하 | `docs/developer-notes.md` |
| 정해진 결정과 이유, 열린 질문(❓) | `docs/decisions/` |
| 운영 설치·백업·복원 | `DEPLOY.md` |
| 보안 설정과 이유 | `SECURITY.md` |
| 서버 끄고 켜기 | `RUN.md` |
| 난독화 jar 만들기 / 라이선스 발급 | `OBFUSCATION.md` / `LICENSING.md` |
| AI 도우미(Claude Code)용 요약 | `CLAUDE.md` |

---

## 1. 한눈에 보기

사내 연차·휴가 관리 웹앱이다.

- **직원**: 휴가를 신청한다.
- **결재**: 팀장·인사관리자가 결재한다.
- **자동 처리**: 연차 부여·이월·소멸, 공휴일, 백업은 자동으로 돈다.

```
브라우저(React SPA) ──HTTPS──▶ Caddy(리버스 프록시) ──▶ Spring Boot(8080, 화면 파일도 같이 제공) ──▶ PostgreSQL 16
                                                          ├─▶ SMTP(메일)
                                                          └─▶ 공공데이터포털 공휴일 API
```

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1(웹 MVC, Security, Data JPA, Validation, Mail, Actuator, AOP), Spring Session JDBC, Flyway, QueryDSL, MapStruct, Apache POI(엑셀), springdoc(Swagger) |
| 프론트엔드 | React 18, TypeScript, Vite 6, TanStack Query 5, zustand, React Router 6, react-hook-form + zod, Tailwind 3 + shadcn/Radix, FullCalendar 6, PWA(vite-plugin-pwa) |
| DB | PostgreSQL 16 |
| 배포 | Docker Compose(`docker-compose.prod.yml`) + Caddy, 또는 jar 하나(`bootJar`가 프론트까지 묶음) |

- 화면 글자, 문서, 커밋 메시지, 테스트 이름은 모두 **한국어**다.
- 서버 오류 메시지도 화면에 그대로 보이므로 직원이 읽을 수 있는 문장으로 쓴다.

---

## 2. 로컬에서 띄우기

**준비물**

- JDK 21, Node.js, Docker(로컬 DB·Testcontainers 테스트)
- 백업 기능까지 쓰려면 PostgreSQL 16 클라이언트(`pg_dump`). 경로는 `BACKUP_PG_BIN` 으로 지정한다.

```powershell
docker compose up -d postgres            # DB: localhost:5432, annual_leave / leave / leave1234
.\gradlew.bat :backend:bootRun           # API 8080 (bootRun 은 local 프로필을 강제)
cd frontend; npm ci; npm run dev         # 화면 5173, /api 는 8080 으로 프록시
```

- **로그인**: `admin` / `admin1234!`(local 프로필 전용 고정 비밀번호)
- **기본 프로필은 prod 다.** jar 를 직접 실행할 때 `SPRING_PROFILES_ACTIVE=local` 을 빼면 운영 설정으로 뜬다.
- **비밀 설정**: 공휴일 API 키, SMTP 비밀번호는 git 에 없는 `backend/application-secret.yml` 에 둔다. 없어도 앱은 뜬다. 공휴일 동기화는 건너뛰고 메일은 실패 로그만 남는다.
- **라이선스**: 유효한 라이선스 키가 없으면 모든 API가 막힌다. local 프로필에는 localhost 용 개발 키가 들어 있다.
- **한글 경로**: 프로젝트 경로에 한글이 있으면 Gradle 테스트가 `ClassNotFoundException` 으로 실패한다. 영문 경로 junction 을 쓴다(README "한글 경로 주의").

**확인용 서버를 하나 더 띄울 때**: 내 8080·5173 은 그대로 두고, 복사본 DB로 띄운다.

1. 복사본 DB를 만든다.
   ```
   docker exec annual-leave-db sh -c "dropdb -U leave --if-exists annual_leave_verify; createdb -U leave annual_leave_verify && pg_dump -U leave annual_leave | psql -q -U leave annual_leave_verify"
   ```
2. 백엔드를 띄운다: `SERVER_PORT=18080 DB_URL=jdbc:postgresql://localhost:5432/annual_leave_verify CORS_ORIGINS=http://localhost:5181 ./gradlew :backend:bootRun`
   - 메일이 실제로 나가지 않게 `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025` 도 준다.
3. 화면을 띄운다: `vite.config.ts` 를 복사해 5173→5181, 8080→18080 으로 바꾼 설정으로 Vite 를 띄운다. 이 설정 파일은 커밋하지 않는다.
4. 끝나면 두 서버를 끄고 `dropdb annual_leave_verify` 로 복사본을 지운다.

---

## 3. 저장소 구조

```
backend/   Spring Boot. src/main/java/com/company/leave/<도메인>/
frontend/  React. src/
docs/      규칙 요약·개발자 노트·결정 기록
scripts/   스모크 테스트, 배포 패키지
```

**백엔드 패키지(도메인별)**

| 패키지 | 하는 일 |
|---|---|
| `auth` | 로그인·로그아웃·자동 로그아웃, 로그인 시도 제한, 비밀번호 재설정·규칙(`password/`), 세션 끊기(`SessionTerminator`) |
| `security` | 세션 사용자(`UserPrincipal`), 요청마다 재직·역할 재확인(`AccountStateFilter`), `SecurityUtils` |
| `employee`, `department` | 직원·부서. 팀장 역할과 부서장 자동 맞춤(`DepartmentLeadSync`), 엑셀 가져오기·내보내기 |
| `leave` | 휴가 신청·결재·취소·강제 등록(`LeaveRequestService`), 연차 부여·이월(`LeaveGrantService`), 잔액 차감·환원(`LeaveCharges`), 연차 촉진, 공휴일 변경 반영(`HolidayImpactService`) |
| `leave/accrual` | 계산기: 연차 일수(`LeaveAccrualCalculator`), 연차 기간(`LeavePeriodCalculator`), 근무일(`WorkdayCalculator`) |
| `policy` | 정책 한 줄(`leave_policy`), 경조사·공가 규칙, 금지 기간(블랙아웃) |
| `calendar` | 달력 일정, 공휴일(`holiday/`)과 공휴일 API 동기화 |
| `notification`, `mail` | 화면 알림, 메일(커밋 후 비동기 발송, 공통 틀 `MailLayout`) |
| `realtime` | SSE 실시간 갱신 |
| `batch` | 스케줄러, 실행 기록(`JobRunRecorder`), 자동화 탭(`AutomationService`) |
| `backup` | 백업·복원(`pg_dump`/`pg_restore`), 자동 백업 예약, 복원 중 점검 모드 |
| `dashboard`, `report` | 대시보드·리포트·엑셀 |
| `audit` | 이벤트 로그(`AuditAspect` 가 자동 기록, 화면 이름표 `AuditLabels`) |
| `license` | 라이선스 검증·집행 |
| `config`, `common` | 보안 설정, 시드 데이터(`config/init`), 공통 응답·예외 |

**프론트엔드(`frontend/src`)**

| 위치 | 하는 일 |
|---|---|
| `api/*.ts` | 도메인별 API 함수. `unwrap()` 이 `ApiResponse.data` 만 꺼낸다. `client.ts` 가 CSRF·401·점검 중(503)을 처리 |
| `features/<도메인>/` | 화면과 대화상자 |
| `layouts/AppLayout.tsx` | 사이드바·상단 바·알림·실시간 연결·자동 로그아웃 |
| `components/ui/` | shadcn/Radix 기본 부품 |
| `lib/` | 공용 훅·포맷(`leaveFormat.ts` 일수·반차 표시, `useServerForm`, `useUnsavedChanges`, `useRealtime`, `useIdleLogout`) |
| `store/` | zustand: 로그인 사용자, 점검 중 표시, 저장 안 한 입력 |
| `types/index.ts` | 백엔드 DTO 와 같은 모양의 타입. **백엔드 DTO 를 바꾸면 같이 바꾼다** |

---

## 4. 백엔드 공통 규칙

- **응답**: 모든 API 는 `ApiResponse`(`{success, data, error}`)를 돌려준다.
- **실패**: `BusinessException(ErrorCode, 메시지)` 를 던진다.
  - `GlobalExceptionHandler` 가 `ErrorCode` 에 정한 HTTP 상태로 바꾼다.
  - 메시지는 화면에 그대로 보이니 한국어 문장으로 쓴다.
- **권한**: 컨트롤러·메서드마다 `@PreAuthorize` 로 건다.
  - 역할은 한 사람에 하나(`employees.role`)이고, 높은 역할이 낮은 역할을 포함한다: `SYSTEM_ADMIN > HR_ADMIN > TEAM_LEAD > EMPLOYEE`.
  - `SYSTEM_ADMIN` 은 `admin` 관리 전용 계정 하나뿐이고, 결재에서는 인사관리자와 같다.
  - 컨트롤러에서 로그인한 사람은 `SecurityUtils.currentEmployeeId()` 로 얻는다.
- **이벤트 로그**: `/api/**` 의 POST/PUT/PATCH/DELETE 와 `.../export` GET 은 자동으로 기록된다. 새 API를 만들면 `AuditLabels` 에 화면 이름표를 단다.
- **실시간 갱신**: 데이터를 바꾸는 요청이 성공하면 자동으로 "바뀜" 신호가 나간다. 새 API는 할 일이 없다.
  - 요청 밖(예약 작업 등)에서 데이터를 바꾸면 `RealtimeHub.broadcast(null)` 을 부른다.
  - 예약 작업은 `JobRunRecorder.run` 으로 감싸면 신호가 자동으로 나간다.
- **조회**:
  - 복잡한 조회는 QueryDSL(Q 클래스는 빌드가 만든다. 직접 고치지 않는다).
  - DTO 변환에는 MapStruct 를 쓴다.
  - `open-in-view` 는 꺼져 있어 지연 로딩은 서비스 안에서 끝낸다.
- **메일**: 서비스가 이벤트를 발행하면 `mail/*MailService` 가 커밋 뒤(`@TransactionalEventListener`) 비동기로 보낸다. 메일이 실패해도 요청은 성공한다.

---

## 5. 휴가 도메인 핵심 흐름

### 5.1 신청 상태

```
신청 ──▶ PENDING(결재 대기) ──승인──▶ APPROVED ──취소 요청──▶ CANCEL_REQUESTED ──승인──▶ CANCELLED
              │                         │                         └──반려──▶ APPROVED
              ├──반려──▶ REJECTED        └──인사관리자 강제 취소──▶ CANCELLED
              └──본인 철회·인사관리자 취소──▶ CANCELLED
```

- **결재는 한 번이다.** 결재할 수 있는 사람 한 명이 승인하면 바로 확정된다.
- **잔액**: 승인할 때 차감하고, 취소가 확정되면 환원한다. 결재 대기 중인 일수는 "예약"으로 잡혀 신청 가능 일수에서 빠진다.
- **취소**:
  - 승인된 본인 휴가는 시작 전까지만 취소 요청할 수 있다.
  - 인사관리자는 다른 직원의 승인된 휴가를 사유를 적고 시작 전후 상관없이 강제 취소할 수 있다.
- **강제 등록**: 인사관리자가 직원 대신 휴가를 등록한다(`register`). 사용 통제(금지 기간 등)는 적용하지 않는다.
- **동시성**: 같은 날을 두 번 신청하거나 동시에 승인해도 이중 차감되지 않게 승인 때 다시 검사하고, `@Version` 으로 막는다.

### 5.2 누가 결재하나

- **팀장의 결재 대상**: 신청자의 소속 부서부터 위로 올라가며 처음 만나는 재직 중인 부서장이다. 본인과 관리 전용 계정은 뺀다.
  - 팀장 본인의 신청이면 자기 부서를 건너뛰어 상위 부서장이 결재한다.
- **인사관리자·시스템 관리자**: 모든 신청을 결재할 수 있다.
- **자가 승인**: 인사관리자, 그리고 위에 결재할 팀장이 없는 최상위 부서장은 본인 신청을 직접 승인할 수 있다. 이벤트 로그에 따로 남는다.
- **팀장이 없는 부서**: 인사관리자 전원에게 알림이 간다.

### 5.3 검증은 한 곳(`plan`)에서

- 신청 화면의 미리보기(`GET /leave-requests/eligibility`)와 실제 신청이 **같은 `plan(...)` 을 지난다.** 규칙을 바꿀 때는 여기만 고치면 둘이 어긋나지 않는다.
- **같은 날 겹침 규칙**:
  - 취소 요청 중인 휴가가 있으면 그날은 모두 막는다.
  - 반차는 오전+오후만 함께 쓸 수 있다.
  - 반차와 시간차는 함께 쓸 수 없다.
  - 시간차는 합계 1일까지다.

### 5.4 연차 계산

- **잔액**: `remaining = granted + carriedOver − used − expired`
- **연차 기간**: 입사일 기념일 또는 회계연도 기준(정책). 기간 경계에 걸친 휴가는 다음 기간 몫을 따로 적는다(`next_period_deducted_days`, `LeaveCharges`).
- **부여와 이월**: 매일 01:00 배치(`LeaveGrantService`)가 지금 기간을 부여·재계산한다. 새 기간이 시작되면 지난 기간 잔여를 정책에 따라 이월·소멸한다.
- **당겨쓴 연차**(음수 잔여)는 정책과 상관없이 새 기간 이월에 **음수로** 넣어 갚는다(2026-10-14).
- **새 직원**: 만들면 커밋 뒤 그 해 연차를 바로 부여한다(`EmployeeLeaveInitListener`).
- **공휴일이 새로 생기면** 걸친 휴가의 차감 일수를 다시 계산한다. 근무일이 0이 되면 자동 취소한다(`HolidayImpactService`).

### 5.5 금지 기간(블랙아웃)

- 저장하면 겹치는 휴가를 정책(`BlackoutConflictMode`)에 따라 자동 반려하거나 취소한다.
- 반려·취소된 사람에게는 알림과 메일이 간다.

---

## 6. 프론트엔드 규칙

- **서버 데이터**:
  - 서버 데이터는 TanStack Query 로 다룬다.
  - 저장한 뒤에는 관련 키를 무효화한다(예: `["dashboard"]`, `["notifications"]`, `["leavePreview"]`).
  - 다른 사람이 바꾼 것은 `useRealtime` 이 알아서 새로 불러온다.
- **입력 폼**:
  - 서버 데이터를 입력 칸에 복사하는 화면은 `useServerForm` 을 쓴다. 다른 곳에서 바뀌면 입력을 지우지 않고 "다른 곳에서 바뀜" 안내를 띄운다.
  - 이런 화면은 `useUnsavedChanges` 도 불러, 나갈 때 "저장 / 저장 안 함 / 취소"를 묻게 한다.
  - `useEffect` 로 쿼리 데이터를 폼에 복사하지 않는다.
- **권한에 따른 화면**: `roleIncludes`(types)와 `useAuthStore().hasAnyRole` 로 판단한다.
- **표시 문구**: 일수·반차·시간차는 `lib/leaveFormat.ts` 의 함수를 쓴다. 화면마다 직접 만들지 않는다.
- **대화상자**: `components/ui/dialog.tsx` 를 쓴다. 대화상자 안의 Select 를 눌러도 닫히지 않게 처리돼 있다.
- **모바일**: 360px 폭에서 가로 스크롤이 생기지 않게 확인한다. 달력은 모바일 전용 화면(`MobileMonthView`)이 따로 있다.
- **검사**: 프론트 테스트 러너와 ESLint 설정은 없다. `npm run build`(= `tsc && vite build`)가 타입 검사다.

---

## 7. DB 와 마이그레이션

- **Flyway**(`backend/src/main/resources/db/migration`):
  - 개발 중 쌓인 것을 2026-10-06 `V1__baseline.sql` 로 합쳤고, 지금은 V14 까지 있다.
  - 새 변경은 `V{다음 번호}__설명.sql` 로 추가한다.
  - **이미 적용된 마이그레이션은 고치지 않는다.**
- **엔티티 검사**: `ddl-auto: validate` 라 엔티티와 테이블이 다르면 앱이 뜨지 않는다.
- **시드 데이터**: 관리자, 기본 정책, 휴가 종류, 공휴일 대체 데이터는 마이그레이션이 아니라 앱 시작 때 `config/init/*Initializer` 가 넣는다.
- **복원**: `public` 스키마를 지우고 다시 만든 뒤 Flyway 를 돌린다. 진행 중에는 모든 API가 503(`MAINTENANCE`)이고 예약 작업도 쉰다.
- **스키마 변경과 의존성 추가**는 먼저 합의한 뒤 한다.

---

## 8. 테스트

```powershell
.\gradlew.bat :backend:test                                              # 전체(Docker 필요: Testcontainers)
.\gradlew.bat :backend:test --tests "*LeaveRequestServiceSickAndPartialTest*"   # 한 클래스
```

- 테스트 파일은 약 100개다.
- **단위 테스트**:
  - JUnit 5 + AssertJ + Mockito.
  - 메서드 이름은 한국어(`@DisplayNameGeneration(ReplaceUnderscores)`). 예: `void 취소_요청_중인_반차가_있는_날에는_반차_시간차도_신청할_수_없다()`
- **권한 테스트**(`*ControllerAccessTest`):
  - DB 없이 `@EnableMethodSecurity` 컨텍스트에 컨트롤러와 목 서비스를 올린다.
  - `UserPrincipal` 을 넣어 역할별로 호출해 본다.
- **실제 DB 테스트**: `RestoreIntegrationTest` 등은 Testcontainers PostgreSQL 을 쓴다.
- **휴가 계산을 바꾸면** 기념일, 회계연도 경계, 이월, 취소, 동시 상태 변경에 대한 회귀 테스트를 꼭 넣는다.
- **스모크 테스트**: `scripts/smoke-test.ps1` 은 떠 있는 앱 전체를 훑는다(로컬·테스트 DB 전용, 뒤처리는 `smoke-cleanup.ps1`).
- **화면 확인**: 2장의 확인용 서버에서 헤드리스 브라우저로 확인한다. 실제 직원 데이터나 내 DB의 계정은 건드리지 않는다.

---

## 9. 설정(환경 변수)

| 변수 | 뜻 | 기본 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` / `prod` | prod |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | DB 접속 | local 은 docker compose 값 |
| `SESSION_TIMEOUT` | 자동 로그아웃(미사용 만료) | `10m` |
| `SESSION_COOKIE_SECURE` | 세션 쿠키 Secure | `true`(local 은 끔) |
| `CORS_ORIGINS` | 허용할 화면 주소 | `http://localhost:5173,…` |
| `APP_ORIGIN` | 메일 속 링크의 기준 주소 | `http://localhost:5173` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` | 메일 서버 | |
| `HOLIDAY_API_KEY` | 공휴일 API 키 | 없으면 동기화 건너뜀 |
| `BACKUP_DIR`, `BACKUP_PG_BIN` | 백업 폴더, `pg_dump` 위치 | |
| `LICENSE_KEY`, `LICENSE_HOST` | 라이선스 | 운영 필수 |

운영 값은 `DEPLOY.md` 를 따른다. 비밀 값이 든 `.env*` 와 `application-secret.yml` 은 git 에 넣지 않는다.

---

## 10. 배포 요약

- **jar 만들기**:
  - `.\gradlew.bat :backend:bootJar` 는 `npm run build` 결과를 jar 에 함께 묶는다. 서버 하나로 화면과 API를 같이 낸다.
  - 고객사 배포용은 `bootJarObf`(난독화 + 라이선스)로 만들고 반드시 실행 확인 뒤 배포한다(`OBFUSCATION.md`).
- **운영**: Docker Compose + Caddy(HTTPS). 설치·백업·복원 절차는 `DEPLOY.md`.

---

## 11. 작업 방식

- **브랜치**: `main` 이 기준이다. 실험 작업은 별도 브랜치(worktree)에서 하고 검토 뒤 합친다.
- **커밋**:
  - 기능별로 묶고, 메시지는 한국어로 쓴다.
  - `push` 는 사람이 직접 한다.
- **같이 고칠 문서**:
  - 규칙을 바꾸면 `docs/policy-overview.md`
  - 시각·숫자를 바꾸면 `docs/developer-notes.md`
  - 결정을 내리면 `docs/decisions/YYYY-MM-DD-주제.md`
- **커밋하지 않는 것**: `backend/application-secret.yml`, `.env*`(예시 파일 포함), 덤프(`*.dump`), 실제 직원 데이터.
- **Vite 주의**: 셸(`sed -i` 등)로 파일을 고치면 실행 중인 Vite 가 반쯤 고친 내용을 들고 있을 수 있다. 파일을 다시 저장하고 `curl http://localhost:5173/src/...` 로 확인한다.

---

## 12. 열린 항목

- ❓ 연속 사용 한도: 나눠 신청한 연속 휴가를 합칠지, 영업일과 달력일 중 무엇으로 셀지(회사 확인 중)
- ❓ 병가와 미래 연차 빌려 쓰기(인사관리자와 논의 필요)
- ❓ 마이너스 연차 한도("최대 N일")는 보류. 하려면 정책 칸과 마이그레이션이 필요하다.
- ❓ Google Calendar 연동 방식 미정
- 운영 보안 항목 보류: HTTP 평문 운영, DB 슈퍼유저, 관리자 초기 비밀번호 등(`docs/decisions`, `SECURITY.md`)

## 고칠 곳 (초본)

- [ ] API 목록(주요 엔드포인트와 권한)을 표로. 지금은 Swagger(`/swagger-ui`, local 만)로 본다.
- [ ] 화면별로 어떤 API·쿼리 키를 쓰는지
- [ ] ERD(주요 테이블 관계)
- [ ] 알림 `type`·`link` 목록과 화면 동작
- [ ] 메일 종류와 받는 사람 표
