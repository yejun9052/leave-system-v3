# Local/test DB only: sets QA account passwords directly in the DB (docker required)
$base="http://localhost:8080/api"
$DbContainer = "annual-leave-db"
$DbUser = "leave"
$DbName = "annual_leave"
$pass=0; $fail=0
function Check($name, $cond, $extra="") {
  if ($cond) { $script:pass++; "  [PASS] $name $extra" }
  else { $script:fail++; "  [FAIL] $name $extra" }
}
$baseUri = [Uri]$base
function XsrfToken($sess) {
  $c = $sess.Cookies.GetCookies($baseUri) | ? { $_.Name -eq "XSRF-TOKEN" } | Select-Object -First 1
  if ($c) { return $c.Value }
  return $null
}
function NewSession() {
  $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  try { Invoke-WebRequest -Uri "$base/auth/csrf" -WebSession $s -UseBasicParsing | Out-Null } catch {}
  return $s
}
function Req($method, $url, $sess, $body=$null) {
  try {
    if ($sess -eq $null) { $sess = NewSession }
    $h = @{}
    if ($method -ne "GET") { $tok = XsrfToken $sess; if ($tok) { $h["X-XSRF-TOKEN"] = $tok } }
    $p = @{ Method=$method; Uri=$url; Headers=$h; WebSession=$sess; UseBasicParsing=$true }
    if ($body -ne $null) { $p.ContentType="application/json;charset=utf-8"; $p.Body=$body }
    $r = Invoke-WebRequest @p
    return @{ ok=$true; status=[int]$r.StatusCode; data=($r.Content | ConvertFrom-Json) }
  } catch {
    $resp=$_.Exception.Response; $code=0; $b=$null
    if ($resp) { $code=[int]$resp.StatusCode; try { $sr=New-Object IO.StreamReader($resp.GetResponseStream()); $b=($sr.ReadToEnd()|ConvertFrom-Json) } catch {} }
    return @{ ok=$false; status=$code; data=$b }
  }
}
function Login($email,$pw) {
  $s = NewSession
  $r = Req POST "$base/auth/login" $s (@{email=$email;password=$pw}|ConvertTo-Json)
  if (-not $r.ok) { return $null }
  try { Invoke-WebRequest -Uri "$base/auth/csrf" -WebSession $s -UseBasicParsing | Out-Null } catch {}
  return $s
}

# dates relative to base monday $mon (chosen after admin login, see below)
function D($n) { return $mon.AddDays($n).ToString("yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture) }

# BCrypt hash of qatest1234! (single quotes: no $ interpolation)
$QaHash = '$2a$10$O85JOhayUuxPLKsejBBat.3j17Brhut6tH8Lo4.wT7eDsYPNhS3Ve'
function SetQaPassword($email) {
  $sql = "UPDATE employees SET password_hash='" + $QaHash + "', password_change_required=false WHERE email='" + $email + "'"
  $out = docker exec $DbContainer psql -U $DbUser -d $DbName -c $sql
  Write-Host "  [INFO] set password $email : $out"
}
"===== 1. Auth / RBAC ====="
$admin = Login "admin" "admin1234!"
Check "admin login" ($admin -ne $null)
# pick base monday: first Monday on/after today+14 whose used dates avoid blackouts, holidays and weekends
$blackouts = @((Req GET "$base/policy/blackouts" $admin).data.data)
$holidays = @(docker exec $DbContainer psql -U $DbUser -d $DbName -Atc "select holiday_date from holidays" | ? { $_ -match '^\d{4}-\d{2}-\d{2}$' })
$offsets = @(0,1,3,21,22,23,24,25,42,43,44)
$mon = (Get-Date).Date.AddDays(14)
while ($mon.DayOfWeek -ne [DayOfWeek]::Monday) { $mon = $mon.AddDays(1) }
$found = $false
for ($try = 0; $try -lt 52; $try++) {
  $conflict = $false
  foreach ($o in $offsets) {
    $day = $mon.AddDays($o); $ds = D $o
    if ($day.DayOfWeek -eq [DayOfWeek]::Saturday -or $day.DayOfWeek -eq [DayOfWeek]::Sunday) { $conflict = $true }
    if ($holidays -contains $ds) { $conflict = $true }
    foreach ($bo0 in $blackouts) {
      if ($bo0) {
        $bs = ([datetime]$bo0.startDate).ToString("yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
        $be = ([datetime]$bo0.endDate).ToString("yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
        if ($ds -ge $bs -and $ds -le $be) { $conflict = $true }
      }
    }
  }
  if (-not $conflict) { $found = $true; break }
  $mon = $mon.AddDays(7)
}
if (-not $found) { "[ERROR] no free week"; exit 1 }
"  [INFO] base monday = $($mon.ToString('yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture))"
$bad = Req POST "$base/auth/login" $null (@{email="admin";password="nope"}|ConvertTo-Json)
Check "wrong password -> 401" ($bad.status -eq 401) "(status=$($bad.status))"
$me = Req GET "$base/auth/me" $admin
Check "me = SYSTEM_ADMIN" ($me.data.data.roles -contains "SYSTEM_ADMIN")

"===== 2. Department create / move / cycle ====="
$rootId = (Req GET "$base/departments" $admin).data.data[0].id
$qa = (Req POST "$base/departments" $admin (@{name="QA_Team";parentId=$rootId}|ConvertTo-Json)).data.data
Check "create dept" ($qa.id -ne $null)
$qaSub = (Req POST "$base/departments" $admin (@{name="QA_Sub";parentId=$qa.id}|ConvertTo-Json)).data.data
Check "create sub dept" ($qaSub.id -ne $null)
$moveBad = Req PATCH "$base/departments/$($qa.id)/move" $admin (@{newParentId=$qaSub.id}|ConvertTo-Json)
Check "cycle move blocked" ($moveBad.status -eq 400 -and $moveBad.data.error.code -eq "DEPARTMENT_CYCLE") "(status=$($moveBad.status))"

"===== 3. Employee create + seniority accrual ====="
$leadBody = @{email="qa.lead@test.local";name="QA_Lead";departmentId=$qa.id;position="lead";hireDate="2019-03-01";roles=@("TEAM_LEAD")}|ConvertTo-Json
$lead = (Req POST "$base/employees" $admin $leadBody).data.data
Check "create team lead" ($lead.id -ne $null)
$empBody = @{email="qa.emp@test.local";name="QA_Emp";departmentId=$qa.id;position="staff";hireDate="2024-01-02";roles=@("EMPLOYEE")}|ConvertTo-Json
$emp = (Req POST "$base/employees" $admin $empBody).data.data
Check "create staff" ($emp.id -ne $null)
$hrBody = @{email="qa.hr@test.local";name="QA_HR";departmentId=$rootId;position="hr";hireDate="2022-01-03";roles=@("HR_ADMIN","EMPLOYEE")}|ConvertTo-Json
$hr = (Req POST "$base/employees" $admin $hrBody).data.data
Check "create HR admin" ($hr.id -ne $null)
$superBody = @{email="qa.super@test.local";name="QA_Super";departmentId=$qa.id;position="x";hireDate="2022-01-03";roles=@("SYSTEM_ADMIN")}|ConvertTo-Json
$superRes = Req POST "$base/employees" $admin $superBody
Check "SYSTEM_ADMIN cannot be granted to employee -> 400" ($superRes.status -eq 400 -and $superRes.data.error.code -eq "SYSTEM_ADMIN_ROLE_RESTRICTED") "(status=$($superRes.status))"
SetQaPassword "qa.lead@test.local"
SetQaPassword "qa.emp@test.local"
SetQaPassword "qa.hr@test.local"
Req PUT "$base/departments/$($qa.id)" $admin (@{name="QA_Team";leadId=$lead.id;sortOrder=0}|ConvertTo-Json) | Out-Null
$leadBal = (Req GET "$base/leave-requests/balances/$($lead.id)" $admin).data.data
$empBal = (Req GET "$base/leave-requests/balances/$($emp.id)" $admin).data.data
Check "lead seniority (2019 -> 18)" ($leadBal.granted -eq 18) "(granted=$($leadBal.granted))"
Check "staff grant (2024 -> 15)" ($empBal.granted -eq 15) "(granted=$($empBal.granted))"

"===== 4. Request -> single approval -> balance/calendar ====="
$empH = Login "qa.emp@test.local" "qatest1234!"
$leadH = Login "qa.lead@test.local" "qatest1234!"
$hrH = Login "qa.hr@test.local" "qatest1234!"
$annualId = ((Req GET "$base/leave-types" $empH).data.data | ? {$_.code -eq "ANNUAL"}).id
$route = (Req GET "$base/leave-requests/approval-route" $empH).data.data
Check "approval route -> LEAD QA_Lead" ($route.approverKind -eq "LEAD" -and $route.leadName -eq "QA_Lead") "(kind=$($route.approverKind) lead=$($route.leadName))"
$reqBody = @{leaveTypeId=$annualId;startDate=(D 0);endDate=(D 1);reason="qa annual"}|ConvertTo-Json
$lr = (Req POST "$base/leave-requests" $empH $reqBody).data.data
Check "annual request (2d, PENDING)" ($lr.status -eq "PENDING" -and $lr.days -eq 2) "(days=$($lr.days))"
$pend = (Req GET "$base/leave-requests/pending" $leadH).data.data
Check "shows in lead inbox" (($pend | ? {$_.id -eq $lr.id}) -ne $null)
$sysPend = Req GET "$base/leave-requests/pending" $admin
Check "system admin sees approval inbox (= HR)" ($sysPend.status -eq 200 -and (($sysPend.data.data | ? {$_.id -eq $lr.id}) -ne $null)) "(status=$($sysPend.status))"
$appr = (Req POST "$base/leave-requests/$($lr.id)/approve" $leadH).data.data
Check "lead approve -> APPROVED (single step)" ($appr.status -eq "APPROVED" -and $appr.approverName -eq "QA_Lead") "(status=$($appr.status))"
$again = Req POST "$base/leave-requests/$($lr.id)/approve" $hrH
Check "second approval -> 409 already processed" ($again.status -eq 409) "(status=$($again.status))"
$empBal2 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance used=2" ($empBal2.used -eq 2) "(used=$($empBal2.used))"
$cal = (Req GET "$base/calendar/events?start=$(D -1)&end=$(D 7)" $empH).data.data
Check "calendar reflects leave" (($cal | ? {$_.source -eq "LEAVE_REQUEST" -and $_.title -like "*QA_Emp*"}) -ne $null)

"===== 5. Cancel request workflow ====="
$c1 = (Req POST "$base/leave-requests/$($lr.id)/cancel" $empH (@{reason="change"}|ConvertTo-Json)).data.data
Check "cancel request -> CANCEL_REQUESTED" ($c1.status -eq "CANCEL_REQUESTED")
$empBalC = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance kept during cancel-req (used=2)" ($empBalC.used -eq 2)
$rej = (Req POST "$base/leave-requests/$($lr.id)/cancel/reject" $leadH (@{reason="need at work"}|ConvertTo-Json)).data.data
Check "lead rejects cancel request -> back to APPROVED" ($rej.status -eq "APPROVED")
$c2 = (Req POST "$base/leave-requests/$($lr.id)/cancel" $empH (@{reason="again"}|ConvertTo-Json)).data.data
Check "re cancel request" ($c2.status -eq "CANCEL_REQUESTED")
$capp = (Req POST "$base/leave-requests/$($lr.id)/cancel/approve" $hrH).data.data
Check "HR approves cancel -> CANCELLED" ($capp.status -eq "CANCELLED")
$empBal3 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance restored (used=0)" ($empBal3.used -eq 0) "(used=$($empBal3.used))"
$cal2 = (Req GET "$base/calendar/events?start=$(D -1)&end=$(D 7)" $empH).data.data
Check "calendar cleared" (($cal2 | ? {$_.title -like "*QA_Emp*"}) -eq $null)

"===== 6. Half day / self approval ====="
$amId = ((Req GET "$base/leave-types" $empH).data.data | ? {$_.code -eq "HALF_AM"}).id
$half = (Req POST "$base/leave-requests" $empH (@{leaveTypeId=$amId;startDate=(D 3);endDate=(D 3)}|ConvertTo-Json)).data.data
Check "half-day request (0.5d)" ($half.days -eq 0.5) "(days=$($half.days))"
Req POST "$base/leave-requests/$($half.id)/approve" $leadH | Out-Null
$empBal4 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "half-day used=0.5" ($empBal4.used -eq 0.5) "(used=$($empBal4.used))"
$hrOwn = (Req POST "$base/leave-requests" $hrH (@{leaveTypeId=$amId;startDate=(D 4);endDate=(D 4)}|ConvertTo-Json)).data.data
$hrSelf = (Req POST "$base/leave-requests/$($hrOwn.id)/approve" $hrH).data.data
Check "HR self approval -> APPROVED" ($hrSelf.status -eq "APPROVED")
$leadRoute = (Req GET "$base/leave-requests/approval-route" $leadH).data.data
$leadSelf = (Req POST "$base/leave-requests" $leadH (@{leaveTypeId=$amId;startDate=(D 4);endDate=(D 4)}|ConvertTo-Json)).data.data
$leadSelfAppr = Req POST "$base/leave-requests/$($leadSelf.id)/approve" $leadH
# QA_Team 의 상위 부서에 부서장이 있으면 상위 팀장 결재(본인 403), 없으면 최상위 팀장이라 자가 승인
$expectSelf = $leadRoute.approverKind -eq "SELF"
Check "lead self approval follows route ($($leadRoute.approverKind))" (($expectSelf -and $leadSelfAppr.data.data.status -eq "APPROVED") -or (-not $expectSelf -and $leadSelfAppr.status -eq 403)) "(status=$($leadSelfAppr.status))"

"===== 6b. Force register / force cancel / concurrent approval ====="
# 지난 근무일(주말·공휴일이면 하루씩 앞으로)에 강제 등록
$reg = $null; $pastDay = (Get-Date).Date.AddDays(-20)
for ($i = 0; $i -lt 10 -and -not ($reg -and $reg.ok); $i++) {
  $d = $pastDay.AddDays(-$i).ToString("yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
  $reg = Req POST "$base/leave-requests/register" $hrH (@{employeeId=$emp.id;leaveTypeId=$annualId;startDate=$d;endDate=$d;reason="qa register"}|ConvertTo-Json)
}
Check "HR force register past day -> APPROVED" ($reg.ok -and $reg.data.data.status -eq "APPROVED") "(status=$($reg.status) $($reg.data.error.message))"
$leadReg = Req POST "$base/leave-requests/register" $leadH (@{employeeId=$emp.id;leaveTypeId=$annualId;startDate=(D 8);endDate=(D 8)}|ConvertTo-Json)
Check "lead cannot force register -> 403" ($leadReg.status -eq 403) "(status=$($leadReg.status))"
$empBal5 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
$noReason = Req POST "$base/leave-requests/$($reg.data.data.id)/cancel" $hrH (@{}|ConvertTo-Json)
Check "force cancel without reason -> 400" ($noReason.status -eq 400) "(status=$($noReason.status))"
$empCancelPast = Req POST "$base/leave-requests/$($reg.data.data.id)/cancel" $empH (@{reason="x"}|ConvertTo-Json)
Check "staff cannot cancel started leave -> 409" ($empCancelPast.status -eq 409) "(status=$($empCancelPast.status))"
$fc = (Req POST "$base/leave-requests/$($reg.data.data.id)/cancel" $hrH (@{reason="qa force cancel"}|ConvertTo-Json)).data.data
Check "HR force cancel past leave -> CANCELLED" ($fc.status -eq "CANCELLED" -and $fc.cancelReason -eq "qa force cancel")
$empBal6 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "force cancel restores balance" ($empBal6.used -eq ($empBal5.used - 1)) "(before=$($empBal5.used) after=$($empBal6.used))"

# 같은 신청을 팀장과 인사관리자가 동시에 승인 → 한 번만 처리
function PostAsync($sess, $url) {
  $h = New-Object System.Net.Http.HttpClientHandler
  $h.CookieContainer = $sess.Cookies
  $c = New-Object System.Net.Http.HttpClient($h)
  $c.DefaultRequestHeaders.Add("X-XSRF-TOKEN", (XsrfToken $sess))
  return $c.PostAsync($url, (New-Object System.Net.Http.StringContent("")))
}
Add-Type -AssemblyName System.Net.Http
$condId = ((Req GET "$base/leave-types" $empH).data.data | ? {$_.code -eq "CONDOLENCE"})
$raceOk = $true; $codes = @()
for ($t = 0; $t -lt 3; $t++) {
  $body = @{leaveTypeId=$condId.id;startDate=(D (49 + $t));endDate=(D (49 + $t));reason="qa race"}
  if ($condId.specialRules.Count -gt 0) { $body.specialRuleId = $condId.specialRules[0].id }
  $race = (Req POST "$base/leave-requests" $empH ($body|ConvertTo-Json)).data.data
  if (-not $race) { $raceOk = $false; break }
  $t1 = PostAsync $leadH "$base/leave-requests/$($race.id)/approve"
  $t2 = PostAsync $hrH "$base/leave-requests/$($race.id)/approve"
  [System.Threading.Tasks.Task]::WaitAll(@($t1, $t2))
  $s = @([int]$t1.Result.StatusCode, [int]$t2.Result.StatusCode) | Sort-Object
  $codes += ($s -join "/")
  $ev = (Req GET "$base/calendar/events?start=$(D (49 + $t))&end=$(D (49 + $t))" $empH).data.data | ? { $_.source -eq "LEAVE_REQUEST" -and $_.title -like "*QA_Emp*" }
  if (-not ($s[0] -eq 200 -and $s[1] -eq 409 -and @($ev).Count -eq 1)) { $raceOk = $false }
}
Check "concurrent approvals: one 200, one 409, one calendar event" $raceOk "(codes=$($codes -join ', '))"

"===== 7. Usage control - blackout ====="
$bo = (Req POST "$base/policy/blackouts" $admin (@{startDate=(D 21);endDate=(D 25);name="QA_Blackout"}|ConvertTo-Json)).data.data
Check "create blackout" ($bo.id -ne $null)
$blocked = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate=(D 22);endDate=(D 23)}|ConvertTo-Json)
Check "request in blackout blocked" ($blocked.status -eq 409 -and $blocked.data.error.code -eq "LEAVE_BLACKOUT") "(status=$($blocked.status))"
Req DELETE "$base/policy/blackouts/$($bo.id)" $admin | Out-Null
$okAfter = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate=(D 22);endDate=(D 23)}|ConvertTo-Json)
Check "request ok after blackout removed" ($okAfter.ok -eq $true)
if ($okAfter.ok) {
  # 화면과 같이 JSON 본문을 보낸다(본문 없이 보내면 취소되지 않고 PENDING 으로 남았음)
  $cx = Req POST "$base/leave-requests/$($okAfter.data.data.id)/cancel" $empH (@{reason="qa"}|ConvertTo-Json)
  Check "cancel pending request -> CANCELLED" ($cx.data.data.status -eq "CANCELLED") "(status=$($cx.status))"
}

"===== 8. Overlap prevention ====="
Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate=(D 42);endDate=(D 43)}|ConvertTo-Json) | Out-Null
$dup = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate=(D 43);endDate=(D 44)}|ConvertTo-Json)
Check "overlap blocked" ($dup.status -eq 409 -and $dup.data.error.code -eq "LEAVE_DATE_OVERLAP") "(status=$($dup.status))"

"===== 9. RBAC isolation ====="
$forbidden = Req GET "$base/employees" $empH
Check "staff cannot list employees (403)" ($forbidden.status -eq 403) "(status=$($forbidden.status))"
$forbidden2 = Req GET "$base/audit-logs" $empH
Check "staff cannot read audit (403)" ($forbidden2.status -eq 403) "(status=$($forbidden2.status))"

"===== 10. Policy / rules / promotion / report ====="
$pol = (Req GET "$base/policy" $admin).data.data
Check "policy seniority fields" ($pol.baseAnnualDays -eq 15 -and $pol.maxAnnualDays -eq 25)
$awards = (Req GET "$base/policy/award-rules" $admin).data.data
Check "award rules >=3" ($awards.Count -ge 3)
$specials = (Req GET "$base/policy/special-rules" $admin).data.data
Check "special rules >=5" ($specials.Count -ge 5)
$targets = (Req GET "$base/leave/promotion/targets" $admin).data.data
Check "promotion targets" ($targets -ne $null)
$rep = Req GET "$base/reports/leave-usage/export" $admin
Check "usage report xlsx" ($rep.ok -eq $true)

"===== 11. Audit log labels ====="
$logs = (Req GET "$base/audit-logs?size=150" $admin).data.data.content
$acts = $logs | % { $_.action }
Check "audit: approve" ($acts -contains "approve")
Check "audit: cancel" ($acts -contains "cancel")
Check "audit: cancel_reject" ($acts -contains "cancel_reject")
Check "audit: cancel_approve" ($acts -contains "cancel_approve")
Check "audit: self_approve" ($acts -contains "self_approve")
Check "audit: force_cancel" ($acts -contains "force_cancel")
Check "audit: register" ($acts -contains "register")
Check "audit: move failure recorded" (($logs | ? {$_.action -eq "move" -and $_.success -eq $false}) -ne $null)

"`n========================================"
"RESULT: PASS=$pass  FAIL=$fail"
"========================================"
