-- =====================================================================
-- V5: 정책 고도화 (근속 가산 정책화 / 사용 통제 / 포상·경조사·블랙아웃)
--     모두 추가(additive) - 기존 데이터 보존
-- =====================================================================

-- 근속 가산 & 월차 (기존 하드코딩 값을 정책으로 이전)
ALTER TABLE leave_policy ADD COLUMN base_annual_days        NUMERIC(4,1) NOT NULL DEFAULT 15;
ALTER TABLE leave_policy ADD COLUMN seniority_step_years    INT          NOT NULL DEFAULT 2;
ALTER TABLE leave_policy ADD COLUMN seniority_increment_days NUMERIC(4,1) NOT NULL DEFAULT 1;
ALTER TABLE leave_policy ADD COLUMN max_annual_days         NUMERIC(4,1) NOT NULL DEFAULT 25;
ALTER TABLE leave_policy ADD COLUMN monthly_accrual_enabled BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE leave_policy ADD COLUMN monthly_accrual_max     INT          NOT NULL DEFAULT 11;

-- 사용 통제
ALTER TABLE leave_policy ADD COLUMN max_concurrent_absence  INT NOT NULL DEFAULT 0;  -- 0 = 무제한
ALTER TABLE leave_policy ADD COLUMN min_advance_days        INT NOT NULL DEFAULT 0;  -- 0 = 제한 없음
ALTER TABLE leave_policy ADD COLUMN max_consecutive_days    INT NOT NULL DEFAULT 0;  -- 0 = 무제한

-- 장기근속 포상휴가 규칙 (근속 N년 도달 시 보너스 연차)
CREATE TABLE service_award_rules (
    id          BIGSERIAL PRIMARY KEY,
    years       INT          NOT NULL,
    bonus_days  NUMERIC(4,1) NOT NULL,
    name        VARCHAR(60),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_award_years UNIQUE (years)
);

-- 경조사 휴가 규칙 (관계/사유별 일수)
CREATE TABLE special_leave_rules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(60)  NOT NULL,
    days            NUMERIC(4,1) NOT NULL,
    leave_type_code VARCHAR(40),
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 연차 사용 금지(블랙아웃) 기간
CREATE TABLE blackout_periods (
    id          BIGSERIAL PRIMARY KEY,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_blackout_dates ON blackout_periods (start_date, end_date);
