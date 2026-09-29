-- =====================================================================
-- V7: 기본 제공 시스템 관리자 계정 숨김 플래그 (additive)
--   system_account = TRUE 인 계정은 사용자(계정) 목록/내보내기/단건조회에서 제외된다.
-- =====================================================================

ALTER TABLE employees ADD COLUMN system_account BOOLEAN NOT NULL DEFAULT FALSE;

-- 시드로 생성된 기본 시스템 관리자에 플래그 지정
UPDATE employees SET system_account = TRUE
 WHERE email = 'admin@company.com' OR employee_no = 'ADMIN';
