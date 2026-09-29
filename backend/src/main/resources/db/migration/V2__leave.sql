-- =====================================================================
-- V2: 연차 정책 / 휴가 종류 / 연차 잔액 / 공휴일
-- =====================================================================

CREATE TABLE leave_policy (
    id                   BIGSERIAL PRIMARY KEY,
    grant_basis          VARCHAR(20)  NOT NULL DEFAULT 'HIRE_DATE',   -- HIRE_DATE | FISCAL_YEAR
    fiscal_start_month   INT          NOT NULL DEFAULT 1,
    fiscal_start_day     INT          NOT NULL DEFAULT 1,
    allow_negative       BOOLEAN      NOT NULL DEFAULT FALSE,
    half_day_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    promotion_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    carry_over_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    max_carry_over_days  NUMERIC(4,1) NOT NULL DEFAULT 0,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE leave_types (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    deduct_days  NUMERIC(4,1) NOT NULL DEFAULT 1.0,   -- 1.0=연차, 0.5=반차, 0=무급/비차감
    paid         BOOLEAN      NOT NULL DEFAULT TRUE,
    half_day     BOOLEAN      NOT NULL DEFAULT FALSE,
    deduct_from_annual BOOLEAN NOT NULL DEFAULT TRUE, -- 연차 잔액에서 차감 여부
    color_hex    VARCHAR(7)   NOT NULL DEFAULT '#4f46e5',
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_leave_types_code UNIQUE (code)
);

CREATE TABLE leave_balances (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT       NOT NULL,
    year         INT          NOT NULL,
    granted      NUMERIC(5,1) NOT NULL DEFAULT 0,
    used         NUMERIC(5,1) NOT NULL DEFAULT 0,
    carried_over NUMERIC(5,1) NOT NULL DEFAULT 0,
    expired      NUMERIC(5,1) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_leave_balances_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT uq_leave_balances_emp_year UNIQUE (employee_id, year)
);

CREATE INDEX idx_leave_balances_employee ON leave_balances (employee_id);

CREATE TABLE holidays (
    id          BIGSERIAL PRIMARY KEY,
    holiday_date DATE        NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_holidays_date UNIQUE (holiday_date)
);

CREATE INDEX idx_holidays_date ON holidays (holiday_date);
