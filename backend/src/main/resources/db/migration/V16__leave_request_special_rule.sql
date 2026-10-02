-- =====================================================================
-- V16: 경조사 규정을 휴가 신청에 연결
--   경조사처럼 규정(본인 결혼 5일 등)이 있는 종류는 신청 때 규정을 골라야 하고,
--   신청 근무일 수가 규정 일수를 넘을 수 없다. 규정이 나중에 수정·삭제돼도 신청 당시 내용이
--   남도록 이름·일수를 함께 저장한다.
-- =====================================================================

ALTER TABLE leave_requests
    ADD COLUMN special_rule_id   BIGINT REFERENCES special_leave_rules (id) ON DELETE SET NULL,
    ADD COLUMN special_rule_name VARCHAR(60),
    ADD COLUMN special_rule_days NUMERIC(4,1);
