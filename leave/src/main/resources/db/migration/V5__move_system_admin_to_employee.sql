-- V4로 발급한 관리자 자격 정보를 사원 테이블의 SYS_ADMIN 행으로 옮긴다.
-- 적용된 V4는 Flyway 이력으로 남기고, 별도 관리자 테이블은 이 이관 후 제거한다.
ALTER TABLE employee
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN email VARCHAR(150) NULL,
    MODIFY COLUMN hire_date DATE NULL,
    ADD COLUMN login_id VARCHAR(80) NULL AFTER email,
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER login_id,
    ADD UNIQUE KEY uk_employee__login_id (login_id);

-- 이전 개발용 시드의 SYS_ADMIN 행은 자격 정보가 없어 로그인에 사용할 수 없다.
-- 감사 참조를 보존하기 위해 삭제하지 않고 비활성화한다.
UPDATE employee
SET active = 0,
    updated_at = NOW(6)
WHERE role = 'SYS_ADMIN';

INSERT INTO employee
    (name, email, login_id, password_hash, hire_date, role, department_id,
     active, created_at, updated_at)
SELECT owner_name, NULL, login_id, password_hash, NULL, 'SYS_ADMIN', NULL,
       active, created_at, updated_at
FROM system_admin_account;

-- 여러 운영자가 필요해질 때까지 활성 SYS_ADMIN은 DB에서도 한 계정만 허용한다.
ALTER TABLE employee
    ADD COLUMN active_sys_admin_slot TINYINT
        GENERATED ALWAYS AS (CASE WHEN role = 'SYS_ADMIN' AND active = 1 THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_employee__single_active_sys_admin (active_sys_admin_slot),
    ADD CONSTRAINT chk_employee__system_admin_fields CHECK (
        (role = 'SYS_ADMIN' AND
            (active = 0 OR
             (email IS NULL AND hire_date IS NULL AND login_id IS NOT NULL AND password_hash IS NOT NULL)))
        OR (role <> 'SYS_ADMIN' AND email IS NOT NULL AND hire_date IS NOT NULL)
    );

DROP TABLE system_admin_account;
