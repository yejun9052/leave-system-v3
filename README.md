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
│           ├── application-local.yaml   # 로컬 프로파일 (기본)
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

```bash
docker compose up -d
```

- 기본값: 포트 `3306`, DB `leave`, 계정 `leave` / `leave`, root 비밀번호 `root`
- 바꾸려면 `.env.example`을 `.env`로 복사 후 수정
- 데이터는 호스트 경로 `./docker/mysql/data`에 저장됨 (named volume 아님)

### 2. 백엔드

```bash
cd leave && ./gradlew bootRun
```

- 기본 프로파일은 `local` (`spring.profiles.default`)
- 기동 시 Flyway가 `db/migration`의 마이그레이션을 자동 적용
- Swagger UI: http://localhost:8080/swagger-ui.html
- 현재 `SecurityConfig`가 없어 Spring Security 기본값이 적용됨 → 모든 엔드포인트가 기본 로그인 폼으로 보호되며, 콘솔에 임시 비밀번호가 출력됨

### 3. 프론트엔드

```bash
cd leave-web && npm install && npm run dev
```

---

## 설정 규칙

### 프로파일
| 프로파일 | 용도 | DB 접속 정보 |
|---|---|---|
| `local` | 로컬 개발 (기본) | 환경변수 없으면 `localhost:3306/leave` 기본값 사용 |
| `prod` | 운영 | `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` **필수** |

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
