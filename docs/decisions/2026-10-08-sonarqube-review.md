# SonarQube 검토 결과

- 작성: 2026-10-08
- 기준 코드: `main` `2bbe221`
- 상태: 세 번에 나눠 약 250건 검토. **보안 문제·실제 동작 버그 없음.** "나중에 고칠 것" 4건은 모두 고침(`5bf1b25`)
  - 1차 7건(보안·신뢰성 중심): 이 문서 "검토 결과"
  - 2차 35건(접근성·시간대 등): "2차 검토"
  - 3차 약 200건(유지보수성·스타일): "3차 검토"

---

## 검토 결과

| # | 위치 | SonarQube 지적 | 판정 | SonarQube 처리 |
|---|---|---|---|---|
| 1 | `backend/.../audit/AuditLabels.java:36` | 하드코딩 비밀번호 (Security, Medium) | **오탐.** `ACTIONS.put("password", "비밀번호 변경")`은 이벤트 로그 동작 이름표일 뿐 비밀번호 값이 아님 | False Positive |
| 2 | `backend/.../config/SecurityConfig.java:116` | HttpOnly 없는 쿠키 (Security, Low) | **의도된 설정, 안전.** CSRF 토큰 쿠키(`XSRF-TOKEN`)는 화면(JS)이 읽어 `X-XSRF-TOKEN` 헤더로 보내야 해서 HttpOnly 를 끔(Spring Security SPA 권장 방식). 로그인 세션 쿠키는 HttpOnly 켜짐 | Safe |
| 3 | `backend/src/main/resources/application-local.yml:24` | 쿠키 Secure 꺼짐 (Security, Low) | **로컬 전용, 안전.** 로컬은 `http://localhost` 라 끔. 운영은 `application.yml`·`docker-compose.prod.yml` 기본값 `true` | Safe |
| 4·5 | `backend/.../leave/accrual/WorkdayCalculator.java:93` (2건) | value-based 타입을 `==` 로 비교 (Reliability, High) | **오탐.** `DayOfWeek` 는 enum 이라 `==` 비교가 정확하고 권장 방식. 비교가 2개라 2건 | False Positive |
| 6 | `backend/.../security/SessionTolerantSecurityContextRepository.java:93` | `ex` 가 null 일 수 있음 (Reliability, 중간) | **오탐.** `ex` 는 `catch` 로 잡힌 예외라 null 일 수 없음. 아래 `isDeserializationFailure` 의 `t != null` 검사를 보고 잘못 추측함 | False Positive |
| 7 | `frontend/src/features/department/DepartmentPage.tsx:294` | 클릭되는 `<div>` (접근성, 신뢰성 중간) | **맞는 지적.** 아래 "나중에 고칠 것" | 고칠 때까지 Open |

## 나중에 고칠 것

### 부서 관리: 부서 줄을 키보드로 열 수 없음 (#7)
- 부서 줄을 누르면 명단이 열리는데, 그 줄이 버튼이 아니라 `<div onClick>` 이다.
- 마우스·터치는 정상. **키보드만 쓰는 사용자는 Tab 으로 갈 수 없고 Enter 로 열 수 없으며, 화면 읽기 프로그램도 누를 수 있는 것으로 알려 주지 않는다.**
- 보안·데이터 문제는 아니다. 사내 관리자 화면이라 우선순위 낮음.
- 고치는 방법: 부서 **이름 부분만** `<button>` 으로 바꾼다(줄 안에 펼치기·수정·삭제 버튼과 끌기 손잡이가 있어 줄 전체를 버튼으로 만들면 안 됨). 이 파일 한 곳.

## 참고: 쿠키 Secure 설정 (#3 관련)
- 내부망을 HTTP 로 운영(DEPLOY.md 모드 B)하면 `.env` 에 `SESSION_COOKIE_SECURE=false` 를 **반드시** 넣어야 로그인된다.
- HTTPS 로 옮길 때는 이 줄을 지워야 한다(남아 있으면 세션 쿠키가 Secure 없이 운영됨).
- "접속 방식(HTTP/HTTPS)을 보고 자동으로 정하기"로 바꾸는 방법도 검토했다. 동작·보안은 같고 설정 실수만 줄여 주는 변경이라 **지금은 하지 않기로** 했다. 실제 배포 때 설정 실수가 생기면 그때 바꾼다.
- HTTPS 로 운영하려면 내부망에서는 Let's Encrypt 자동 발급(모드 A)을 쓸 수 없다. 사내 인증기관(CA) 인증서, Caddy 자체 인증서(`tls internal`), 회사 공인 도메인 + DNS 인증 중 하나가 필요하다. 회사 IT 확인 필요.

---

## 2차 검토 (35건)

| 묶음 | 위치 | 건수 | 판정 |
|---|---|---|---|
| `.now()` 에 시간대 지정 없음 (정보 등급) | `HolidaySyncService`·`LeaveRequestService`·`LeavePeriodCalculator`·`DashboardService`·`ReportController` 등 | 22 | **지금은 안전.** 서버 시간대가 Docker 설정(`Dockerfile` `ENV TZ=Asia/Seoul`, compose `TZ: Asia/Seoul`)으로 한국 시간. Docker 없이 UTC 서버에서 띄우면 한국 새벽 0~9시에 "오늘"이 하루 전이 됨(금지 기간·연차 기간 경계·부여 배치·리포트 기준일) |
| 클릭되는 `<div>` / 키보드 대응 없음 | `DepartmentPage.tsx:294` | 1+ | **맞는 지적.** 1차 #7 과 같은 줄 |
| 〃 | `PolicyPage.tsx:552` | 2 | **작은 지적.** 휴가 종류 추가·수정 창의 바깥 배경 클릭으로 닫기. "취소" 버튼으로 닫을 수 있지만 이 창만 공용 창 부품을 안 써서 Esc 로 닫히지 않음 |
| 〃 | `AppLayout.tsx:116` | 2 | **사실상 문제없음.** 모바일 메뉴 바깥 배경 클릭으로 닫기. 키보드용 닫기(X) 버튼 있음 |
| 라벨에 글자 없음 | `PolicyPage.tsx:304` | 1 | **오탐.** 금지 기간 처리 방식 라디오에 글자가 있지만 변수로 들어가 SonarQube 가 못 읽음 |
| `i + 1` 을 소수로 바꿔 더하라 | `LeaveReportService.java:162·191·352` | 3 | **오탐.** 엑셀 번호(많아야 수백)라 넘칠 일 없음 |
| `charCodeAt` → `codePointAt` | `LeaveRequestForm.tsx:40` | 1 | **오탐에 가까움.** 받침 판정(은/는·을/를). 한글은 결과가 같고 특수 문자도 정상 처리 |
| 간격이 모호함 | `LeaveRequestForm.tsx:381` | 1 | **문제없음.** 체크박스와 글자 사이 간격은 `gap-2` 로 처리됨 |
| `volatile` 로 충분하지 않음 | `LicenseService.java:37` | 1 | **무시.** 라이선스는 무시하기로 함(코드상으로도 통째로 바뀌는 값이라 안전) |

## 3차 검토 (약 200건, 유지보수성·스타일)

### 동작에 영향이 있을 수 있어 코드로 확인한 것

| 지적 | 건수 | 판정 |
|---|---|---|
| this 로 트랜잭션 메서드 호출 (`EmployeeService`·`DepartmentService`·`LeavePromotionService`·`LeaveRequestService`·`LeaveTypeService`·`PolicyService`·`LeaveBalanceService`·`AutomationService`·`LeaveReportService`) | 약 30 | **문제 없음.** 바깥 메서드(`update`·`move`·`send`·`updatePromotion` 등)에 이미 트랜잭션이 있어 안쪽 호출도 같은 트랜잭션에서 돈다. 호출 위치마다 확인함 |
| 빈 목록 대신 null 반환 (`EmployeeService:94`) | 1 | **의도된 것.** null = 제한 없음(관리자), 빈 목록 = 볼 수 있는 부서 없음 |
| 변수 이름이 필드와 같음 (`LeaveRequest:190`) | 1 | **문제 없음.** 지역 변수 이름일 뿐 |
| 부모 안에 컴포넌트 정의 (`AppLayout:69·93` 메뉴·로고) | 2 | **사소한 성능.** 다시 그릴 때마다 새로 만들어짐. 상태가 없어 오동작 없음 |
| 목록 key 에 순번 (`MobileMonthView:255`, `DashboardPage:136`, `PolicyPage:874`) | 3 | **문제 없음.** 순서가 바뀌지 않는 장식용 목록 |
| 알림(toast) 값이 매번 새로 만들어짐 (`toast.tsx:43`) | 1 | **사소한 성능.** 체감 차이 없음 |
| 로그 인자를 미리 계산 (`HolidayApiClient:67`, `HolidaySyncService:81`) | 2 | **사소한 성능.** 하루 한 번 도는 동기화 |
| 사용 안 하는 필드·주석 코드 (`LeaveGrantService:36·118`) | 2 | **의도된 것.** 근속 포상 자동 가산 "일시 중지(2026-09-30)" 로 남겨 둠 |
| `LicenseTool` (`System.out`, 일반 예외) | 9 | **무시.** 라이선스 발급용 명령줄 도구, 라이선스는 무시 |
| 접근성: `role="dialog"` (`LeaveEntryPanel:120`), 끌기 손잡이 `role="button"` (`DepartmentPage:310`) | 2 | **작은 지적.** 마우스·터치 정상 |

### 스타일 지적 (동작과 무관)

| 지적 | 대략 건수 |
|---|---|
| 컴포넌트 props 를 읽기 전용으로 표시 | 40 |
| 중첩된 삼항 연산자 | 35 |
| 메서드·생성자 인자가 7개 넘음 | 14 |
| 같은 문자열 반복(`"/my-leaves"`, `"CONDOLENCE"` 등) | 13 |
| 인지 복잡도 15 초과(16~23) | 12 |
| 템플릿 문자열 중첩, optional chain, `dataset`, `void`, break/continue 개수, dangling Javadoc 등 | 15 |
| 이름이 `record`(제한된 식별자) | 3 |

→ 한꺼번에 고치면 바뀌는 곳만 많고 얻는 게 적다. **그 파일을 다른 일로 고칠 때 같이 정리한다.**

---

## 나중에 고칠 것 (전체)

4건 모두 고쳤다(2026-10-08, `5bf1b25`). 화면에서 Tab·Enter, Esc, 메뉴 이동을 확인했다.

| 우선 | 항목 | 위치 | 비고 | 상태 |
|---|---|---|---|---|
| 낮음 | 부서 줄 키보드로 열기(이름 부분만 `<button>`) | `DepartmentPage.tsx:294` | 1차 #7 | ✅ 완료 |
| 낮음 | 휴가 종류 추가·수정 창을 공용 창 부품으로(Esc 로 닫기) | `PolicyPage.tsx:552` | 2차 | ✅ 완료 |
| 선택 | 앱 시작 때 기본 시간대를 한국으로 고정(한 줄, 어디서 띄워도 안전) | `LeaveManagementApplication` | 2차 `.now()` 22건 대응 | ✅ 완료 |
| 선택 | 메뉴·로고 컴포넌트를 바깥으로 | `AppLayout.tsx:69·93` | 3차 | ✅ 완료 |
