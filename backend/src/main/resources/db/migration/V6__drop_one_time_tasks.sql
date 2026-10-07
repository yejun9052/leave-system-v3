-- 한 번만 실행하는 데이터 보정 작업 기록 테이블을 없앤다.
-- 유일한 사용처였던 연차 기간 재계산(LeavePeriodRebuildRunner)은 2026-10-02 로컬에서 끝났고, 운영 DB가 없어 다시 쓸 일이 없다.
DROP TABLE one_time_tasks;
