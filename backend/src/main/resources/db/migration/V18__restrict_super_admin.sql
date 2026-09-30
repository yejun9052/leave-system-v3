-- 일반 직원이 가진 시스템 관리자 권한 회수
DELETE FROM employee_roles
 WHERE role = 'SUPER_ADMIN'
   AND employee_id IN (SELECT id FROM employees WHERE system_account = FALSE);

-- 관리 전용 계정은 시스템 관리자 권한만
DELETE FROM employee_roles
 WHERE role <> 'SUPER_ADMIN'
   AND employee_id IN (SELECT id FROM employees WHERE system_account = TRUE);
