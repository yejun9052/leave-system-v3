-- 반반차(QUARTER) 단위를 없앤다. 반반차는 시간차 2시간(0.25일)으로 쓴다.
-- 남아 있는 반반차 종류를 지우고, 그 종류의 신청 기록은 시간차로 옮긴다(기록 일수 0.25 = 시간차 2시간, 차감 일수는 그대로).
-- 적용 전 로컬 DB: 반반차 종류 1건(비활성), 신청 0건.

-- 1) 반반차 신청을 시간차 종류로 옮긴다(시간차 종류가 있을 때)
UPDATE leave_requests
   SET leave_type_id = (SELECT MIN(id) FROM leave_types WHERE portion = 'HOURLY')
 WHERE leave_type_id IN (SELECT id FROM leave_types WHERE portion = 'QUARTER')
   AND EXISTS (SELECT 1 FROM leave_types WHERE portion = 'HOURLY');

-- 2) 반반차 종류에 걸린 경조사 규정은 연결만 끊는다
UPDATE special_leave_rules
   SET leave_type_code = NULL
 WHERE leave_type_code IN (SELECT code FROM leave_types WHERE portion = 'QUARTER');

-- 3) 신청이 없는 반반차 종류는 지운다
DELETE FROM leave_types t
 WHERE t.portion = 'QUARTER'
   AND NOT EXISTS (SELECT 1 FROM leave_requests r WHERE r.leave_type_id = t.id);

-- 4) 그래도 남은 것(시간차 종류가 없어 신청을 못 옮긴 경우)은 그 종류를 시간차로 바꾼다. 1회 차감을 1시간당으로(0.25 → 0.125)
UPDATE leave_types
   SET portion = 'HOURLY',
       deduct_days = deduct_days / 2
 WHERE portion = 'QUARTER';
