$base="http://localhost:8080/api"
$pass=0; $fail=0
function Check($name, $cond, $extra="") {
  if ($cond) { $script:pass++; "  [PASS] $name $extra" }
  else { $script:fail++; "  [FAIL] $name $extra" }
}
function Req($method, $url, $headers, $body=$null) {
  try {
    $p = @{ Method=$method; Uri=$url; Headers=$headers; UseBasicParsing=$true }
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
  $r = Req POST "$base/auth/login" @{} (@{email=$email;password=$pw}|ConvertTo-Json)
  if (-not $r.ok) { return $null }
  return @{ Authorization = "Bearer " + $r.data.data.accessToken }
}

"===== 1. Auth / RBAC ====="
$admin = Login "admin@company.com" "admin1234!"
Check "admin login" ($admin -ne $null)
$bad = Req POST "$base/auth/login" @{} (@{email="admin@company.com";password="nope"}|ConvertTo-Json)
Check "wrong password -> 401" ($bad.status -eq 401) "(status=$($bad.status))"
$me = Req GET "$base/auth/me" $admin
Check "me = SUPER_ADMIN" ($me.data.data.roles -contains "SUPER_ADMIN")

"===== 2. Department create / move / cycle ====="
$rootId = (Req GET "$base/departments" $admin).data.data[0].id
$qa = (Req POST "$base/departments" $admin (@{name="QA_Team";parentId=$rootId}|ConvertTo-Json)).data.data
Check "create dept" ($qa.id -ne $null)
$qaSub = (Req POST "$base/departments" $admin (@{name="QA_Sub";parentId=$qa.id}|ConvertTo-Json)).data.data
Check "create sub dept" ($qaSub.id -ne $null)
$moveBad = Req PATCH "$base/departments/$($qa.id)/move" $admin (@{newParentId=$qaSub.id}|ConvertTo-Json)
Check "cycle move blocked" ($moveBad.status -eq 400 -and $moveBad.data.error.code -eq "DEPARTMENT_CYCLE") "(status=$($moveBad.status))"

"===== 3. Employee create + seniority accrual ====="
$leadBody = @{email="qa.lead@test.local";name="QA_Lead";departmentId=$qa.id;position="lead";hireDate="2019-03-01";roles=@("TEAM_LEAD");initialPassword="qatest1234!"}|ConvertTo-Json
$lead = (Req POST "$base/employees" $admin $leadBody).data.data
Check "create team lead" ($lead.id -ne $null)
$empBody = @{email="qa.emp@test.local";name="QA_Emp";departmentId=$qa.id;position="staff";hireDate="2024-01-02";roles=@("EMPLOYEE");initialPassword="qatest1234!"}|ConvertTo-Json
$emp = (Req POST "$base/employees" $admin $empBody).data.data
Check "create staff" ($emp.id -ne $null)
Req PUT "$base/departments/$($qa.id)" $admin (@{name="QA_Team";leadId=$lead.id;sortOrder=0}|ConvertTo-Json) | Out-Null
$leadBal = (Req GET "$base/leave-requests/balances/$($lead.id)" $admin).data.data
$empBal = (Req GET "$base/leave-requests/balances/$($emp.id)" $admin).data.data
Check "lead seniority (2019 -> 18)" ($leadBal.granted -eq 18) "(granted=$($leadBal.granted))"
Check "staff grant (2024 -> 15)" ($empBal.granted -eq 15) "(granted=$($empBal.granted))"

"===== 4. Request -> approve -> balance/calendar ====="
$empH = Login "qa.emp@test.local" "qatest1234!"
$leadH = Login "qa.lead@test.local" "qatest1234!"
$annualId = ((Req GET "$base/leave-types" $empH).data.data | ? {$_.code -eq "ANNUAL"}).id
$reqBody = @{leaveTypeId=$annualId;startDate="2026-07-20";endDate="2026-07-21";reason="qa annual"}|ConvertTo-Json
$lr = (Req POST "$base/leave-requests" $empH $reqBody).data.data
Check "annual request (2d, PENDING)" ($lr.status -eq "PENDING" -and $lr.days -eq 2) "(days=$($lr.days))"
$pend = (Req GET "$base/leave-requests/pending" $leadH).data.data
Check "shows in lead inbox" (($pend | ? {$_.id -eq $lr.id}) -ne $null)
$appr = (Req POST "$base/leave-requests/$($lr.id)/approve" $leadH).data.data
Check "lead approve -> APPROVED" ($appr.status -eq "APPROVED")
$empBal2 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance used=2" ($empBal2.used -eq 2) "(used=$($empBal2.used))"
$cal = (Req GET "$base/calendar/events?start=2026-07-01&end=2026-07-31" $empH).data.data
Check "calendar reflects leave" (($cal | ? {$_.source -eq "LEAVE_REQUEST" -and $_.title -like "*QA_Emp*"}) -ne $null)

"===== 5. Cancel re-approval workflow ====="
$c1 = (Req POST "$base/leave-requests/$($lr.id)/cancel" $empH (@{reason="change"}|ConvertTo-Json)).data.data
Check "cancel request -> CANCEL_REQUESTED" ($c1.status -eq "CANCEL_REQUESTED")
$empBalC = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance kept during cancel-req (used=2)" ($empBalC.used -eq 2)
$rej = (Req POST "$base/leave-requests/$($lr.id)/cancel/reject" $leadH (@{reason="need at work"}|ConvertTo-Json)).data.data
Check "cancel reject -> back to APPROVED" ($rej.status -eq "APPROVED")
$c2 = (Req POST "$base/leave-requests/$($lr.id)/cancel" $empH (@{reason="again"}|ConvertTo-Json)).data.data
Check "re cancel request" ($c2.status -eq "CANCEL_REQUESTED")
$capp = (Req POST "$base/leave-requests/$($lr.id)/cancel/approve" $leadH).data.data
Check "cancel approve -> CANCELLED" ($capp.status -eq "CANCELLED")
$empBal3 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "balance restored (used=0)" ($empBal3.used -eq 0) "(used=$($empBal3.used))"
$cal2 = (Req GET "$base/calendar/events?start=2026-07-01&end=2026-07-31" $empH).data.data
Check "calendar cleared" (($cal2 | ? {$_.title -like "*QA_Emp*"}) -eq $null)

"===== 6. Half day ====="
$amId = ((Req GET "$base/leave-types" $empH).data.data | ? {$_.code -eq "HALF_AM"}).id
$half = (Req POST "$base/leave-requests" $empH (@{leaveTypeId=$amId;startDate="2026-07-23";endDate="2026-07-23"}|ConvertTo-Json)).data.data
Check "half-day request (0.5d)" ($half.days -eq 0.5) "(days=$($half.days))"
Req POST "$base/leave-requests/$($half.id)/approve" $leadH | Out-Null
$empBal4 = (Req GET "$base/leave-requests/balances/me" $empH).data.data
Check "half-day used=0.5" ($empBal4.used -eq 0.5) "(used=$($empBal4.used))"

"===== 7. Usage control - blackout ====="
$bo = (Req POST "$base/policy/blackouts" $admin (@{startDate="2026-08-10";endDate="2026-08-14";name="QA_Blackout"}|ConvertTo-Json)).data.data
Check "create blackout" ($bo.id -ne $null)
$blocked = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate="2026-08-11";endDate="2026-08-12"}|ConvertTo-Json)
Check "request in blackout blocked" ($blocked.status -eq 409 -and $blocked.data.error.code -eq "LEAVE_BLACKOUT") "(status=$($blocked.status))"
Req DELETE "$base/policy/blackouts/$($bo.id)" $admin | Out-Null
$okAfter = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate="2026-08-11";endDate="2026-08-12"}|ConvertTo-Json)
Check "request ok after blackout removed" ($okAfter.ok -eq $true)
if ($okAfter.ok) { Req POST "$base/leave-requests/$($okAfter.data.data.id)/cancel" $empH | Out-Null }

"===== 8. Overlap prevention ====="
Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate="2026-09-01";endDate="2026-09-02"}|ConvertTo-Json) | Out-Null
$dup = Req POST "$base/leave-requests" $empH (@{leaveTypeId=$annualId;startDate="2026-09-02";endDate="2026-09-03"}|ConvertTo-Json)
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
$logs = (Req GET "$base/audit-logs?size=60" $admin).data.data.content
$acts = $logs | % { $_.action }
Check "audit: approve" ($acts -contains "approve")
Check "audit: cancel" ($acts -contains "cancel")
Check "audit: cancel_reject" ($acts -contains "cancel_reject")
Check "audit: cancel_approve" ($acts -contains "cancel_approve")
Check "audit: move failure recorded" (($logs | ? {$_.action -eq "move" -and $_.success -eq $false}) -ne $null)

"`n========================================"
"RESULT: PASS=$pass  FAIL=$fail"
"========================================"
