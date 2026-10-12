-- 공가: 연차와 무관(차감·소진 조건 없음), 경조사처럼 규정을 골라 신청한다. 기본 규정은 "예비군 1일".
--   이미 설치된 DB만 바꾼다. 새로 설치하면 이 시점에 휴가 종류가 비어 있어 아무것도 하지 않고,
--   시작할 때 LeaveDataInitializer 가 공가(연차와 무관)와 예비군 규정을 함께 만든다.
UPDATE leave_types
SET annual_deduction_mode = 'NONE', deduct_from_annual = FALSE, requires_annual_exhausted = FALSE
WHERE code = 'OFFICIAL';

INSERT INTO special_leave_rules (name, days, leave_type_code, sort_order)
SELECT '예비군', 1.0, 'OFFICIAL', 1
WHERE EXISTS (SELECT 1 FROM leave_types WHERE code = 'OFFICIAL')
  AND NOT EXISTS (SELECT 1 FROM special_leave_rules WHERE leave_type_code = 'OFFICIAL');
