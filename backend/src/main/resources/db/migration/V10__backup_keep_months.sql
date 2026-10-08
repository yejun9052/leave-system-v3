-- 자동 백업 보관 규칙 단순화: 일간·주간·월간 개수(V9) → "최근 N개월 동안의 자동 백업은 모두 보관" 하나.
--   keep_months: 1~24, 기본 6. 이미 저장된 값은 월간 개수를 옮겨 온다(0 이면 1).
ALTER TABLE backup_settings ADD COLUMN keep_months INTEGER NOT NULL DEFAULT 6 CHECK (keep_months BETWEEN 1 AND 24);
UPDATE backup_settings SET keep_months = GREATEST(1, LEAST(24, keep_monthly));
ALTER TABLE backup_settings DROP COLUMN keep_daily, DROP COLUMN keep_weekly, DROP COLUMN keep_monthly;
