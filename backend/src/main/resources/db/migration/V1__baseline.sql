-- 연차 관리 기준 스키마(2026-10-06)
-- 개발 중에 쌓인 V1~V27 을 그 결과 스키마 그대로 하나로 합쳤다(운영 DB 가 생기기 전). 예전 단계별 변경은 git 이력에 있다.
-- 이제부터 스키마 변경은 이 파일을 고치지 않고 V2 부터 새 버전으로 추가한다.
-- 기본 데이터(관리자 계정·정책·휴가 종류·공휴일)는 앱이 처음 뜰 때 CoreDataInitializer 가 넣는다.

-- ───────────── 조직·계정 ─────────────

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT,
    lead_id     BIGINT,                         -- 부서장(employees.id). 아래에서 FK 연결
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments (id) ON DELETE RESTRICT
);
CREATE INDEX idx_departments_parent ON departments (parent_id);

CREATE TABLE employees (
    id                        BIGSERIAL PRIMARY KEY,
    email                     VARCHAR(255) NOT NULL,   -- 로그인 ID(관리 전용 계정은 'admin' 같은 아이디)
    password_hash             VARCHAR(255) NOT NULL,
    name                      VARCHAR(100) NOT NULL,
    department_id             BIGINT,
    "position"                VARCHAR(50),
    phone                     VARCHAR(30),
    hire_date                 DATE         NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / ON_LEAVE / RESIGNED
    resigned_date             DATE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    system_account            BOOLEAN      NOT NULL DEFAULT FALSE,     -- 직원이 아닌 관리 전용 계정
    password_change_required  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_employees_email UNIQUE (email),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL
);
CREATE INDEX idx_employees_department ON employees (department_id);
CREATE INDEX idx_employees_status ON employees (status);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_lead FOREIGN KEY (lead_id) REFERENCES employees (id) ON DELETE SET NULL;

CREATE TABLE employee_roles (
    employee_id  BIGINT      NOT NULL,
    role         VARCHAR(30) NOT NULL,  -- SYSTEM_ADMIN / HR_ADMIN / TEAM_LEAD / EMPLOYEE
    PRIMARY KEY (employee_id, role),
    CONSTRAINT fk_employee_roles_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT      NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    token_hash   CHAR(64)    NOT NULL UNIQUE,  -- 토큰 원문이 아니라 SHA-256 해시
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_employee ON password_reset_tokens (employee_id);

-- 로그인 세션(Spring Session JDBC 기본 스키마). application.yml 의 initialize-schema: never
CREATE TABLE spring_session (
    primary_id             CHAR(36) NOT NULL,
    session_id             CHAR(36) NOT NULL,
    creation_time          BIGINT   NOT NULL,
    last_access_time       BIGINT   NOT NULL,
    max_inactive_interval  INTEGER  NOT NULL,
    expiry_time            BIGINT   NOT NULL,
    principal_name         VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id  CHAR(36)     NOT NULL,
    attribute_name      VARCHAR(200) NOT NULL,
    attribute_bytes     BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- ───────────── 정책·휴가 종류 ─────────────

-- 연차 정책. 사용 중인 정책은 하나(active = TRUE)뿐이다
CREATE TABLE leave_policy (
    id                               BIGSERIAL PRIMARY KEY,
    grant_basis                      VARCHAR(20)  NOT NULL DEFAULT 'HIRE_DATE',  -- HIRE_DATE / FISCAL_YEAR
    fiscal_start_month               INTEGER      NOT NULL DEFAULT 1,
    fiscal_start_day                 INTEGER      NOT NULL DEFAULT 1,
    allow_negative                   BOOLEAN      NOT NULL DEFAULT FALSE,
    half_day_enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    promotion_enabled                BOOLEAN      NOT NULL DEFAULT TRUE,  -- 촉진 자동 발송 ON/OFF(자동화 탭)
    carry_over_enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    max_carry_over_days              NUMERIC(4,1) NOT NULL DEFAULT 0,
    active                           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    base_annual_days                 NUMERIC(4,1) NOT NULL DEFAULT 15,
    seniority_step_years             INTEGER      NOT NULL DEFAULT 2,
    seniority_increment_days         NUMERIC(4,1) NOT NULL DEFAULT 1,
    max_annual_days                  NUMERIC(4,1) NOT NULL DEFAULT 25,
    monthly_accrual_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    monthly_accrual_max              INTEGER      NOT NULL DEFAULT 11,
    max_concurrent_absence           INTEGER      NOT NULL DEFAULT 0,
    min_advance_days                 INTEGER      NOT NULL DEFAULT 0,
    max_consecutive_days             INTEGER      NOT NULL DEFAULT 0,
    hourly_enabled                   BOOLEAN      NOT NULL DEFAULT FALSE,
    lead_approval_required           BOOLEAN      NOT NULL DEFAULT TRUE,  -- 예전 2단계 결재 스위치. 지금은 읽지 않음
    next_period_reservation_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,  -- 다음 연차 기간 날짜 신청 허용
    promotion_months                 VARCHAR(20)  NOT NULL DEFAULT '6,2'  -- 촉진 자동 발송 시기(사용 기한 N개월 전)
);
CREATE UNIQUE INDEX uq_leave_policy_active ON leave_policy (active) WHERE active = TRUE;

CREATE TABLE leave_types (
    id                         BIGSERIAL PRIMARY KEY,
    code                       VARCHAR(40)  NOT NULL,
    name                       VARCHAR(60)  NOT NULL,
    deduct_days                NUMERIC(5,3) NOT NULL DEFAULT 1.0,
    paid                       BOOLEAN      NOT NULL DEFAULT TRUE,
    deduct_from_annual         BOOLEAN      NOT NULL DEFAULT TRUE,
    color_hex                  VARCHAR(7)   NOT NULL DEFAULT '#4f46e5',
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    portion                    VARCHAR(10)  NOT NULL DEFAULT 'FULL',  -- FULL / HALF / QUARTER(옛 반반차) / HOURLY
    requires_annual_exhausted  BOOLEAN      NOT NULL DEFAULT FALSE,   -- 연차를 다 쓴 뒤에만 신청 가능(예: 병가)
    CONSTRAINT uq_leave_types_code UNIQUE (code)
);

-- 경조사 규정(이름·일수). 신청할 때 하나를 고른다
CREATE TABLE special_leave_rules (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(60)  NOT NULL,
    days             NUMERIC(4,1) NOT NULL,
    leave_type_code  VARCHAR(40),
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 근속 포상 휴가
CREATE TABLE service_award_rules (
    id          BIGSERIAL PRIMARY KEY,
    years       INTEGER      NOT NULL,
    bonus_days  NUMERIC(4,1) NOT NULL,
    name        VARCHAR(60),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_award_years UNIQUE (years)
);

-- 휴가 신청 금지 기간
CREATE TABLE blackout_periods (
    id          BIGSERIAL PRIMARY KEY,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_blackout_dates ON blackout_periods (start_date, end_date);

-- ───────────── 연차 잔액·휴가 신청 ─────────────

-- year: 그 해에 시작하는 연차 기간(입사일 기준이면 입사 기념일부터 1년)
CREATE TABLE leave_balances (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT       NOT NULL,
    year          INTEGER      NOT NULL,
    granted       NUMERIC(6,3) NOT NULL DEFAULT 0,
    used          NUMERIC(6,3) NOT NULL DEFAULT 0,
    carried_over  NUMERIC(6,3) NOT NULL DEFAULT 0,
    expired       NUMERIC(6,3) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,  -- 낙관적 락
    CONSTRAINT uq_leave_balances_emp_year UNIQUE (employee_id, year),
    CONSTRAINT fk_leave_balances_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);
CREATE INDEX idx_leave_balances_employee ON leave_balances (employee_id);

CREATE TABLE leave_requests (
    id                         BIGSERIAL PRIMARY KEY,
    employee_id                BIGINT       NOT NULL,
    leave_type_id              BIGINT       NOT NULL,
    start_date                 DATE         NOT NULL,
    end_date                   DATE         NOT NULL,
    days                       NUMERIC(6,3) NOT NULL,
    applied_year               INTEGER      NOT NULL,  -- 차감하는 연차 기간(leave_balances.year)
    status                     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason                     VARCHAR(500),
    approver_id                BIGINT,
    approved_at                TIMESTAMPTZ,
    reject_reason              VARCHAR(500),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancel_reason              VARCHAR(500),
    deducted_days              NUMERIC(6,3) NOT NULL DEFAULT 0,  -- 연차에서 빼는 일수
    forfeited_days             NUMERIC(6,3) NOT NULL DEFAULT 0,  -- 승인으로 소멸되는 연차(병가·공가)
    special_rule_id            BIGINT       REFERENCES special_leave_rules (id) ON DELETE SET NULL,
    special_rule_name          VARCHAR(60),
    special_rule_days          NUMERIC(4,1),
    lead_approver_id           BIGINT       REFERENCES employees (id) ON DELETE SET NULL,  -- 예전 2단계 결재 기록
    lead_approved_at           TIMESTAMPTZ,
    hr_direct_reason           VARCHAR(500),
    version                    BIGINT       NOT NULL DEFAULT 0,  -- 낙관적 락
    next_period_deducted_days  NUMERIC(6,3) NOT NULL DEFAULT 0,  -- deducted_days 중 다음 연차 기간에서 빼는 몫
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_approver FOREIGN KEY (approver_id) REFERENCES employees (id) ON DELETE SET NULL
);
CREATE INDEX idx_lr_employee ON leave_requests (employee_id);
CREATE INDEX idx_lr_status ON leave_requests (status);
CREATE INDEX idx_lr_dates ON leave_requests (start_date, end_date);

-- ───────────── 일정·공휴일 ─────────────

CREATE TABLE holidays (
    id            BIGSERIAL PRIMARY KEY,
    holiday_date  DATE        NOT NULL,
    name          VARCHAR(60) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_holidays_date UNIQUE (holiday_date)
);
CREATE INDEX idx_holidays_date ON holidays (holiday_date);

CREATE TABLE calendar_events (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    all_day           BOOLEAN      NOT NULL DEFAULT TRUE,
    scope             VARCHAR(20)  NOT NULL DEFAULT 'COMPANY',      -- COMPANY / DEPARTMENT / PERSONAL
    source            VARCHAR(20)  NOT NULL DEFAULT 'ADMIN_EVENT',  -- LEAVE_REQUEST / ADMIN_EVENT / HOLIDAY
    color_hex         VARCHAR(7)   NOT NULL DEFAULT '#4f46e5',
    department_id     BIGINT,
    employee_id       BIGINT,
    leave_request_id  BIGINT,
    created_by        BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_ce_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_ce_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ce_request FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_ce_creator FOREIGN KEY (created_by) REFERENCES employees (id) ON DELETE SET NULL
);
CREATE INDEX idx_ce_dates ON calendar_events (start_date, end_date);
CREATE INDEX idx_ce_scope ON calendar_events (scope);

-- ───────────── 알림·이력 ─────────────

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT       NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(500),
    link         VARCHAR(200),
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_notif_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);
CREATE INDEX idx_notif_employee_read ON notifications (employee_id, is_read);

-- 이벤트 로그(관리 화면의 변경 기록)
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT,
    actor_name   VARCHAR(100),
    action       VARCHAR(60)   NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(60),
    detail       VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    success      BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);

-- 연차 사용 촉진 안내 발송 이력
CREATE TABLE promotion_notices (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    balance_year    INTEGER      NOT NULL,
    period_end      DATE         NOT NULL,
    remaining_days  NUMERIC(6,3) NOT NULL,
    days_left       INTEGER      NOT NULL,
    email           VARCHAR(255),
    sent_by         BIGINT       REFERENCES employees (id) ON DELETE SET NULL,
    sent_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    auto_months     INTEGER  -- 자동 발송이면 그때의 시기(N개월 전), 수동 발송이면 NULL
);
CREATE INDEX idx_promotion_notices_employee ON promotion_notices (employee_id, balance_year, sent_at DESC);

-- 자동 작업(스케줄러)별 마지막 실행 결과. success: TRUE 성공 / FALSE 실패 / NULL 건너뜀
CREATE TABLE scheduled_job_runs (
    job          VARCHAR(40)  PRIMARY KEY,
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    success      BOOLEAN,
    message      VARCHAR(500)
);

-- 한 번만 실행하는 데이터 보정 작업. 행이 있고 done_at 이 비어 있을 때만 실행된다
CREATE TABLE one_time_tasks (
    name        VARCHAR(60) PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    done_at     TIMESTAMPTZ
);
