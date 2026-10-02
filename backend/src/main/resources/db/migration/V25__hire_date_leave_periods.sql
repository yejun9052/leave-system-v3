-- 입사일 기준 연차 기간 전환.
-- 지금까지는 정책이 "입사일 기준"이어도 연차를 1/1~12/31 단위로 쌓고 1월 1일에 전년 잔여를 소멸시켰다.
-- 이제 leave_balances.year · leave_requests.applied_year 는 "그 해에 시작한 연차 기간"을 뜻한다
-- (입사일 기준: 입사 기념일 ~ 다음 기념일 전날, 회계연도 기준: 회계연도). 계산은 LeavePeriodCalculator.

-- 휴가가 기산일을 걸치면 기산일부터의 날짜분은 다음 기간(applied_year + 1)에서 뺀다.
ALTER TABLE leave_requests ADD COLUMN next_period_deducted_days NUMERIC(6,3) NOT NULL DEFAULT 0;

-- 다음 기간(다음 기산일 이후) 날짜의 연차 신청 허용 스위치.
ALTER TABLE leave_policy ADD COLUMN next_period_reservation_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 배포 뒤 한 번만 돌릴 작업. 서버가 시작될 때 done_at 이 비어 있는 작업을 실행하고 시각을 남긴다.
CREATE TABLE one_time_tasks (
    name       VARCHAR(60) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    done_at    TIMESTAMPTZ
);

-- 기존 휴가의 기간 배정과 기간별 잔액을 새 기준으로 다시 계산한다(LeavePeriodRebuildRunner).
INSERT INTO one_time_tasks (name) VALUES ('leave-period-rebuild');
