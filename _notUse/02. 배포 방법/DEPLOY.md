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

## 4. 소스(압축본) 서버로 올리기

내 PC(Windows PowerShell)에서:
```powershell
scp "C:\Users\picas\Desktop\annual-leave-deploy.tar.gz" 사용자명@서버IP:~/
```
> 압축본에는 `backend/app.jar`(미리 빌드됨) + 배포 설정이 모두 들어 있습니다.

---

## 5. 압축 풀기 (서버에서)

```bash
tar xzf annual-leave-deploy.tar.gz
cd annual-leave
ls        # docker-compose.prod.yml, backend, frontend 등이 보이면 OK
```

---

## 6. 환경설정 (`.env` 만들기)

```bash
cp .env.prod.example .env
nano .env
```

### 모드 A (도메인 + HTTPS) 로 채우기
```ini
SITE_ADDRESS=leave.회사.com
APP_ORIGIN=https://leave.회사.com
POSTGRES_DB=annual_leave
POSTGRES_USER=leave
POSTGRES_PASSWORD=<강력한_DB_비밀번호>
JWT_SECRET=<아래 명령으로 생성>
```

### 모드 B (서버 IP + HTTP) 로 채우기
```ini
SITE_ADDRESS=:80
APP_ORIGIN=http://<서버_공인_IP>
POSTGRES_DB=annual_leave
POSTGRES_USER=leave
POSTGRES_PASSWORD=<강력한_DB_비밀번호>
JWT_SECRET=<아래 명령으로 생성>
```

JWT 시크릿 생성(값을 복사해 `JWT_SECRET=` 에 붙여넣기):
```bash
openssl rand -base64 48
```
저장: `Ctrl+O` → `Enter` → `Ctrl+X`

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

```
이메일: admin@company.com
비밀번호: admin1234!
```
1. 로그인 후 **비밀번호 즉시 변경**(운영 전 필수)
2. 부서 → 사용자 등록 순으로 시작
3. 최초 기동 시 기본 정책·휴가종류·2026 공휴일·포상/경조사 규칙이 자동 시드됩니다

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
.\gradlew.bat :backend:bootJarObf
copy backend\build\libs\backend-obf.jar backend\app.jar
# 2) app.jar 만 서버로 교체 (또는 압축본 재생성 후 업로드)
scp backend\app.jar 사용자명@서버IP:~/annual-leave/backend/app.jar
```
서버:
```bash
cd annual-leave
docker compose -f docker-compose.prod.yml up -d --build   # Flyway가 스키마 자동 마이그레이션(데이터 보존)
```
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

- [ ] `admin@company.com` 기본 비밀번호 변경
- [ ] `JWT_SECRET` 길고 무작위(재배포해도 유지)
- [ ] `POSTGRES_PASSWORD` 강력하게
- [ ] 방화벽 80/443/22만 개방 (5432·8080은 외부 미노출 — 기본 구성상 안전)
- [ ] 정기 DB 백업(cron)
- [ ] 가능하면 모드 A(HTTPS)로 운영

---

## 15. 문제 해결

| 증상 | 원인 / 조치 |
|------|------------|
| HTTPS 인증서 실패 | DNS가 서버를 안 가리키거나 80 차단 → 2장 확인 후 `... restart web` |
| 502 Bad Gateway | 백엔드 기동 전 접속 → `logs backend`에서 `Started ...` 확인 |
| DB 연결 실패 | `.env` `POSTGRES_PASSWORD` 확인 후 `... up -d` |
| 포트 충돌(80/443) | 서버의 기존 nginx/apache 중지: `sudo systemctl stop nginx` |
| 로그인 후 401 반복 | `JWT_SECRET` 변경 후 재배포 시 → 재로그인 |
| (모드 B) 앱 설치 안 됨 | HTTP에선 PWA/서비스워커 비활성 — 정상. 앱 기능엔 지장 없음 |

---

문의나 오류 로그가 있으면 해당 로그와 함께 알려주세요. 한 단계씩 같이 진행할 수 있습니다.
