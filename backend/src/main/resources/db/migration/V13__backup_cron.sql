-- 자동 백업 실행 시각을 cron 하나로(초 분 시 일 월 요일, 예 "0 10 0 * * *" = 매일 00:10). 서버 시간대 Asia/Seoul.
--   예전 설정(매일 / 매주 요일 / N시간마다)은 같은 뜻의 cron 으로 옮긴다.
ALTER TABLE backup_settings ADD COLUMN cron VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * *';

UPDATE backup_settings SET cron = CASE frequency
    WHEN 'DAILY'  THEN format('0 %s %s * * *', extract(minute FROM run_time)::int, extract(hour FROM run_time)::int)
    WHEN 'WEEKLY' THEN format('0 %s %s * * %s', extract(minute FROM run_time)::int, extract(hour FROM run_time)::int,
                              (ARRAY['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'])[day_of_week])
    WHEN 'HOURLY' THEN CASE WHEN interval_hours >= 24 THEN '0 0 0 * * *'
                            ELSE format('0 0 0/%s * * *', interval_hours) END
    ELSE '0 0 2 * * *'
END;

ALTER TABLE backup_settings DROP COLUMN frequency, DROP COLUMN day_of_week, DROP COLUMN run_time,
    DROP COLUMN interval_hours;
