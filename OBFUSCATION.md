# 난독화 배포 절차 (앞으로 반복하는 과정)

코드를 수정한 뒤 **난독화된 jar 로 배포**할 때마다 아래 3단계를 따릅니다.
핵심 원칙: **난독화본은 반드시 "빌드 → 실행 검증 → 배포" 순서**로. (난독화는 코드 구조가 바뀌면 깨질 수 있으므로 검증이 필수)

```
① 난독화 jar 빌드  →  ② 실행+테스트로 검증  →  ③ 배포 jar 교체 후 재배포
```

전제: **JDK 21 + Node.js**. (빌드 시 프론트엔드도 함께 빌드해 jar 에 번들하므로 Node 필요)

> **경로 주의:** 빌드는 **실제 프로젝트 경로**에서 실행합니다. 한글 경로 회피용 junction(`C:\alwork`)에서 실행하면 Vite(프론트) 빌드가 상대경로 문제로 실패합니다. junction 은 `gradlew test`(테스트 워커) 실행용으로만 쓰세요.

---

## ① 난독화 jar 빌드 (프론트 + 백엔드 함께)
```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:Path='C:\Program Files\nodejs;'+$env:Path
cd C:\Users\picas\Desktop\김회철\annual-leave      # 실제 경로 (junction 아님)
.\gradlew.bat :backend:bootJarObf
```
- 결과물: `backend\build\libs\backend-obf.jar` — **프론트 UI 번들 + 백엔드 난독화 + 라이선스** 포함 실행 jar
- 내부 순서: `frontendBuild(vite) → copyFrontend(static 번들) → compileJava → proguardClasses(난독화) → bootJarObf(재패키징)`
- 프론트 화면 수정도 이 빌드 한 번에 포함됩니다(별도 프론트 배포 불필요).

번들·난독화 확인(선택):
```powershell
# 프론트 번들 확인
& "$env:JAVA_HOME\bin\jar.exe" tf backend\build\libs\backend-obf.jar | findstr "static/index.html"
# 백엔드 난독화 확인 (메서드가 a(), b() 로 바뀜)
& "$env:JAVA_HOME\bin\javap.exe" -p -cp backend\build\obf\classes com.company.leave.security.SecurityUtils
```

---

## ② 실행 + 테스트로 검증 (가장 중요)
난독화가 리플렉션 등을 깨뜨리지 않았는지 **실제로 띄워서** 확인합니다.

1) 로컬 DB(PostgreSQL) 가 떠 있는지 확인 (`Get-Service postgresql-x64-16`)
2) 난독화 jar 실행:
```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar C:\alwork\backend\build\libs\backend-obf.jar
```
   로그에 `Started LeaveManagementApplication` 이 뜨면 기동 성공.
3) **스모크 테스트**(다른 터미널에서) — 전체 기능을 자동 점검:
```powershell
C:\alwork\scripts\smoke-test.ps1
# 결과: RESULT: PASS=40  FAIL=0  이면 통과
```
   > 이 스크립트는 `QA_Team / QA_Lead / QA_Emp` 등 테스트 데이터를 만듭니다.
   > **운영 DB가 아닌 테스트 환경**에서 돌리세요. 실행 후 정리(FK 순서대로 삭제):
   > ```powershell
   > .\scripts\smoke-cleanup.ps1
   > ```
4) 검증 후 실행 중인 jar 종료(`Ctrl+C` 또는 8080 프로세스 종료).

**FAIL 이 나오면** → 5장(문제 해결)의 keep 규칙을 보완하고 ①부터 다시.

---

## ③ 배포 jar 교체 후 재배포
검증을 통과했으면 배포용 `app.jar` 를 난독화본으로 교체하고, **소스 없는 배포 패키지**를 만듭니다.
```powershell
copy backend\build\libs\backend-obf.jar backend\app.jar
# (내 PC) 소스 미포함 배포 패키지 생성 (소스 포함 여부 자동 검증)
cd C:\Users\picas\Desktop\김회철\annual-leave
.\scripts\package-deploy.ps1
#   → 바탕화면\annual-leave-deploy.tar.gz  (app.jar + 도커설정 + install.sh, 소스 없음)
```
```bash
# (서버) 업로드 후
tar xzf annual-leave-deploy.tar.gz && cd annual-leave
docker compose -f docker-compose.prod.yml up -d --build
```
> jar 만 바꾼 경우, `app.jar` 만 서버로 scp 하고 `docker compose ... up -d --build` 해도 됩니다.
>
> ⚠️ 옛 방식으로 프로젝트 폴더 전체를 `tar` 하면 **소스가 포함되어 난독화가 무의미**해집니다. 반드시 `scripts\package-deploy.ps1`(소스 자동 제외)을 사용하세요.

---

## 4. 롤백
문제가 생기면 **일반(비난독화) jar** 로 즉시 되돌립니다.
```powershell
cd C:\alwork
.\gradlew.bat :backend:bootJar
copy backend\build\libs\backend-0.1.0.jar backend\app.jar
```

---

## 5. 문제 해결 — 난독화 후 깨질 때 (원인별 keep 규칙)
코드를 수정해 **새 record/엔티티/DTO/리포지토리/애스펙트**가 생기면 아래 규칙 범위에 드는지 확인하세요. `backend/proguard-rules.pro` 에서 조정합니다.

| 증상 | 원인 | 조치(규칙) |
|------|------|-----------|
| 응답이 `{}` 로 빔 | record 인식 실패 | `-keepattributes Record,RecordComponents` (이미 있음), record 는 `-keepclassmembers class * extends java.lang.Record { *; }` 로 자동 커버 |
| 기동 시 `-parameters`/파라미터명 오류 | MethodParameters 제거 | `-keepattributes MethodParameters`, `-keepparameternames` (이미 있음) |
| `No property 'a' found` (리포지토리) | 파생쿼리 메서드명 난독화 | `-keepclassmembers interface * extends org.springframework.data.repository.Repository { *; }` (이미 있음) |
| 특정 동작이 감사로그/AOP 에서 누락 | @Aspect pointcut 메서드명 난독화 | 해당 `@Aspect` 클래스 원형 보존: `-keep class 패키지.클래스 { *; }` |
| 엔티티 매핑/JPQL 오류 | 엔티티 필드/클래스명 난독화 | 엔티티는 `domain` 패키지에 두면 `-keepclassmembers class com.company.leave.**.domain.** { *; }` 로 커버 |
| 빈 주입/스캔 실패 | 클래스명 난독화 | 우리 클래스명은 `-keep class com.company.leave.** { <init>(...); }` 로 이미 보존 |

> 새 규칙을 넣은 뒤에는 반드시 ②(실행+스모크 테스트)를 다시 돌려 확인하세요.

---

## 요약
1. `gradlew :backend:bootJarObf`
2. `java -jar backend-obf.jar` → `scripts\smoke-test.ps1` (PASS=40)
3. `copy backend-obf.jar → app.jar` → 압축본 재생성 → `docker compose up -d --build`
4. 문제 시 → keep 규칙 보완(5장) 후 재검증 / 급하면 일반 jar 로 롤백
