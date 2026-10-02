# =====================================================================
# 스모크 테스트가 생성한 QA_* 테스트 데이터 정리 (실사용자/운영데이터 보존)
#   smoke-test.ps1 실행 후 로컬/테스트 DB에서만 사용하세요.
#   FK 의존 순서대로 삭제합니다(자식 → 부모).
#
# 사용: .\scripts\smoke-cleanup.ps1
# =====================================================================
param(
  [string]$PsqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe",
  [string]$DbUser   = "leave",
  [string]$DbName   = "annual_leave",
  [string]$DbHost   = "localhost",
  [string]$DbPass   = "leave1234"
)
$ErrorActionPreference = 'Stop'
$env:PGPASSWORD = $DbPass

$qa = "(SELECT id FROM employees WHERE email LIKE 'qa.%@test.local')"
$stmts = @(
  "DELETE FROM leave_requests WHERE employee_id IN $qa OR approver_id IN $qa OR lead_approver_id IN $qa;",
  "DELETE FROM leave_balances  WHERE employee_id IN $qa;",
  "DELETE FROM notifications   WHERE employee_id IN $qa;",
  # 실제 인사관리자 등이 받은 QA 신청 관련 알림
  "DELETE FROM notifications   WHERE message LIKE '%QA\_%';",
  "DELETE FROM calendar_events WHERE employee_id IN $qa OR created_by IN $qa;",
  "UPDATE departments SET lead_id = NULL WHERE lead_id IN $qa;",
  "DELETE FROM employee_roles  WHERE employee_id IN $qa;",
  "DELETE FROM employees       WHERE email LIKE 'qa.%@test.local';",
  "DELETE FROM audit_logs      WHERE actor_name IN ('QA_Lead','QA_Emp','QA_HR');",
  "DELETE FROM blackout_periods WHERE name LIKE 'QA%';",
  "DELETE FROM departments     WHERE name IN ('QA_Sub','QA_Team');"
)
foreach ($s in $stmts) { & $PsqlPath -U $DbUser -h $DbHost -d $DbName -c $s | Out-Null }

Write-Host "QA 테스트 데이터 정리 완료. 남은 사용자:"
& $PsqlPath -U $DbUser -h $DbHost -d $DbName -c "SELECT email, system_account FROM employees ORDER BY id;"
