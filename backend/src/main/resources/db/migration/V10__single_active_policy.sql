-- =====================================================================
-- V10: 활성 연차 정책은 항상 1개만 존재하도록 보장 (동시 기본정책 중복생성 방지)
--   먼저 혹시 모를 중복 활성 정책을 정리한 뒤 부분 유니크 인덱스를 건다.
-- =====================================================================

-- 안전장치: 활성 정책이 여러 개면 가장 낮은 id 만 남기고 비활성화
UPDATE leave_policy SET active = false
 WHERE active = true
   AND id <> (SELECT MIN(id) FROM leave_policy WHERE active = true);

CREATE UNIQUE INDEX uq_leave_policy_active ON leave_policy (active) WHERE active = true;
