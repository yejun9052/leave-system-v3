-- 팀장(TEAM_LEAD) 권한이 있는 재직자를 자기 부서의 부서장으로 본다(이후 저장은 DepartmentLeadSync 가 맞춘다).
-- 기존 데이터: 부서장이 비어 있는 부서에 그 부서 소속 재직 팀장을 지정한다. 여러 명이면 먼저 등록된 사람(가장 작은 id).
-- 이미 부서장이 지정된 부서는 그대로 둔다.
UPDATE departments d
   SET lead_id = (
       SELECT e.id
         FROM employees e
         JOIN employee_roles r ON r.employee_id = e.id AND r.role = 'TEAM_LEAD'
        WHERE e.department_id = d.id
          AND e.status = 'ACTIVE'
          AND e.system_account = FALSE
        ORDER BY e.id
        LIMIT 1)
 WHERE d.lead_id IS NULL;
