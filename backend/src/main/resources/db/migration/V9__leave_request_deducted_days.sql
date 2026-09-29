-- =====================================================================
-- V9: 휴가 신청에 "실제 차감액" 컬럼 추가 (기간 days 와 분리)
--   기존 건은 과거 로직(차감액 = days)과 동일하게 backfill 하여
--   기존 승인건 취소 시 잔액 환원이 정확히 유지되도록 한다.
-- =====================================================================

ALTER TABLE leave_requests ADD COLUMN deducted_days NUMERIC(4,1) NOT NULL DEFAULT 0;
UPDATE leave_requests SET deducted_days = days;
