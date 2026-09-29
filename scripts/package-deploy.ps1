# =====================================================================
# 소스 없는 "런타임 전용" 배포 패키지 생성
#   서버에는 난독화된 app.jar + 도커 설정만 올라갑니다 (소스 미포함).
#
# 사용:
#   1) (사전) 프론트+백엔드 난독화 빌드 & app.jar 교체
#        cd <프로젝트 실제 경로>
#        .\gradlew.bat :backend:bootJarObf
#        copy backend\build\libs\backend-obf.jar backend\app.jar
#   2) .\scripts\package-deploy.ps1
#        → 바탕화면(기본)에 annual-leave-deploy.tar.gz 생성
# =====================================================================
param(
  [string]$Out = "$env:USERPROFILE\Desktop\annual-leave-deploy.tar.gz"
)
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot\..").Path
$appJar = Join-Path $root "backend\app.jar"
if (-not (Test-Path $appJar)) {
  throw "backend\app.jar 가 없습니다. 먼저 난독화 빌드로 app.jar 를 생성하세요 (OBFUSCATION.md 참고)."
}

$stageParent = Join-Path $env:TEMP ("aldeploy_" + [System.IO.Path]::GetRandomFileName())
$stage = Join-Path $stageParent "annual-leave"
New-Item -ItemType Directory -Force -Path (Join-Path $stage "backend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage "frontend") | Out-Null

# 런타임에 필요한 파일만 복사 (소스/빌드파일 제외)
Copy-Item (Join-Path $root "docker-compose.prod.yml") $stage
Copy-Item (Join-Path $root ".env.prod.example")       $stage
if (Test-Path (Join-Path $root "DEPLOY.md")) { Copy-Item (Join-Path $root "DEPLOY.md") $stage }
# 리눅스 실행 스크립트(LF 보장) — install/시크릿 암호화·복호화
foreach ($shName in @("install.sh","secrets-encrypt.sh","secrets-decrypt.sh")) {
  $src = Join-Path $root $shName
  if (Test-Path $src) {
    $sh = [System.IO.File]::ReadAllText($src) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText((Join-Path $stage $shName), $sh)
  }
}
Copy-Item (Join-Path $root "backend\Dockerfile")  (Join-Path $stage "backend")
Copy-Item $appJar                                  (Join-Path $stage "backend")
Copy-Item (Join-Path $root "frontend\Dockerfile") (Join-Path $stage "frontend")
Copy-Item (Join-Path $root "frontend\Caddyfile")  (Join-Path $stage "frontend")

if (Test-Path $Out) { Remove-Item $Out -Force }
tar -czf $Out -C $stageParent annual-leave
Remove-Item $stageParent -Recurse -Force

Write-Host "생성됨: $Out"
Write-Host "--- 포함 파일 ---"
tar -tzf $Out
$src = (tar -tzf $Out | Select-String -Pattern "\.java$|\.tsx?$|build\.gradle|proguard|src/|gradlew|\.kts$")
if ($src) { Write-Warning "소스 파일이 포함되었습니다! 확인 필요:"; $src } else { Write-Host "소스 파일 없음 (OK)" }
