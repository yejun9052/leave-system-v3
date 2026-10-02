-- 권한 이름 변경: SUPER_ADMIN → SYSTEM_ADMIN
UPDATE employee_roles SET role = 'SYSTEM_ADMIN' WHERE role = 'SUPER_ADMIN';

-- 로그인 세션에는 권한이 문자열(ROLE_SUPER_ADMIN)로 저장돼 있어 이름이 바뀌면 권한을 잃는다.
-- 해당 사용자의 세션을 지워 다시 로그인하게 한다(세션 사용자 이름 = 직원 ID, V11).
DELETE FROM spring_session
 WHERE principal_name IN (SELECT r.employee_id::text FROM employee_roles r WHERE r.role = 'SYSTEM_ADMIN');
