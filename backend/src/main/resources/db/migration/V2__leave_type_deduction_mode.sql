-- 휴가 종류의 연차 차감 방식을 선택지 하나로 합친다.
--   DEDUCT        연차처럼 차감
--   EXHAUST_FIRST 회사 규정: 연차 먼저 소진(사용 가능 연차 1일 미만일 때만 신청, 승인 때 남은 연차 소멸)
--   NONE          법정 기준: 연차와 무관(차감 없음)
-- 예전 두 열(deduct_from_annual, requires_annual_exhausted)은 지우지 않는다. 앱은 새 열만 읽고 두 열은 새 값에 맞춰 계속 저장한다.
-- 두 열이 모두 TRUE 인 모순 상태는 DEDUCT 로 본다(적용 전 로컬 DB 0건).

ALTER TABLE leave_types ADD COLUMN annual_deduction_mode VARCHAR(20);

UPDATE leave_types
   SET annual_deduction_mode = CASE
           WHEN deduct_from_annual THEN 'DEDUCT'
           WHEN requires_annual_exhausted THEN 'EXHAUST_FIRST'
           ELSE 'NONE'
       END;

ALTER TABLE leave_types ALTER COLUMN annual_deduction_mode SET NOT NULL;
