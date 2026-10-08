-- 자동 백업 설정(정책 › 백업). 한 줄(id = 1)만 둔다.
--   frequency      : DAILY(매일 run_time) / WEEKLY(매주 day_of_week 의 run_time) / HOURLY(0시부터 interval_hours 간격, 정각)
--   day_of_week    : 1 = 월요일 … 7 = 일요일
--   keep_daily     : 날짜별 마지막 자동 백업을 최근 N일치 보관
--   keep_weekly    : 주(월~일)별 마지막 자동 백업을 최근 N주치 보관
--   keep_monthly   : 월별 마지막 자동 백업을 최근 N개월치 보관
-- 수동·복원 전 백업은 자동 정리하지 않는다.
CREATE TABLE backup_settings (
    id             INTEGER     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    frequency      VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (frequency IN ('DAILY', 'WEEKLY', 'HOURLY')),
    day_of_week    INTEGER     NOT NULL DEFAULT 1 CHECK (day_of_week BETWEEN 1 AND 7),
    run_time       TIME        NOT NULL DEFAULT '02:00',
    interval_hours INTEGER     NOT NULL DEFAULT 6 CHECK (interval_hours BETWEEN 1 AND 24),
    keep_daily     INTEGER     NOT NULL DEFAULT 7 CHECK (keep_daily BETWEEN 0 AND 60),
    keep_weekly    INTEGER     NOT NULL DEFAULT 4 CHECK (keep_weekly BETWEEN 0 AND 52),
    keep_monthly   INTEGER     NOT NULL DEFAULT 6 CHECK (keep_monthly BETWEEN 0 AND 24),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO backup_settings (id) VALUES (1);
