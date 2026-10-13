-- 팀 사용 통제: "동시 부재 최대 인원"을 "팀 최소 보류 인원"(휴가 날마다 같은 부서에 남아 있어야 할 재직 인원)으로 바꾼다.
-- 뜻이 반대라 기존 값은 옮기지 않고 0(제한 없음)으로 시작한다. 인사관리자가 정책 화면에서 다시 정한다.
ALTER TABLE leave_policy RENAME COLUMN max_concurrent_absence TO min_team_staff;
UPDATE leave_policy SET min_team_staff = 0;
