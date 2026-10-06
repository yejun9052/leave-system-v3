-- 종일 단위 휴가 종류를 반차로 신청한 경우의 오전·오후(AM / PM). 0.5일짜리 경조사 규정(예: 생일 반차)에 쓴다.
-- 비어 있으면 휴가 종류의 단위 그대로(기존 신청 모두 해당).

ALTER TABLE leave_requests ADD COLUMN half_day_part VARCHAR(2);
