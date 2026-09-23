# Leave-System

휴가 관리 시스템. 백엔드(Spring Boot) + 프론트엔드(React) 구성.

---

## 사용 스택

### 백엔드

- **Java 21** / **Gradle 9.7.1**
- **Spring Boot 4.1.1**
- **Spring Data JPA** + 네이티브 SQL (리포트·부서 재귀 CTE만 SQL)
- **Spring Security** (세션 방식)
- **Spring Mail** + **Thymeleaf** (메일 템플릿)
- **Spring @Scheduled** + 실행 로그 테이블
- **springdoc-openapi** (Swagger UI)
- **Apache POI** → 엑셀 임포트·내보내기
- OAuth 2.0

### DB

- **MySQL 8.4**
- **Flyway** (`flyway-core`, `flyway-mysql`) → 백업용

### 프론트엔드

- **React 19** + **Vite 8** + **TypeScript**
- **TanStack Query** (폴링, 서버 상태 캐시)

### 배포

- **Docker Compose** (앱 + MySQL, DB 볼륨은 호스트 경로)

### 사용 안 함 (의도적 제외)

- **캐시 (Redis 등)** — 해결할 문제가 없음
- **JWT** — 서버 한 대라 무상태 이점이 없음. 리프레시 토큰도 결국 DB 저장이라 상태가 생김
- **Quartz** — 규모 대비 무거움, `@Scheduled`로 대체 가능

---

## 디렉터리 구조

```
leave-system/
├── docker-compose.yml          # MySQL 8.4 (호스트 볼륨)
├── .env.example                # compose 환경변수 샘플
├── docker/mysql/data/          # MySQL 데이터 (git 제외)
├── leave/                      # 백엔드 (Spring Boot)
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/leavesystem/leave/
│       └── resources/
│           ├── application.yaml         # 공통 설정
│           ├── application-local.yaml   # 로컬 DB 설정
│           ├── application-dev.yaml     # 개발 DB 설정 재사용 (기본)
│           ├── application-prod.yaml    # 운영 프로파일
│           ├── db/migration/            # Flyway 마이그레이션
│           ├── templates/               # Thymeleaf 메일 템플릿
│           └── static/
└── leave-web/                  # 프론트엔드 (React + Vite)
    └── src/
```

---

## 로컬 실행

### 1. MySQL 기동

먼저 `.env.example`을 `.env`로 복사하고 아래의 최초 관리자 값을 채운다.

```bash
docker compose up -d
```

- 기본값: 포트 `3306`, DB `leave`, 계정 `leave` / `leave`, root 비밀번호 `root`
- DB 접속 정보를 바꾸려면 `.env`의 MySQL과 Spring 항목을 함께 수정
- 데이터는 호스트 경로 `./docker/mysql/data`에 저장됨 (named volume 아님)

### 2. 백엔드

```bash
cd leave && ./gradlew bootRun
```

- 기본 프로파일은 `dev` (`spring.profiles.default`)이며 로컬 DB 설정을 재사용한다. 인증은 모든 프로파일에서 동일한 DB 계정·세션 방식이다.
- 기동 시 Flyway가 `db/migration`의 마이그레이션을 자동 적용
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- 첫 기동 전 아래의 시스템 관리자 발급 환경변수를 설정한다. 운영 Google OAuth와 사원 로그인은 별도 구현 대상이다.

### 3. 프론트엔드

```bash
cd leave-web && npm install && npm run dev
```

### 최초 시스템 관리자 발급

루트의 `.env.example`을 `.env`로 복사하고 `INITIAL_ADMIN_LOGIN_ID`,
`INITIAL_ADMIN_OWNER`, `INITIAL_ADMIN_PASSWORD`를 입력한다. 비밀번호는 8자 이상으로 정한다.
`INITIAL_ADMIN_OWNER`에는 실제 계정 사용자를 기록한다. `dev`와 `prod` 모두 첫 기동 시
발급된 `SYS_ADMIN` 계정이 전혀 없다면 이 값으로 계정 하나를 생성한다. 비밀번호는 BCrypt
해시로 DB에 저장한다. 이미 발급된 계정이 있으면 비활성 상태여도 재기동으로 다시
활성화하거나 비밀번호·소유자를 바꾸지 않는다. 값이 빠진 상태에서 발급된 계정도 없으면
앱은 명확한 오류와 함께 기동을 멈춘다.

계정은 `employee` 테이블의 `SYS_ADMIN` 행으로 보관한다. 이 행은 이메일·입사일이 없는
운영 계정이며 사원 수·연차 대상에서 제외해야 한다. DB는 활성 `SYS_ADMIN`을 최대
1개로 제한한다. 기존 별도 테이블의 계정은 V5 마이그레이션에서 이관한다.
회원가입과 사원 로그인은 이 단계의 범위 밖이다. 이전의 개발용 고정 비밀번호와 자동
샘플 사원 생성은 로그인 경로에서 사용하지 않는다.

### 관리자 세션 로그인

MySQL과 백엔드를 실행한 뒤 [로그인 폼](http://localhost:8080/login)에서 `.env`에
설정한 로그인 ID와 비밀번호를 입력한다. Spring Security가 `POST /api/auth/login`으로
인증하고 세션을 발급한다. 성공하면 `/api/auth/me`에서 로그인 ID와 `ROLE_SYS_ADMIN`을
확인할 수 있고, 이후 브라우저는 `JSESSIONID` 쿠키로 세션을 유지한다.

폼 요청에는 CSRF 토큰이 포함된다. JSON 클라이언트는 `GET /api/auth/csrf`에서 토큰을
받아 로그인·로그아웃 요청에 전달할 수 있다. 로그아웃은 CSRF 토큰을 포함한
`POST /api/auth/logout`이며 세션과 쿠키를 제거한다.

---

## 설정 규칙

### 프로파일


| 프로파일    | 용도         | DB 접속 정보                                                  |
| ------- | ---------- | --------------------------------------------------------- |
| `dev` | 개발 실행 (기본) | local의 DB 설정 재사용, 관리자 DB 로그인 |
| `local` | 로컬 DB 설정 | 환경변수 없으면 `localhost:3306/leave` 기본값 사용 |
| `prod`  | 운영         | `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` **필수** |


주요 환경변수: `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` `MAIL_HOST` `MAIL_PORT` `MAIL_USERNAME` `MAIL_PASSWORD`

### 주요 결정 사항

- `spring.jpa.hibernate.ddl-auto: validate` — **스키마는 Flyway가 단독 관리**. 엔티티로 DDL을 생성하지 않음
- `spring.jpa.open-in-view: false` — 뷰 렌더링 중 지연 로딩 금지, 서비스 계층에서 명시적으로 조회
- 세션 타임아웃 30분, 쿠키는 `HttpOnly` + `SameSite=Lax` (운영은 `Secure` 추가)
- 멀티파트 업로드 10MB 제한 (엑셀 임포트용)

### DB 마이그레이션

- 파일명: `V{번호}__{설명}.sql` (예: `V2__create_employee.sql`)
- 위치: `leave/src/main/resources/db/migration/`
- **적용된 마이그레이션은 절대 수정하지 않는다.** 변경은 항상 새 버전 파일로
- `V1__init.sql`: 스케줄러 실행 로그 테이블(`scheduled_job_log`)만 포함. 도메인 테이블은 스키마 확정 후 V2 이상에서 추가

---

## API 공통 응답

모든 API 응답은 `ApiResponse` 로 감싼다.
위치: `leave/src/main/java/com/leavesystem/leave/common/response/`

성공:

```json
{ "success": true, "data": { } }
```

실패:

```json
{ "success": false, "error": { "status": 400, "message": "잘못된 요청입니다" } }
```

- null 필드는 직렬화에서 제외 → 성공 응답에 `error`, 실패 응답에 `data` 가 나타나지 않는다
- `error.status` 는 HTTP 상태 코드(int)
- 사용법
  - `ApiResponse.success(data)` — 데이터 있는 성공
  - `ApiResponse.success()` — 데이터 없는 성공 (등록·수정·삭제)
  - `ApiResponse.error(HttpStatus.BAD_REQUEST, "...")` — 실패
- 레코드 컴포넌트명이 `isSuccess` 인 것은 접근자 `success()` 와 정적 팩토리 `success()` 의 시그니처가 충돌하기 때문. JSON 필드명은 `@JsonProperty("success")` 로 고정했다

---

## 진행 상황

### 완료

- [x] Docker Compose (MySQL 8.4, 호스트 볼륨)
- [x] `application.yaml` 프로파일 분리 + datasource / JPA / Flyway / Mail / 세션 설정
- [x] Flyway `V1__init.sql` (스케줄러 실행 로그 테이블)
- [x] Apache POI 의존성 추가
- [x] TanStack Query 의존성 추가
- [x] API 공통 응답 포맷 (`ApiResponse`, `ApiError`) + 직렬화 테스트

### 미완료

- [ ] 전역 예외 핸들러 (`@RestControllerAdvice`) — 예외를 `ApiResponse.error` 로 변환
- [ ] 백엔드 패키지 구조 (domain / config / controller ...)
- [ ] `SecurityConfig` — 세션 방식 인증 설정
- [ ] `@EnableScheduling` + 스케줄러 실행 로그 기록 로직
- [ ] Swagger(springdoc) 세부 설정
- [ ] 도메인 스키마 설계 및 Flyway V2 이상 마이그레이션
- [ ] 프론트 `QueryClientProvider` 연결 (라이브러리만 설치된 상태)
- [ ] 프론트 Vite dev 프록시 (`server.proxy`) 설정
- [ ] 프론트 Vite 템플릿 잔재 정리 (`App.tsx`, `assets/`)
- [ ] 백엔드 `Dockerfile` + compose에 앱 서비스 추가 (현재 compose는 MySQL만)
