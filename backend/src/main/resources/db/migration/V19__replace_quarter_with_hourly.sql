-- 반반차(0.25일)를 없애고 시간차(2시간 = 0.25일)로 대체
-- 반반차 종류는 과거 신청 기록이 참조할 수 있어 삭제하지 않고 비활성화한다(신청 목록에서 사라짐).
UPDATE leave_types SET active = FALSE WHERE portion = 'QUARTER';

-- 정책의 반반차 스위치 삭제(서버는 반반차 신청을 항상 막는다)
ALTER TABLE leave_policy DROP COLUMN quarter_day_enabled;
