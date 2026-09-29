# 서버 종료 · 재시작 가이드

> 이 PC에는 JDK 21, Node.js, PostgreSQL 16이 설치되어 있고, 한글 경로 회피용 junction **`C:\alwork`** 가 프로젝트를 가리킵니다. 모든 명령은 `C:\alwork` 기준입니다.

구성 요소와 포트
- **PostgreSQL 16** — Windows 서비스(`postgresql-x64-16`), 자동 시작. 보통 계속 켜둡니다.
- **백엔드(Spring Boot)** — 포트 **8080**
- **프론트엔드(Vite)** — 포트 **5173**

---

## 1. 종료 (Stop)

### 가장 쉬운 방법: 실행 중인 터미널에서 `Ctrl + C`
백엔드/프론트를 터미널이나 IntelliJ에서 띄웠다면 해당 창에서 `Ctrl + C` (IntelliJ는 ■ 정지 버튼).

### 포트로 강제 종료 (PowerShell)
```powershell
# 8080(백엔드) 종료
Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess -Force -ErrorAction SilentlyContinue
# 5173(프론트) 종료
Stop-Process -Id (Get-NetTCPConnection -LocalPort 5173 -State Listen).OwningProcess -Force -ErrorAction SilentlyContinue
```

### Gradle 데몬까지 정리 (선택)
```powershell
C:\alwork\gradlew.bat --stop
```

### PostgreSQL 서비스 (보통 끌 필요 없음)
```powershell
Stop-Service  postgresql-x64-16   # 중지 (관리자 권한)
Start-Service postgresql-x64-16   # 시작
Get-Service   postgresql-x64-16   # 상태 확인
```

---

## 2. 재시작 (Start)

### (0) DB 확인 — 자동 실행이라 보통 그대로 OK
```powershell
Get-Service postgresql-x64-16      # Status가 Running이면 됨
```

### (A) 백엔드
```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
cd C:\alwork
.\gradlew.bat :backend:bootRun
```
- 기동 완료 로그: `Started LeaveManagementApplication`
- 확인: http://localhost:8080/swagger-ui

### (B) 프론트엔드 (새 터미널)
```powershell
cd C:\alwork\frontend
npm run dev
```
- 확인: http://localhost:5173  (로그인 `admin@company.com` / `admin1234!`)

---

## 3. IntelliJ 로 실행 (권장)
1. **`C:\alwork`** 폴더 열기 (한글 경로 회피)
2. Gradle JVM = JDK 21 확인 (Settings → Build Tools → Gradle)
3. `LeaveManagementApplication` ▶ 실행 → 백엔드 8080
4. 하단 Terminal에서 `cd frontend && npm run dev` → 프론트 5173
- 이미 8080/5173이 떠 있으면 위 "종료"로 먼저 내리고 실행하세요.

---

## 4. 자주 겪는 문제
- **포트 사용 중(8080/5173)**: 위 "포트로 강제 종료" 실행 후 다시 시작.
- **`gradlew` 가 JDK를 못 찾음**: `JAVA_HOME` 을 JDK 21로 지정했는지 확인.
- **테스트가 `ClassNotFoundException`**: 한글 경로 문제 → 반드시 `C:\alwork` 에서 실행(또는 IntelliJ 테스트 러너 사용). 자세한 내용은 [README.md](README.md) 참고.
- **DB 연결 실패**: `Get-Service postgresql-x64-16` 로 서비스가 Running인지 확인.
