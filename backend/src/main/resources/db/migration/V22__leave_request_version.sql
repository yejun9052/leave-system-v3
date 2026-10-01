-- 휴가 신청 낙관적 잠금(@Version). 팀장과 인사관리자가 같은 신청을 동시에 처리하면 한쪽만 반영되고
-- 늦은 쪽은 "이미 처리된 신청입니다"(409)를 받는다. 비차감 휴가(병가·경조사 등)도 캘린더·알림이 두 번 생기지 않는다.
ALTER TABLE leave_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
