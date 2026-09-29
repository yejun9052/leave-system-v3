# 연차관리 시스템 (Annual Leave Management System)

회사에서 실제 운영할 수 있는 상용 수준의 연차/반차 관리 시스템입니다. 근로기준법 기반의 연차 자동 부여·소멸, 부서/사용자 관리, 권한 분리(RBAC), 결재 워크플로우, 전사 캘린더, 대시보드를 제공하며 웹과 모바일(반응형 + PWA)에서 동작합니다.

## 기술 스택

| 구분 | 스택 |
|------|------|
| Backend | Java 21, Spring Boot 3.4, Spring Security(JWT), Spring Data JPA, QueryDSL, Flyway |
| Database | PostgreSQL 16 |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui, TanStack Query, FullCalendar |
| 인프라 | Docker Compose |

## 사전 요구사항 (로컬 개발)

아래 도구가 필요합니다. 현재 개발 PC에 미설치 상태이면 먼저 설치하세요.

- **JDK 21** (Temurin/Adoptium 권장)
- **Node.js 20+** 및 npm
- **Docker Desktop** (PostgreSQL 실행용. 미사용 시 로컬 PostgreSQL 16 직접 설치 가능)

> Windows에서는 `winget install EclipseAdoptium.Temurin.21.JDK`, `winget install OpenJS.NodeJS.LTS`, `winget install Docker.DockerDesktop` 로 설치할 수 있습니다.

## 빠른 시작

### 1) 데이터베이스 실행
```bash
docker compose up -d postgres
```
PostgreSQL이 `localhost:5432` (db: `annual_leave`, user: `leave`, pw: `leave1234`)로 뜹니다.

### 2) 백엔드 실행
```bash
cd backend
# 최초 1회: Gradle Wrapper 생성 (JDK21 + Gradle 설치 또는 IntelliJ 사용 시 자동)
gradle wrapper --gradle-version 8.11.1
./gradlew bootRun
```
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui
- 최초 실행 시 Flyway가 스키마를 생성하고 시드 데이터(관리자 계정, 기본 정책, 휴가 종류, 공휴일)를 넣습니다.

### 3) 프론트엔드 실행
```bash
cd frontend
npm install
npm run dev
```
- 웹: http://localhost:5173 (개발 프록시로 `/api` → 백엔드 8080 연결)

### 기본 관리자 계정
- **로컬 개발(local 프로파일)**: `admin@company.com` / `admin1234!`
- **운영(prod)**: 초기 비밀번호가 **설치마다 무작위 생성**되어 **최초 기동 로그에 1회** 표시됩니다(고정 기본 비번 없음). 로그인 후 즉시 변경하세요. — 자세한 내용 [DEPLOY.md](DEPLOY.md) 9장
> 관리자 계정은 사용자 목록에 노출되지 않으며, 본인 프로필 화면에서 비밀번호를 변경합니다.

## 프로젝트 구조
```
annual-leave/
├── docker-compose.yml       # PostgreSQL (+ 선택적 backend)
├── backend/                 # Spring Boot REST API
│   └── src/main/java/com/company/leave/
│       ├── auth/ employee/ department/ leave/ policy/
│       ├── calendar/ dashboard/ notification/ batch/ audit/
│       ├── config/ common/
│       └── resources/db/migration/   # Flyway 마이그레이션
└── frontend/                # React SPA (PWA)
    └── src/{api,components,features,layouts,routes,store,lib}
```

## 권한 구조 (RBAC)
- `SUPER_ADMIN` 시스템관리자 — 전체 권한
- `HR_ADMIN` 인사관리자 — 사용자/부서/연차 운영, 대리승인
- `TEAM_LEAD` 팀장 — 소속 팀원 결재/팀 캘린더
- `EMPLOYEE` 사원 — 휴가 신청/조회

## 운영 배포 (Linux · Docker · HTTPS)
운영 서버 배포는 **[DEPLOY.md](DEPLOY.md)** 를 참고하세요. 요약:
```bash
# 간편: Docker 설치 + 압축해제 + 시크릿 자동생성 + 기동 (OS 선택 메뉴)
sudo bash install.sh

# 또는 수동
cp .env.prod.example .env          # 접속주소·라이선스 (DB비번·JWT는 secrets/ 파일)
docker compose -f docker-compose.prod.yml up -d --build
```
- Caddy가 도메인으로 **HTTPS 인증서를 자동 발급**하고, 프론트 정적파일 + `/api` 프록시를 처리합니다.
- 외부 노출 포트는 80/443뿐이며 backend·postgres는 내부 네트워크 전용입니다.
- **DB 비밀번호·JWT 서명키는 `.env` 평문이 아니라 Docker secrets(`secrets/*`)** 로 관리합니다.
- 배포 아티팩트는 **소스 미포함(난독화 jar)**, 컨테이너는 **비-root** 로 실행됩니다.

## 구현된 기능

- **인증/권한**: JWT 로그인·토큰 재발급, 4단계 RBAC(시스템/인사/팀장/사원), 메서드 단위 인가
- **부서 관리**: 트리 조회, 생성/수정/삭제, 상·하위 이동(재귀 CTE 순환 방지)
- **사용자 관리**: 검색·페이지네이션, 생성/수정, 퇴사/복원, 비밀번호 초기화, 엑셀 가져오기/내보내기(행별 오류 보고), 비밀번호 입력 **마스킹**, 기본 시스템관리자 계정은 목록에서 **숨김**
- **연차 정책**: 입사일/회계연도 기준, 반차·마이너스연차·촉진·이월 설정
  - **근속 가산 정책화**: 기본일수·가산주기·가산량·상한·월차 설정(근로기준법 기본값 제공)
  - **장기근속 포상휴가**(근속 N년 자동 가산), **경조사 규정**(관계별 일수)
  - **사용 통제**: 팀 동시 부재 최대 인원·최소 사전 신청 기한·최대 연속 사용일·블랙아웃 기간
  - **연차 촉진 자동화**(1차 7/1·2차 11/1 알림 배치) 및 미사용 연차 현황
- **연차 엔진**: 근로기준법 기반 자동 산정(1년미만 월 적치, 1년 15일, 3년+ 가산, 최대 25일), 스케줄러 자동 부여/이월, 신규 입사자 초기 부여
- **휴가 신청/결재**: 연차/오전·오후 반차, 근무일 자동 계산(주말·공휴일 제외), 중복 신청 방지, 잔액 검증, 팀장 승인/반려, 관리자 대리승인, 취소·잔액 복원
- **캘린더**: FullCalendar 전사/부서/개인 뷰, 승인 휴가 자동 반영, 관리자·팀장 전사 일정 CRUD, 공휴일 표시
- **대시보드**: 개인(잔여/사용/예정) + 관리자(재직·휴가자·소진율·월별/부서별 통계 차트)
- **리포트**: 연차 사용 현황 엑셀 다운로드
- **알림**: 신청/승인/반려 인앱 알림(헤더 벨), 안 읽음 배지
- **이벤트(감사) 로그**: 모든 생성·수정·삭제·로그인 활동을 AOP로 자동 기록, 관리자 전용 조회 화면(검색·페이지네이션·성공/실패)
- **PWA**: 홈 화면 설치, 오프라인 셸, 반응형(모바일/PC)
- **보안**: 비밀번호 BCrypt·응답 미노출, 로그인 브루트포스 잠금(5회·15분), CSP 등 보안 헤더, DB비번·JWT를 **Docker secrets** 로 분리, 운영 Swagger 비활성, 컨테이너 비-root, 업로드 크기 제한 — 상세 [SECURITY.md](SECURITY.md)

## 테스트
```bash
cd backend
./gradlew test    # 연차 산정 로직 단위 테스트 등
```
전체 기능 스모크 테스트(앱 기동 상태에서, 로컬/테스트 DB 전용):
```powershell
.\scripts\smoke-test.ps1      # 결과 PASS=40 이면 통과 (QA_* 테스트 데이터 생성)
.\scripts\smoke-cleanup.ps1   # 실행 후 QA_* 데이터 정리(실사용자 보존)
```

## ⚠️ 한글 경로 주의 (중요)

프로젝트 경로에 한글(`김회철`)이 포함되면 **Gradle 테스트 워커가 클래스패스를 못 찾아**(`ClassNotFoundException`) 테스트가 실패합니다. 앱 실행/컴파일은 되지만 `test` 태스크가 깨집니다. 두 가지 해결책:

1. **ASCII 경로 junction 사용(권장)**: 관리자 없이 생성 가능
   ```powershell
   cmd /c mklink /J C:\alwork "C:\Users\picas\Desktop\김회철\annual-leave"
   ```
   이후 IntelliJ에서 **`C:\alwork`** 를 열거나, CLI에서 `gradle -p C:\alwork ...` 로 빌드/테스트/실행.
2. **IntelliJ 테스트 러너 사용**: Settings → Build Tools → Gradle → *Run tests using* 를 **IntelliJ IDEA** 로 변경(Gradle 대신 IDE 러너로 테스트 실행 → 경로 문제 회피).

## 개발 참고

- **Gradle Wrapper**: 저장소에는 wrapper 스크립트/설정만 포함되어 있습니다. JDK 21 + Gradle(또는 IntelliJ 내장 Gradle) 준비 후 최초 1회 `gradle wrapper --gradle-version 8.11.1` 로 `gradle-wrapper.jar` 를 생성하면 이후 `./gradlew` 사용이 가능합니다.
- **PWA 아이콘**: 기본은 `favicon.svg` 를 사용합니다. 브랜드 PNG 아이콘(192/512px)을 쓰려면 `frontend/public/` 에 추가하고 `vite.config.ts` 의 manifest.icons 를 교체하세요.
- **이메일 알림**: `application.yml` 의 `app.mail.enabled` 및 SMTP 설정으로 확장할 수 있습니다(현재 기본은 인앱 알림).
- **초기 데이터**: 최초 구동 시 관리자 계정, 기본 정책, 휴가 종류 6종, 2026년 공휴일이 자동 시드됩니다.

## 라이선스
사내 사용 목적.
