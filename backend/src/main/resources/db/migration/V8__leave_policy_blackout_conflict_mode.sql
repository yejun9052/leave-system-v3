-- 연차 사용 금지 기간을 등록하거나 기간을 늘려 수정할 때, 겹치는 기존 휴가 처리 방식.
--   KEEP_APPROVED(기본): 승인·취소 요청 중인 휴가는 유지, 결재 대기 휴가는 자동 반려
--   CANCEL_ALL         : 승인·취소 요청 중인 휴가도 자동 취소(연차 환원), 결재 대기 휴가는 자동 반려
-- 금지 기간에도 신청할 수 있는 종류(allowed_during_blackout)는 처리하지 않는다.
ALTER TABLE leave_policy ADD COLUMN blackout_conflict_mode VARCHAR(20) NOT NULL DEFAULT 'KEEP_APPROVED';
ALTER TABLE leave_policy ADD CONSTRAINT chk_leave_policy_blackout_conflict_mode
    CHECK (blackout_conflict_mode IN ('KEEP_APPROVED', 'CANCEL_ALL'));
