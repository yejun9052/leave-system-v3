-- 자동화: 연차 촉진 자동 발송 설정과 자동 작업 실행 기록.

-- promotion_enabled 는 7/1·11/1 일괄 앱 알림 스위치였는데, 이제 직원별 사용 기한에 맞춘 자동 발송(메일 + 알림) ON/OFF 다.
-- 메일까지 가므로 꺼진 상태로 시작한다.
UPDATE leave_policy SET promotion_enabled = FALSE;

-- 자동 발송 시기: 사용 기한 몇 개월 전에 보낼지(1~6, 쉼표로 구분). 기본은 근로기준법 1차·2차 촉진 시기인 6개월·2개월 전.
ALTER TABLE leave_policy ADD COLUMN promotion_months VARCHAR(20) NOT NULL DEFAULT '6,2';

-- 자동 발송이면 그때의 발송 시기(사용 기한 N개월 전), 관리자 수동 발송이면 NULL.
ALTER TABLE promotion_notices ADD COLUMN auto_months INT;

-- 자동 작업(스케줄러)별 마지막 실행 결과. 자동화 탭에서 보여 준다.
CREATE TABLE scheduled_job_runs (
    job         VARCHAR(40)  PRIMARY KEY,
    started_at  TIMESTAMPTZ  NOT NULL,
    finished_at TIMESTAMPTZ,
    success     BOOLEAN,
    message     VARCHAR(500)
);
