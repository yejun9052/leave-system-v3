# 보안 가이드 — 코드 보호 & 서버 보안

> **핵심 결론:** 서버(root)를 완전히 장악당하면 코드를 100% 숨기는 것은 원리적으로 불가능합니다.
> 따라서 방어의 우선순위는 **① 서버 탈취 자체를 막기(가장 중요) → ② 탈취되더라도 데이터 보호 → ③ 코드 난독화(보조)** 입니다.

---

## 1. 코드 보호의 현실

| 자산 | 노출 정도 | 대응 |
|------|-----------|------|
| 백엔드 `app.jar` | 디컴파일러로 거의 복원 가능 | 난독화(보조, 5장) / 서버접근 통제(2장) |
| 프론트 JS | 브라우저로 내려가는 공개 코드 | 원래 숨길 수 없음(최소화만) |
| 실행 중 메모리·키 | root면 접근 가능 | 서버 접근 통제가 유일한 실효 방어 |

→ **가장 효과적인 "코드 보호"는 서버에 아무나 못 들어오게 하는 것**입니다.

---

## 2. 서버 보안 강화 (실질 방어 · 필수)

### 2-1. SSH 잠그기
```bash
# 키 기반 로그인만 허용, 비밀번호/루트 로그인 차단
sudo nano /etc/ssh/sshd_config
#   PasswordAuthentication no
#   PermitRootLogin no
sudo systemctl restart ssh
```
- 접속은 **SSH 키**로만. 개인키는 담당자 PC에만 보관.

### 2-2. 방화벽 최소 개방
```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp      # (가능하면 사내 IP만 허용)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```
- **5432(DB)·8080(백엔드)은 절대 외부 개방 금지** — 현재 구성상 컨테이너 내부에서만 통신하므로 기본적으로 안전합니다.

### 2-3. 자동 보안 업데이트 & 침입 차단
```bash
sudo apt install -y unattended-upgrades fail2ban
sudo systemctl enable --now fail2ban
```

### 2-4. 최소 권한
- 배포/운영 계정은 sudo 최소화, Docker 그룹만 부여.
- 서버에 소스·비밀키를 남기지 말 것(발급용 라이선스 **비공개 키는 서버에 두지 않음**).

---

## 3. 저장 데이터 보호 (디스크 암호화)

서버/디스크가 물리적으로 탈취돼도 내용을 못 읽게:
- **클라우드**: 볼륨 암호화 옵션 켜기(AWS EBS Encryption, GCP CMEK 등) — 체크박스 하나.
- **온프렘**: 설치 시 **LUKS 디스크 암호화** 사용.
- DB 백업 파일도 암호화 보관(`gpg` 등).

> 주의: 서버가 켜져 있고 root가 뚫리면 디스크 암호화는 무력화됩니다(이미 복호화 상태). 디스크 암호화는 **"꺼진 서버/디스크 도난"**에 대한 방어입니다.

---

## 4. 비밀정보 관리 (Docker secrets)

- **DB 비밀번호·SMTP 비밀번호는 `.env` 평문이 아니라 Docker secret 파일로 관리**합니다.
  - `secrets/db_password`, `secrets/mail_password`(SMTP 비밀번호) → 컨테이너 내부 `/run/secrets/` 로만 주입, Spring `configtree` 로 로딩.
  - postgres 는 `POSTGRES_PASSWORD_FILE`, backend 는 `configtree` 로 동일 시크릿을 읽습니다.
  - `install.sh` 가 없으면 **자동 생성**(무작위, `chmod 600`). 수동 생성은 `.env.prod.example` 주석 참고.
  - 메일 본문(임시 비밀번호·재설정 토큰)은 로그에 남기지 않습니다.
- `.env`(LICENSE_KEY·접속주소 등)와 `secrets/` 는 **git 커밋 금지**(`.gitignore`). 권한: `chmod 600 .env`, `chmod 700 secrets && chmod 600 secrets/*`.
- 시크릿 교체: `secrets/*` 파일 갱신 후, DB 비번은 컨테이너에서 `ALTER USER` 또는 볼륨 초기화로 맞춤.
- **(선택·권장) 시크릿 암호화**: `secrets-encrypt.sh` 로 `secrets/` 를 AES-256 암호문 `secrets.enc/*.enc` 로 저장하고, **마스터키는 앱 폴더 밖(root 전용)** 에 분리 보관. 배포 시 `secrets-decrypt.sh`(또는 `install.sh`)가 복호화합니다. 암호문은 백업/보관이 안전하며, **마스터키는 반드시 별도 백업**(분실 시 복호화 불가).
- **디스크 암호화(LUKS/클라우드 볼륨)** 를 함께 쓰면 "꺼진 서버/디스크 도난"까지 방어됩니다(3장).
- 라이선스 **발급용 비공개 키**는 서버가 아니라 **별도 안전한 곳**(암호관리자/오프라인)에 보관.

---

## 5. (옵션) 코드 난독화

### 5-1. 방식
ProGuard를 **이 프로젝트에 맞게 통합**해 두었습니다. 클래스 "이름"은 보존(엔티티 JPQL·빈 스캔·JSON 매핑 안전)하고, **서비스 내부 메서드/필드 이름을 난독화**합니다. 필요한 보존 규칙(엔티티·DTO·record·리포지토리·파라미터명·@Aspect·Record 속성 등)은 `backend/proguard-rules.pro` 에 반영돼 있으며, **전체 e2e 테스트(40/40) 통과를 확인**했습니다.

### 5-2. 적용 방법 (Gradle 태스크)
```bash
cd backend        # 또는 프로젝트 루트에서 :backend: 접두사로
./gradlew :backend:bootJarObf
# → build/libs/backend-obf.jar 생성 (난독화된 실행 jar)
```
검증 후 배포에 반영:
```bash
cp backend/build/libs/backend-obf.jar backend/app.jar   # 배포용 jar 를 난독화본으로 교체
# 압축본 재생성 후 서버 재배포
```
난독화 확인(예): 서비스 메서드가 `a()`, `b()` 등으로 바뀐 것을 `javap` 로 볼 수 있습니다.

> 코드를 수정해 클래스/멤버 구조가 바뀌면 keep 규칙 보완이 필요할 수 있으니, 난독화본은 항상 **전체 기능 테스트 후** 배포하세요. 되돌리려면 일반 `bootJar` 로 만든 jar 를 쓰면 됩니다.

### 5-2-1. 한계
디컴파일 자체를 막지는 못하며(읽기 어렵게만 함), 프레임워크가 리플렉션으로 다루는 클래스/멤버는 이름을 유지해야 하므로 그 부분은 난독화되지 않습니다.

### 5-3. 더 강한 보호가 꼭 필요하면
- **GraalVM Native Image**: 바이트코드가 아닌 네이티브 바이너리로 컴파일 → 디컴파일 난이도 대폭 상승. 단, 리플렉션 설정 등 작업량이 큽니다(별도 프로젝트 수준).

---

## 6. 애플리케이션 레벨 보안 (이미 적용됨)

- 비밀번호 **BCrypt** 해시 저장, API 응답에 비밀번호/해시 **미노출**
- **서버 세션 인증**(Spring Session JDBC, 세션은 PostgreSQL 저장) + 역할기반 접근제어(RBAC), 메서드 단위 인가
  - 세션 쿠키 `SESSION`: **HttpOnly · Secure · SameSite=Lax** (JS에서 읽을 수 없음)
  - **CSRF 보호**: `GET /api/auth/csrf` 로 `XSRF-TOKEN` 쿠키 발급 → POST/PUT/PATCH/DELETE 는 `X-XSRF-TOKEN` 헤더 필수(없으면 403)
  - **로그인 시 세션 ID 재발급**(세션 고정 방어), 로그아웃(`POST /api/auth/logout`) 시 세션 폐기
  - 세션 미사용 만료 2시간(`SESSION_TIMEOUT`), 서버 재시작 후에도 세션 유지(DB 저장)
  - **퇴사 처리 시 해당 직원의 세션 즉시 폐기**
- **비밀번호 수명주기**
  - 계정 생성·엑셀 가져오기 시 **서버가 임시 비밀번호를 생성**하고 계정 생성 메일로만 전달합니다(관리자에게 비공개). 신규 계정은 **첫 로그인 시 비밀번호 변경 강제**(변경 전에는 login·csrf·me·logout·비밀번호 변경·라이선스 조회 외 API 차단, 403 PASSWORD_CHANGE_REQUIRED)
  - **재설정 링크**: 토큰은 SHA-256 해시만 저장, 30분 유효·1회용, 새 토큰 발급 시 이전 토큰 무효화, 비밀번호 찾기 응답은 계정 존재 여부와 무관하게 동일(계정 존재 비노출), 이메일당 1시간 3회 제한
  - 비밀번호 변경·재설정 시 **다른 세션 종료**(변경 시 현재 세션 ID 재발급, 재설정 시 해당 사용자의 모든 세션 종료)
- **로그인 브루트포스 방어**: 이메일당 5회 실패 시 15분 잠금(HTTP 429)
- **보안 응답 헤더**: Content-Security-Policy, Referrer-Policy, Permissions-Policy, X-Frame-Options: DENY, X-Content-Type-Options: nosniff
- **비밀정보 Docker secrets 분리**
- 운영 프로파일에서 **Swagger/OpenAPI 문서 비활성**(API 명세 비노출)
- **Docker 컨테이너 비-root 실행**(uid 10001)
- **업로드 파일 크기 제한**(멀티파트 5MB — 압축폭탄/DoS 완화)
- **사용자 관리 비밀번호 입력 마스킹**, 기본 **시스템관리자 계정 목록 숨김**
- 전역 예외 처리로 **스택트레이스 미노출**, DB 제약 위반은 재시도 가능한 409로 매핑
- 관리 전용 계정(`admin`)의 기본 비밀번호는 `admin1234!` 이며 운영(prod)은 **첫 로그인 때 비밀번호 변경 강제**, 기본 실행 프로파일 **prod**(개발용 시크릿 폴백 차단)
- 모든 변경·로그인 **감사 로그** 기록(이벤트 로그 화면)
- **라이선스 키** 집행(만료·사용자수·설치처) — [DEPLOY.md](DEPLOY.md) 라이선스 장 참고

### 6-1. 배포/컨테이너 하드닝
- Docker 이미지 **비-root 실행**(uid 10001), 베이스 태그 고정(temurin `21-jre-jammy`·caddy `2.8-alpine`), **HEALTHCHECK**
- compose 서비스별 **메모리 제한** + `security_opt: no-new-privileges`, JVM `-XX:MaxRAMPercentage=75.0`
- backend(8080)·postgres(5432) **외부 미노출**(내부 네트워크 전용), web(80/443)만 공개
- 의존성 최신 유지: Spring Boot **4.1.1**(Spring Security 7.1.1, Tomcat 11.0.24)·POI 5.4.0·springdoc 3.1.1
- `.gitignore` 에 `secrets/`·`secrets.enc/`·`*.key`·`master.key`·`*.pem`·배포 산출물 제외

### 6-2. 신뢰성/정합성 통제
- 연차 잔액 **낙관적 락(@Version)** + 승인 시점 잔액 재검증 → 동시 승인 over-spend/lost-update 방지
- 휴가 **차감액(deductedDays)** 을 기간(days)과 분리 저장 → 유형별 차감 정확
- **활성 정책 단일 보장**(부분 유니크 인덱스), 사용자 생성 **직렬화**(advisory lock)로 라이선스 인원 초과 방지

> 정적(SonarQube)·동적(OWASP ZAP) 기준 취약점 점검을 여러 차례 수행하고 발견 항목을 조치했습니다(2026-07-07 기준). SQL 인젝션(파라미터 바인딩)·RCE·로그 인젝션 소지 없음 확인. 정확한 의존성 CVE 전수 확정은 Trivy/OWASP-Dependency-Check 스캔을 권장합니다.

---

## 보안 점검 체크리스트
- [ ] SSH 키 로그인만, 루트/비번 로그인 차단
- [ ] 방화벽 22/80/443만, DB/백엔드 포트 외부 차단
- [ ] 디스크/볼륨 암호화
- [ ] `.env` 권한 600, git 미포함 / `secrets/` 권한 700·600, git 미포함
- [ ] DB 비번은 **Docker secrets(`secrets/*`)** 로 관리(무작위·강력)
- [ ] 라이선스 비공개 키 서버 밖 보관
- [ ] 정기 백업(암호화) + 자동 보안 업데이트
- [ ] (선택) 난독화 jar는 전체 테스트 후 적용
