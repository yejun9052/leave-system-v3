-- 권한은 한 사람당 하나: employee_roles(여러 개) → employees.role(하나).
--   상위 권한이 하위 권한의 기능을 모두 쓴다(SYSTEM_ADMIN > HR_ADMIN > TEAM_LEAD > EMPLOYEE, 코드의 RoleHierarchy).
--   기존 데이터는 가진 권한 중 가장 높은 것 하나만 남긴다.
ALTER TABLE employees ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'EMPLOYEE'
    CHECK (role IN ('SYSTEM_ADMIN', 'HR_ADMIN', 'TEAM_LEAD', 'EMPLOYEE'));

UPDATE employees e SET role = top.role
FROM (
    SELECT DISTINCT ON (employee_id) employee_id, role
    FROM employee_roles
    ORDER BY employee_id,
             CASE role WHEN 'SYSTEM_ADMIN' THEN 0 WHEN 'HR_ADMIN' THEN 1 WHEN 'TEAM_LEAD' THEN 2 ELSE 3 END
) top
WHERE e.id = top.employee_id;

DROP TABLE employee_roles;
