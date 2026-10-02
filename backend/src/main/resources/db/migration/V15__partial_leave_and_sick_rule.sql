-- =====================================================================
-- V15: 반반차·시간차 도입 + 병가·공가 사용 조건
--   1) 일수 컬럼을 소수점 셋째 자리까지(반반차 0.25, 시간차 0.125일 = 1시간)
--   2) 휴가 종류 단위(portion: FULL/HALF/QUARTER/HOURLY)로 half_day 대체
--   3) requires_annual_exhausted: 잔여 연차 1일 미만 + 대기 중 연차 신청 없음일 때만 신청(병가·공가)
--   4) forfeited_days: 병가·공가 승인 때 소멸시킨 남은 연차(취소 시 되돌림)
--   5) 정책: 반반차·시간차 사용 여부(기본 꺼짐)
--   6) 기존 설치에 반반차·시간차 종류 추가(신규 설치는 LeaveDataInitializer 가 생성)
-- =====================================================================

-- 1) 일수 정밀도
ALTER TABLE leave_balances
    ALTER COLUMN granted      TYPE NUMERIC(6,3),
    ALTER COLUMN used         TYPE NUMERIC(6,3),
    ALTER COLUMN carried_over TYPE NUMERIC(6,3),
    ALTER COLUMN expired      TYPE NUMERIC(6,3);
ALTER TABLE leave_requests
    ALTER COLUMN days          TYPE NUMERIC(6,3),
    ALTER COLUMN deducted_days TYPE NUMERIC(6,3);
ALTER TABLE leave_types
    ALTER COLUMN deduct_days TYPE NUMERIC(5,3);

-- 2) 휴가 단위
ALTER TABLE leave_types ADD COLUMN portion VARCHAR(10) NOT NULL DEFAULT 'FULL';
UPDATE leave_types SET portion = 'HALF' WHERE half_day = TRUE;
ALTER TABLE leave_types DROP COLUMN half_day;

-- 3) 병가·공가 사용 조건
ALTER TABLE leave_types ADD COLUMN requires_annual_exhausted BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE leave_types SET requires_annual_exhausted = TRUE WHERE code IN ('SICK', 'OFFICIAL');

-- 4) 소멸 기록
ALTER TABLE leave_requests ADD COLUMN forfeited_days NUMERIC(6,3) NOT NULL DEFAULT 0;

-- 5) 정책
ALTER TABLE leave_policy ADD COLUMN quarter_day_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE leave_policy ADD COLUMN hourly_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 6) 기존 설치(휴가 종류가 이미 있음)에만 추가. 신규 설치는 초기 데이터 생성에서 8종을 함께 만든다.
INSERT INTO leave_types (code, name, deduct_days, paid, portion, deduct_from_annual, color_hex, sort_order)
SELECT v.code, v.name, v.deduct_days, TRUE, v.portion, TRUE, v.color_hex, v.sort_order
  FROM (VALUES ('QUARTER', '반반차', 0.25, 'QUARTER', '#14b8a6', 3),
               ('HOURLY',  '시간차', 0.125, 'HOURLY', '#0ea5e9', 3)) AS v(code, name, deduct_days, portion, color_hex, sort_order)
 WHERE EXISTS (SELECT 1 FROM leave_types)
   AND NOT EXISTS (SELECT 1 FROM leave_types t WHERE t.code = v.code);
