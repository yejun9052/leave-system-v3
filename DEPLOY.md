# 운영 배포 가이드 (리눅스 · Docker · 처음 사용자용)

이 문서 하나로 리눅스 서버에 배포할 수 있습니다. **Docker가 처음이어도** 순서대로 따라 하면 됩니다.

---

## 0. 개념 30초 이해

- **컨테이너** = 프로그램 하나를 실행환경째로 담은 "작은 상자".
- **Docker Compose** = 여러 상자를 한 번에 켜고 서로 연결해주는 "지휘자".

이 시스템은 **상자 3개**로 돌아갑니다:

```
브라우저 ─▶ [web (Caddy)] ─▶ [backend (app.jar)] ─▶ [postgres (DB)]
              HTTPS 종단·프록시     UI + API 서빙          데이터 저장
```

| 상자 | 역할 | 비고 |
|------|------|------|
| **postgres** | 데이터베이스 | 없으면 백엔드가 시작 못 함 |
| **backend** | 미리 빌드한 `app.jar` 실행 | **프론트 화면(UI)까지 jar 안에 번들** — UI·API를 한 곳에서 서빙 |
| **web** | 주소(HTTP/HTTPS) 종단 + 프록시 | Caddy가 인증서 자동발급, 모든 요청을 backend 로 전달 |

> **프론트엔드는 백엔드 jar 에 포함**되어 있어 서버에서 프론트를 따로 빌드하지 않습니다. Caddy 는 TLS(https)와 프록시만 담당합니다.
> **서버엔 Docker만 설치하면 됩니다.** 자바(JRE)·PostgreSQL·Node는 상자 안/jar에 이미 반영되어 따로 설치하지 않습니다.

---

## 1. 접속 방식 두 가지 (먼저 결정)

| | **[모드 A] 도메인 + HTTPS** (권장·운영용) | **[모드 B] 서버 IP + HTTP** (임시·테스트용) |
|---|---|---|
| 주소 | `https://leave.회사.com` | `http://<서버IP>` |
| 준비물 | 도메인 + DNS 설정 | 없음(IP만) |
| 보안 | 자물쇠(암호화) O | 암호화 X (사내/테스트만) |
| PWA 설치·오프라인 | 가능 | 불가(브라우저가 HTTP에서 차단) |

> 지금 도메인이 없으면 **모드 B로 먼저 띄워 확인**하고, 나중에 도메인이 생기면 **`.env` 두 줄만 바꿔 재기동**하면 HTTPS로 전환됩니다(11장).

---

## 2. 사전 준비

- 리눅스 서버(공인 IP, SSH 접속 가능), 2vCPU/2GB↑ 권장
- 방화벽/보안그룹 인바운드 개방: **22(SSH), 80, 443**
- (모드 A만) 도메인 **A레코드 → 서버 공인 IP** 설정
  ```bash
  nslookup leave.회사.com     # 결과 IP == 서버 IP 여야 함
  ```

---

## 3. Docker 설치 (서버에서, 최초 1회)

```bash
# 서버 접속
ssh 사용자명@서버IP

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# 권한 적용 위해 재접속
exit
ssh 사용자명@서버IP
docker compose version      # 버전이 보이면 성공
```
> RHEL/Rocky/Alma 계열:
> ```bash
> sudo dnf install -y dnf-plugins-core
> sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
> sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
> sudo systemctl enable --now docker
> ```

---

## 4. 배포 패키지 업로드 (소스 미포함)

> **보안:** 배포 패키지에는 **소스코드가 들어있지 않습니다.** 난독화된 `backend/app.jar`(프론트 UI 번들 + 라이선스 포함)와 도커 설정만 담겨 서버로 올라갑니다. 원본 소스는 **개발 PC에만** 남습니다.

패키지 구성:
```
annual-leave/
├── docker-compose.prod.yml · .env.prod.example · DEPLOY.md · install.sh
├── backend/  Dockerfile(런타임 전용·비root) + app.jar(난독화·FE번들·라이선스)
└── frontend/ Dockerfile(Caddy) + Caddyfile
```

> **간편 설치(권장):** 패키지에 포함된 `install.sh` 를 쓰면 **Docker 설치 → 압축 해제 → 시크릿 자동 생성 → 기동**(3~7장)을 한 번에 처리합니다. OS(Ubuntu/CentOS) 선택 메뉴가 제공됩니다.
> ```bash
> sudo bash install.sh          # 대화형
> sudo bash install.sh --os ubuntu   # 비대화형
> ```
> 아래 3~7장은 수동 절차 설명입니다.

내 PC(Windows PowerShell) — **패키지 생성 후 업로드**:
```powershell
# 소스 없는 배포 패키지 생성 (자동으로 소스 미포함 검증)
.\scripts\package-deploy.ps1
# 업로드
scp "C:\Users\picas\Desktop\annual-leave-deploy.tar.gz" 사용자명@서버IP:~/
```
> `package-deploy.ps1` 실행 전에 `backend/app.jar`가 최신 난독화 빌드인지 확인하세요(12장 빌드 절차). 서버의 `backend/Dockerfile`은 소스 없이 **app.jar만 실행**하는 런타임 전용입니다.

---

## 5. 압축 풀기 (서버에서)

```bash
tar xzf annual-leave-deploy.tar.gz
cd annual-leave
ls        # docker-compose.prod.yml, backend, frontend 등이 보이면 OK
```

---

## 6. 환경설정 (`.env` + 비밀정보 시크릿)

> DB 비밀번호는 **`.env` 평문이 아니라 Docker secret 파일**로 관리합니다(보안 강화). `.env` 에는 접속주소·DB명·라이선스만 둡니다.

### 6-1. `.env` 만들기
```bash
cp .env.prod.example .env
nano .env
```
모드 A (도메인 + HTTPS):
```ini
SITE_ADDRESS=leave.회사.com
APP_ORIGIN=https://leave.회사.com
POSTGRES_DB=annual_leave
POSTGRES_USER=leave
LICENSE_KEY=<발급받은 라이선스 문자열>
LICENSE_HOST=leave.회사.com
```
모드 B (서버 IP + HTTP):
```ini
SITE_ADDRESS=:80
APP_ORIGIN=http://<서버_공인_IP>
POSTGRES_DB=annual_leave
POSTGRES_USER=leave
LICENSE_KEY=<발급받은 라이선스 문자열>
LICENSE_HOST=<라이선스 발급 host>
SESSION_COOKIE_SECURE=false
```
> 모드 B(HTTP)로 운영할 때는 `SESSION_COOKIE_SECURE=false` 를 반드시 추가해야 로그인됩니다(기본값 true).

저장: `Ctrl+O` → `Enter` → `Ctrl+X`

### 6-1-1. 메일(SMTP) 설정
백엔드는 회사 SMTP 로 계정 메일(계정 생성 시 임시 비밀번호, 비밀번호 재설정 링크)을 보냅니다. 트랜잭션 커밋 후 비동기로 발송하며, 발송이 실패해도 API 는 정상 응답하고 로그만 남깁니다(메일 본문의 임시 비밀번호·토큰은 로그에 남기지 않음). `.env` 에 아래 항목을 추가합니다.

| 변수 | 의미 | 기본값 |
|------|------|--------|
| `MAIL_HOST` | SMTP 서버 주소 | `localhost` |
| `MAIL_PORT` | SMTP 포트 | `587` |
| `MAIL_USERNAME` | SMTP 계정 | (빈 값) |
| `MAIL_AUTH` | SMTP 인증 사용 여부 | `true` |
| `MAIL_STARTTLS` | STARTTLS 사용 여부 | `true` |
| `MAIL_FROM` | 발신 주소 | `no-reply@annual-leave.local` |

- SMTP 비밀번호는 `.env` 가 아니라 Docker secret `secrets/mail_password` 로 관리합니다(6-2 참고).
- SMTP 설정이 틀리면 발송이 실패하고 백엔드 로그에 "메일 발송 실패"가 남습니다(API 는 정상 동작). 계정 생성·비밀번호 재설정에 메일이 필요하므로 운영 시 SMTP 설정은 필수입니다.
- `MAIL_FROM` 은 회사 SMTP 가 허용하는 발신 주소로 지정하세요.
- 메일 속 링크는 `.env` 의 `APP_ORIGIN` 주소 기준으로 만들어집니다.

### 6-1-2. 공휴일 API 설정
공공데이터포털 한국천문연구원 특일 정보 API(`getRestDeInfo`)로 공휴일을 `holidays` 테이블에 동기화합니다.
- 공공데이터포털에서 발급받은 **Decoding 키**를 사용하세요(Encoding 키를 넣으면 이중 인코딩 오류가 납니다).
- 키는 `.env` 가 아니라 Docker secret `secrets/holiday_api_key` 로 관리합니다(6-2 참고).
- 매일 00:10(Asia/Seoul)에 올해·내년 공휴일을 자동 동기화하며, 관리자 화면 **정책 · 휴가종류 > 공휴일** 탭에서 연도별 조회와 수동 **동기화**도 할 수 있습니다.
- 배치 순서(Asia/Seoul): 00:10 공휴일 동기화(매일) → 00:30 새해 연차 부여·이월(1월 1일) → 01:00 연차 재계산(매일). 새해 공휴일이 먼저 들어간 뒤 부여가 돌도록 시각을 나눴습니다.
- 새 공휴일이 이미 신청된 휴가 기간에 걸리면 차감 일수를 자동 재계산해 잔액을 환원하고 직원에게 알립니다(기간 전체가 공휴일이면 자동 취소).
- 키가 비어 있으면 동기화하지 않고(경고 로그) 기존 `holidays` 데이터만 사용합니다.

### 6-2. 비밀정보(시크릿) 생성 — DB 비밀번호 · SMTP 비밀번호 · 공휴일 API 키
`install.sh` 를 쓰면 **자동 생성**됩니다. 수동 생성은:
```bash
mkdir -p secrets
printf '%s' "$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | cut -c1-28)" > secrets/db_password
# 메일(SMTP)을 쓰는 경우: SMTP 비밀번호 입력
printf '%s' '<SMTP 비밀번호>' > secrets/mail_password
# 메일을 쓰지 않는 경우: 빈 파일 생성(compose 가 파일을 요구하므로 반드시 있어야 함)
# : > secrets/mail_password
# 공휴일 API 키를 쓰는 경우: 공공데이터포털 Decoding 키 입력
printf '%s' '<공휴일 API Decoding 키>' > secrets/holiday_api_key
# 공휴일 API 를 쓰지 않는 경우: 빈 파일 생성(compose 가 파일을 요구하므로 반드시 있어야 함)
# : > secrets/holiday_api_key
chmod 700 secrets && chmod 644 secrets/*   # 파일 644: 컨테이너 비-root 사용자 읽기용(디렉터리 700 이 보호)
```
- 값은 `secrets/` 파일 안에만 존재하고 컨테이너 내부 `/run/secrets/` 로만 주입됩니다(postgres·backend 공용).
- `secrets/` 는 git·백업 공유에서 제외하세요. 파일 끝에 **줄바꿈이 없어야** 합니다(위 `printf` 사용 시 자동으로 없음).

### 6-3. (선택·권장) 시크릿 암호화 — 마스터키로 at-rest 보호
평문 `secrets/` 대신 **암호문으로 보관**할 수 있습니다(AES-256). 복호화 키(마스터키)는 앱 폴더 밖 root 전용 위치에 두어 분리합니다.
```bash
bash secrets-encrypt.sh     # 마스터키(~/.config/annual-leave/master.key) + secrets.enc/*.enc 생성
rm -f secrets/*             # (선택) 평문 삭제 — 배포 때 자동 복호화됨
```
- 기동 전 `install.sh`(또는 `bash secrets-decrypt.sh`)가 `secrets.enc/` → `secrets/` 로 복호화합니다.
- `secrets.enc/` 는 암호문이라 백업/보관이 안전합니다. **마스터키(`master.key`)는 반드시 별도 백업**하세요(분실 시 복호화 불가).
- 완전한 at-rest 보호는 **디스크 암호화(LUKS/클라우드 볼륨)** 와 함께 쓰는 것을 권장합니다 → [SECURITY.md](SECURITY.md).

---

## 7. 실행 (이 한 줄이 상자 3개를 모두 띄움)

```bash
docker compose -f docker-compose.prod.yml up -d --build
```
- 백엔드는 미리 빌드된 jar를 실행하므로 빠릅니다(서버 컴파일 없음).
- 상태 확인:
```bash
docker compose -f docker-compose.prod.yml ps      # 3개 모두 Up 이면 성공
```

---

## 8. 동작 확인

- **모드 A**: 브라우저에서 `https://leave.회사.com` (자물쇠 확인)
  - 인증서 발급 로그: `docker compose -f docker-compose.prod.yml logs -f web` → `certificate obtained` 류 메시지
- **모드 B**: 브라우저에서 `http://<서버IP>`

백엔드 기동 확인:
```bash
docker compose -f docker-compose.prod.yml logs backend | grep "Started LeaveManagementApplication"
```

---

## 9. 최초 로그인 & 초기 설정

관리 전용 계정(시스템 관리자)은 직원이 아니며, 로그인 아이디는 이메일이 아니라 **`admin`**, 기본 비밀번호는 **`admin1234!`** 입니다(신규 설치 기준. 이미 설치된 서버의 비밀번호는 바뀌지 않습니다).

```
아이디: admin
비밀번호: admin1234!
```
1. 운영(prod)은 첫 로그인 후 **비밀번호 변경 화면이 강제로 표시**되며 변경 전에는 다른 기능을 쓸 수 없습니다(운영 전 필수). 관리 전용 계정은 사용자 목록에 노출되지 않고 연차 부여·휴가 신청·인원 수·리포트 집계에서 제외되며, 우상단 **[내 정보 → 비밀번호 변경]** 으로 변경합니다.
2. 부서 → 사용자 등록 순으로 시작
   - 사용자 등록 시 서버가 임시 비밀번호를 생성해 **계정 생성 메일로만** 발송합니다(관리자는 값을 볼 수 없음, SMTP 설정 필수 — 6-1-1). 사용자는 첫 로그인 때 비밀번호를 변경해야 합니다.
3. 최초 기동 시 기본 정책·휴가종류·2026 공휴일·포상/경조사 규칙이 자동 시드됩니다
4. 비밀번호를 잊은 경우: 로그인 화면의 **"비밀번호 찾기"** 를 쓰거나, 관리자가 사용자 관리에서 **재설정 메일 발송**을 실행합니다(재설정 링크는 30분 유효·1회용).

> 초기 비밀번호를 지정하고 싶으면(테스트 등) `.env` 없이 컨테이너 env 로 `APP_ADMIN_INITIAL_PASSWORD=원하는값` 을 주면 그 값으로 생성됩니다(운영 비권장).

---

## 10. 운영 명령

```bash
# 상태 / 로그
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f web

# 중지 / 시작 / 재시작
docker compose -f docker-compose.prod.yml stop
docker compose -f docker-compose.prod.yml start
docker compose -f docker-compose.prod.yml restart web
```

---

## 11. 나중에 HTTP → HTTPS 전환 (도메인 생겼을 때)

1. 도메인 A레코드 → 서버 IP 설정(2장)
2. `.env` 두 줄만 변경:
   ```ini
   SITE_ADDRESS=leave.회사.com
   APP_ORIGIN=https://leave.회사.com
   ```
3. 재기동:
   ```bash
   docker compose -f docker-compose.prod.yml up -d
   docker compose -f docker-compose.prod.yml restart web
   ```
   → Caddy가 인증서를 자동 발급하고 HTTPS로 전환됩니다. **데이터는 그대로 유지**됩니다.

---

## 12. 새 버전 재배포 (코드 수정했을 때)

프론트·백엔드를 **한 번에 빌드**해 하나의 `app.jar`(UI 번들 + 난독화 + 라이선스)로 만든 뒤 교체합니다. 빌드에는 내 PC에 **Node.js + JDK 21**이 필요합니다(프론트를 함께 빌드하므로).

내 PC:
```powershell
# 1) 프론트+백엔드 함께 빌드 (난독화본). 실제 경로에서 실행
cd C:\Users\picas\Desktop\김회철\annual-leave
$env:Path='C:\Program Files\nodejs;'+$env:Path
.\gradlew.bat :backend:bootJarObf
copy backend\build\libs\backend-obf.jar backend\app.jar
# 2) 소스 없는 배포 패키지 생성
.\scripts\package-deploy.ps1
# 3) 업로드 — 패키지 전체(권장) 또는 app.jar 만 교체
scp "$env:USERPROFILE\Desktop\annual-leave-deploy.tar.gz" 사용자명@서버IP:~/
#   (app.jar만 바꿀 땐)  scp backend\app.jar 사용자명@서버IP:~/annual-leave/backend/app.jar
```
서버:
```bash
# 패키지를 새로 올렸다면
tar xzf annual-leave-deploy.tar.gz && cd annual-leave
docker compose -f docker-compose.prod.yml up -d --build   # Flyway가 스키마 자동 마이그레이션(데이터 보존)
```
> 이번 버전으로 업그레이드하면 **기존 계정은 모두 첫 로그인 때 비밀번호 변경이 요구됩니다**(사용자에게 사전 공지 권장).
> 프론트 화면 수정도 이 한 번의 빌드에 포함됩니다(별도 프론트 배포 불필요). 자세한 빌드/검증 절차는 [OBFUSCATION.md](OBFUSCATION.md) 참고.

---

## 13. 데이터베이스 백업 / 복구

```bash
# 백업 (매일 cron 권장)
docker exec leave-postgres pg_dump -U leave annual_leave | gzip > leave_$(date +%F).sql.gz

# 복구
gunzip -c leave_2026-07-07.sql.gz | docker exec -i leave-postgres psql -U leave -d annual_leave
```
> DB 데이터는 도커 볼륨 `pgdata`, 인증서는 `caddy_data` 에 영구 저장됩니다.

### 화면에서 백업 (정책 › 백업)
- 인사관리자·시스템 관리자가 "지금 백업"을 누르면 서버 폴더 `/var/backups/annual-leave` 에 `annual_leave_날짜_시각_manual.dump` 파일이 생깁니다. 내려받기는 시스템 관리자만 할 수 있습니다.
- 폴더 위치를 바꾸려면 `.env` 에 `BACKUP_DIR=/원하는/경로` 를 넣습니다.
- **이 폴더의 주인은 앱 계정(uid 10001)이어야 합니다.** 앱이 컨테이너 안에서 일반 계정으로 돌기 때문에, 주인이 root 이면 "백업 폴더를 만들 수 없습니다(권한 확인)" 오류가 납니다. `install.sh` 가 자동으로 준비하고, 직접 하려면:
  ```bash
  sudo mkdir -p /var/backups/annual-leave/import
  sudo chown -R 10001:10001 /var/backups/annual-leave
  sudo chmod 700 /var/backups/annual-leave
  ```
- 백업 파일에는 전 직원 정보(비밀번호 해시 포함)가 들어 있습니다. 서버 밖으로 옮길 때 주의하세요.
- 내용 확인: `docker exec leave-backend pg_restore --list /backups/파일이름.dump`
- **자동 백업**: 같은 화면에서 켜고 끄며 주기(매일·매주·N시간마다)·시각·보관 기간(기본 6개월, 이 기간 안의 자동 백업은 모두 남고 더 오래된 것만 지움)을 정합니다. 기본은 매일 02:00. 실패하면 시스템 관리자·인사관리자에게 알림과 메일이 갑니다.
- 백업마다 같은 이름의 `.json`(만든 시각·종류·DB 버전·크기·SHA-256)이 함께 생깁니다. 백업 파일을 옮길 때 같이 옮기세요.
- **화면에서 복원**(시스템 관리자): 목록의 "복원" → 확인 창에서 "복원" 입력. 복원 직전 상태를 `..._pre-restore.dump` 로 먼저 저장하고, 복원하는 동안 다른 사용자에게는 "시스템 점검 중"이 보이며, 끝나면 모든 사용자가 다시 로그인해야 합니다.
- **다른 서버의 백업 불러오기**: 파일을 서버의 `/var/backups/annual-leave/import/` 에 넣으면(이름은 영문·숫자·`._-` 와 `.dump`) 화면의 "가져온 파일"에 보이고 복원할 수 있습니다. 화면에서 올리는 기능은 일부러 두지 않았습니다.
  ```bash
  sudo cp 받은파일.dump /var/backups/annual-leave/import/ && sudo chown 10001:10001 /var/backups/annual-leave/import/받은파일.dump
  ```

### 앱이 고장 났을 때 서버에서 직접 복원
화면에 들어갈 수 없을 때 서버에서 명령으로 복원합니다. 아래 `leave` / `annual_leave` 는 `.env` 의 `POSTGRES_USER` / `POSTGRES_DB` 값입니다(다르면 바꿔서 입력). 모든 명령은 `annual-leave` 폴더에서 root(또는 `sudo`)로 실행합니다.

```bash
# 0) 복원할 파일 고르기 (.json 의 dbVersion 이 지금 앱보다 높으면 복원하면 안 됨)
ls -lh /var/backups/annual-leave/
FILE=/var/backups/annual-leave/annual_leave_20261008_020000_auto.dump   # ← 고른 파일

# 1) 서비스 중지 (DB 는 켜 둠)
docker compose -f docker-compose.prod.yml stop web backend

# 2) 지금 상태를 먼저 백업 (되돌릴 때 씀)
NOW=/var/backups/annual-leave/annual_leave_$(date +%Y%m%d_%H%M%S)_pre-restore.dump
docker exec leave-postgres pg_dump -U leave -Fc \
  --exclude-table-data=spring_session --exclude-table-data=spring_session_attributes annual_leave > "$NOW"
chown 10001:10001 "$NOW"

# 3) 백업 파일을 DB 상자로 복사하고 정상 파일인지 확인 (표 목록이 나오면 정상)
docker cp "$FILE" leave-postgres:/tmp/restore.dump
docker exec leave-postgres pg_restore --list /tmp/restore.dump | head

# 4) 한 트랜잭션으로 복원: 모든 표 지우기 → 백업 내용 넣기. 중간에 오류가 나면 전부 되돌아가 DB 는 그대로입니다
docker exec leave-postgres pg_restore --no-owner -f /tmp/restore.sql /tmp/restore.dump
docker exec leave-postgres sh -c "printf 'DROP SCHEMA public CASCADE;\nCREATE SCHEMA public;\n' > /tmp/reset.sql"
docker exec leave-postgres psql -X -q -o /dev/null -v ON_ERROR_STOP=1 --single-transaction -U leave -d annual_leave \
  -f /tmp/reset.sql -f /tmp/restore.sql && echo "복원 성공"
docker exec leave-postgres rm -f /tmp/restore.dump /tmp/restore.sql /tmp/reset.sql

# 5) 로그인 세션 정리 (복원된 직원·권한으로 다시 로그인하게)
docker exec leave-postgres psql -U leave -d annual_leave -c "DELETE FROM spring_session"

# 6) 서비스 시작 (백업이 이전 버전이면 앱이 시작하며 지금 버전으로 자동 변환)
docker compose -f docker-compose.prod.yml start backend web

# 7) 확인
docker compose -f docker-compose.prod.yml logs --tail=50 backend   # "Started LeaveManagementApplication" 이 보이면 정상
```
- 4)에서 "복원 성공"이 안 나오고 오류가 보이면 DB 는 복원 전 그대로입니다. 6)으로 서비스만 다시 켜고 오류 내용을 확인하세요.
- 복원 뒤 브라우저에서 로그인해 데이터가 그 시점으로 돌아왔는지 확인합니다. 되돌리려면 2)에서 만든 파일로 같은 순서를 반복합니다.

### (선택) 지금 로컬 테스트 데이터 옮기기
내 PC:
```powershell
$env:PGPASSWORD='leave1234'
& "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" -U leave -h localhost annual_leave > C:\Users\picas\Desktop\leave_dump.sql
scp C:\Users\picas\Desktop\leave_dump.sql 사용자명@서버IP:~/
```
서버(DB가 비어있을 때 권장):
```bash
cat ~/leave_dump.sql | docker exec -i leave-postgres psql -U leave -d annual_leave
```

---

## 13-2. 라이선스 키 (선택)

고객사마다 라이선스를 **발급**하고 서버에서 **집행**할 수 있습니다(만료일·최대 사용자수·설치처 고정).

### 발급 (벤더 = 당신 PC에서, 비공개 키 필요)
```powershell
cd C:\alwork
.\gradlew.bat :backend:compileJava
java -cp backend\build\classes\java\main com.company.leave.license.tool.LicenseTool `
     issue "<PRIVATE_KEY>" "고객회사명" 2027-12-31 50 leave.고객도메인.com
# 출력된 라이선스 문자열을 고객에게 전달
```
> `<PRIVATE_KEY>` 는 `LICENSE-PRIVATE-KEY.txt` 에 보관된 발급용 키입니다(서버에 두지 마세요).
> 새 키 쌍이 필요하면 `... LicenseTool keygen` 으로 생성 후, 공개키를 `LicenseService.PUBLIC_KEY_B64` 에 넣고 재빌드하세요.

### 집행 (고객 서버 `.env`) — 필수
```ini
LICENSE_KEY=<발급받은 라이선스 문자열>
LICENSE_HOST=leave.고객도메인.com     # 발급 때 지정한 host 와 동일해야 함
```
라이선스는 **항상 집행**됩니다(on/off 옵션 없음). **유효한 키가 없으면 앱의 모든 기능(로그인 포함)이 차단**되므로, 배포 시 `LICENSE_KEY`·`LICENSE_HOST`를 반드시 설정해야 합니다. 만료/위조/설치처 불일치도 동일하게 차단되며, 만료 30일 전부터 상단 경고 배너가 표시됩니다. 상태는 `GET /api/license` 로 확인할 수 있습니다.

> 코드 보호·서버 보안은 [SECURITY.md](SECURITY.md) 를 참고하세요.

## 14. 배포 전 보안 체크리스트

- [ ] 관리자(`admin`) 기본 비밀번호 변경(운영은 첫 로그인 때 강제)
- [ ] DB 비번은 **`secrets/` 파일**(무작위·강력), `chmod 600`
- [ ] `secrets/`·`.env` 는 git·백업 공유에서 제외
- [ ] 방화벽 80/443/22만 개방 (5432·8080은 외부 미노출 — 기본 구성상 안전)
- [ ] 정기 DB 백업(cron)
- [ ] 가능하면 모드 A(HTTPS)로 운영
- [ ] SMTP 설정 후 테스트 메일 수신·스팸함 확인

---

## 15. 문제 해결

| 증상 | 원인 / 조치 |
|------|------------|
| HTTPS 인증서 실패 | DNS가 서버를 안 가리키거나 80 차단 → 2장 확인 후 `... restart web` |
| 502 Bad Gateway | 백엔드 기동 전 접속 → `logs backend`에서 `Started ...` 확인 |
| DB 연결 실패 | `.env` `POSTGRES_PASSWORD` 확인 후 `... up -d` |
| 포트 충돌(80/443) | 서버의 기존 nginx/apache 중지: `sudo systemctl stop nginx` |
| 업그레이드 후 로그인 풀림 | 재로그인(세션 테이블 호환성) |
| 메일이 안 옴 | SMTP 설정(`MAIL_*`, `secrets/mail_password`) 확인, 백엔드 로그(`logs backend`)의 "메일 발송 실패" 확인 |
| 공휴일이 안 들어옴 | 키(Decoding 키인지) 확인, 백엔드 로그(`logs backend`)의 "공휴일 동기화 실패" 확인 |
| (모드 B) 앱 설치 안 됨 | HTTP에선 PWA/서비스워커 비활성 — 정상. 앱 기능엔 지장 없음 |

---

문의나 오류 로그가 있으면 해당 로그와 함께 알려주세요. 한 단계씩 같이 진행할 수 있습니다.
