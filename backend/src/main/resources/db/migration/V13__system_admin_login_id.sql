-- =====================================================================
-- V13: 관리 전용 계정(system_account) 로그인 아이디를 이메일 대신 'admin' 으로
--   관리 전용 계정은 직원이 아니므로(연차·휴가·인원 집계 제외) 회사 이메일 대신 아이디로 로그인한다.
--   스키마 변경 없음. 기존 기본값 'admin@company.com' 인 경우에만 바꾼다(운영에서 이미 바꾼 값은 유지).
-- =====================================================================

UPDATE employees
   SET email = 'admin'
 WHERE system_account = TRUE
   AND email = 'admin@company.com'
   AND NOT EXISTS (SELECT 1 FROM employees WHERE email = 'admin');
