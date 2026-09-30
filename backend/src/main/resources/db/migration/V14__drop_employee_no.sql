-- =====================================================================
-- V14: 사번(employee_no) 기능 삭제
--   사번 입력·검색·엑셀·리포트 열을 모두 없앴다. 기존 사번 값은 함께 삭제된다(되돌릴 수 없음).
-- =====================================================================

DROP INDEX IF EXISTS uq_employees_employee_no;
ALTER TABLE employees DROP COLUMN IF EXISTS employee_no;
