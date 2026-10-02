-- 연차 사용 촉진 안내 발송 이력. 직원에게 사용 기한 전에 남은 연차를 알린 기록(통보 증빙)이자,
-- 목록의 "최근 발송"과 나중에 자동 발송할 때 같은 기간에 다시 보내지 않게 하는 근거.
CREATE TABLE promotion_notices (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    balance_year   INT          NOT NULL,          -- 연차 기간(그 해에 시작한 기간)
    period_end     DATE         NOT NULL,          -- 사용 기한
    remaining_days NUMERIC(6,3) NOT NULL,          -- 보낼 때 남은 연차
    days_left      INT          NOT NULL,          -- 보낼 때 사용 기한까지 남은 날(D-day)
    email          VARCHAR(255),                   -- 메일을 보낸 주소. 없으면 앱 알림만
    sent_by        BIGINT       REFERENCES employees (id) ON DELETE SET NULL, -- 보낸 관리자(자동 발송이면 NULL)
    sent_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_promotion_notices_employee ON promotion_notices (employee_id, balance_year, sent_at DESC);
