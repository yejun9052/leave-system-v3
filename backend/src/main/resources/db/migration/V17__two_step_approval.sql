-- =====================================================================
-- V17: 2단계 결재(팀장 1차 → 인사관리자 최종)
--   leave_requests.status 에 'LEAD_APPROVED'(1차 승인, 인사 결재 대기)가 추가된다(문자 컬럼이라 스키마 변경 없음).
--   - lead_approver_id / lead_approved_at : 1차 승인한 팀장과 시각
--   - hr_direct_reason : 팀장 부재로 인사관리자에게 바로 신청한 사유(없으면 일반 경로)
--   - leave_policy.lead_approval_required : ON 이면 팀장 1차 승인을 거치고, OFF 면 모든 신청이 인사관리자에게 바로 간다
--   배포 시점에 대기 중인 신청은 정책에 따라 팀장 단계(ON) 또는 인사 단계(OFF)로 처리된다.
-- =====================================================================

ALTER TABLE leave_requests
    ADD COLUMN lead_approver_id BIGINT REFERENCES employees (id) ON DELETE SET NULL,
    ADD COLUMN lead_approved_at TIMESTAMPTZ,
    ADD COLUMN hr_direct_reason VARCHAR(500);

ALTER TABLE leave_policy ADD COLUMN lead_approval_required BOOLEAN NOT NULL DEFAULT TRUE;
