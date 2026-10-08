# SonarQube 검토 결과

- 작성: 2026-10-08
- 기준 코드: `main` `2bbe221`
- 상태: 7건 검토. 6건은 문제 없음(오탐·의도된 설정), **1건은 나중에 고칠 것(접근성)**

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
