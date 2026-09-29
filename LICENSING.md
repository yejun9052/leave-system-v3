


# 라이선스 발급 가이드 (공급사 전용)

고객사마다 라이선스를 **발급(서명)**하고 전달하는 방법입니다. 발급은 **비공개 키**로만 가능하며, 앱에는 **공개 키**만 내장되어 검증만 합니다. 고객은 키를 위조할 수 없습니다.

> 라이선스는 **항상 집행**됩니다. 유효한 키가 없으면 고객 서버에서 로그인 포함 모든 기능이 차단됩니다.

---

## 1. 구조 이해 (Ed25519 서명)

```
[비공개 키]  ──서명──▶  라이선스 문자열  ──검증──▶  [공개 키(앱 내장)]
  당신 보관                고객에게 전달              고객 서버에서 확인
```

라이선스에 담기는 정보:
| 항목 | 설명 |
|------|------|
| **licensee** | 사용 회사명 (화면 표기·식별용) |
| **expiresAt** | 만료일 (이 날짜까지 유효) |
| **maxUsers** | 최대 재직 사용자 수 (0 = 무제한) |
| **host** | 설치처 고정 값(도메인). 고객 `.env`의 `LICENSE_HOST`와 일치해야 함 |

---

## 2. 최초 1회: 키 쌍 (이미 완료됨)

키 쌍은 이미 생성되어 있습니다.
- **공개 키**: 앱 `LicenseService.PUBLIC_KEY_B64` 에 내장됨
- **비공개 키**: `LICENSE-PRIVATE-KEY.txt` 에 보관 (저장소·서버에 두지 말 것)

> **키 쌍을 새로 만들어야 할 때만** (예: 비공개 키 유출) 아래를 수행하세요. 새 공개키를 앱에 넣고 **재빌드·재배포**해야 하며, 기존 라이선스는 모두 무효가 됩니다.
> ```powershell
> cd C:\alwork
> .\gradlew.bat :backend:compileJava
> java -cp backend\build\classes\java\main com.company.leave.license.tool.LicenseTool keygen
> # 출력된 PUBLIC KEY → LicenseService.PUBLIC_KEY_B64 교체 후 재빌드
> # 출력된 PRIVATE KEY → 안전하게 보관
> ```

---

## 3. 라이선스 발급 (고객마다)

### 준비 (최초 1회 / 코드 변경 후)
```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
cd C:\alwork
.\gradlew.bat :backend:compileJava
```

### 발급 명령
```powershell
java -cp backend\build\classes\java\main com.company.leave.license.tool.LicenseTool `
     issue "<비공개키>" "<회사명>" <만료일> <최대사용자수> <설치처host>
```
예시 — ㈜가나상사, 2027-12-31 만료, 최대 50명, 도메인 leave.gana.co.kr:
```powershell
java -cp backend\build\classes\java\main com.company.leave.license.tool.LicenseTool `
     issue "MC4CAQAw...(LICENSE-PRIVATE-KEY.txt 참조)" "가나상사" 2027-12-31 50 leave.gana.co.kr
```

인자 설명:
| 순서 | 인자 | 예 |
|------|------|-----|
| 1 | 비공개 키 (Base64) | `LICENSE-PRIVATE-KEY.txt`의 값 |
| 2 | 회사명 | `"가나상사"` (공백 있으면 따옴표) |
| 3 | 만료일 `YYYY-MM-DD` | `2027-12-31` |
| 4 | 최대 사용자 수 | `50` (0=무제한) |
| 5 | 설치처 host | `leave.gana.co.kr` |

출력:
```
=== LICENSE (고객 서버 LICENSE_KEY 로 설정) ===
eyJpZCI6...(긴 문자열)...RAiCQ
```
이 **라이선스 문자열**을 고객에게 전달합니다.

---

## 4. 고객 적용 방법 (전달 시 안내)

고객 서버의 `.env` 에:
```ini
LICENSE_KEY=<발급한 라이선스 문자열>
LICENSE_HOST=leave.gana.co.kr     # 발급 때 지정한 host 와 동일
```
재기동:
```bash
docker compose -f docker-compose.prod.yml up -d
```
- 상태 확인: 브라우저에서 접속 → 정상 로그인되면 적용 완료. `GET /api/license` 로도 확인 가능.
- 만료 30일 전부터 상단에 갱신 안내 배너가 표시됩니다.

---

## 5. 갱신 / 변경

- **연장·인원 변경**: 새 값으로 다시 `issue` → 새 문자열을 고객 `.env`의 `LICENSE_KEY`에 교체 후 재기동. (같은 host면 됨)
- **정지**: 라이선스를 갱신해주지 않으면 만료일에 자동으로 차단됩니다.

---

## 6. 보안 주의

- **비공개 키는 절대 노출 금지.** 유출되면 누구나 유효 라이선스를 위조할 수 있습니다.
- 비공개 키는 서버·저장소·고객 어디에도 두지 말고, 공급사 내부의 안전한 곳(암호관리자/오프라인)에 보관하세요.
- 비공개 키 분실 시 새 키 쌍 생성 + 앱 재배포가 필요하며 기존 라이선스는 모두 무효가 됩니다.

---

## 7. 문제 해결

| 고객 증상 | 원인 | 조치 |
|-----------|------|------|
| 로그인 등 모든 기능 차단 | 키 미설정/만료/위조 | `LICENSE_KEY` 확인, 만료면 재발급 |
| "설치처(host) 불일치" | `LICENSE_HOST` ≠ 발급 host | 값 일치시키거나 올바른 host로 재발급 |
| 사용자 추가 시 "최대 사용자 수 도달" | maxUsers 초과 | 인원 상향해 재발급 |

---

### 요약
1. (완료) 키 쌍 존재 — 공개키 내장, 비공개키 `LICENSE-PRIVATE-KEY.txt`
2. `gradlew :backend:compileJava`
3. `LicenseTool issue "<비공개키>" "회사명" 만료일 최대인원 host`
4. 출력 문자열 → 고객 `.env` `LICENSE_KEY` + `LICENSE_HOST` → 재기동
