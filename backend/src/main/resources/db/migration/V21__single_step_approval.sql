-- 단일 결재로 변경(팀장·인사관리자·시스템 관리자 중 한 명이 승인하면 확정)
-- 2단계 결재의 "팀장 1차 승인 완료" 건은 다시 결재 대기로 되돌린다(누구든 결재 권한자가 한 번 승인하면 확정).
-- lead_approver_id·lead_approved_at·hr_direct_reason·leave_policy.lead_approval_required 열은 기록으로 남기고 쓰지 않는다.
UPDATE leave_requests SET status = 'PENDING' WHERE status = 'LEAD_APPROVED';
