-- =====================================================================
-- V3: 휴가 신청 / 캘린더 일정 / 알림 / 감사 로그
-- =====================================================================

CREATE TABLE leave_requests (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT       NOT NULL,
    leave_type_id  BIGINT       NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    days           NUMERIC(5,1) NOT NULL,
    applied_year   INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason         VARCHAR(500),
    approver_id    BIGINT,
    approved_at    TIMESTAMPTZ,
    reject_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_approver FOREIGN KEY (approver_id) REFERENCES employees (id) ON DELETE SET NULL
);

CREATE INDEX idx_lr_employee ON leave_requests (employee_id);
CREATE INDEX idx_lr_status ON leave_requests (status);
CREATE INDEX idx_lr_dates ON leave_requests (start_date, end_date);

CREATE TABLE calendar_events (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    all_day       BOOLEAN      NOT NULL DEFAULT TRUE,
    scope         VARCHAR(20)  NOT NULL DEFAULT 'COMPANY',   -- COMPANY | DEPARTMENT | PERSONAL
    source        VARCHAR(20)  NOT NULL DEFAULT 'ADMIN_EVENT', -- LEAVE_REQUEST | ADMIN_EVENT | HOLIDAY
    color_hex     VARCHAR(7)   NOT NULL DEFAULT '#4f46e5',
    department_id BIGINT,
    employee_id   BIGINT,
    leave_request_id BIGINT,
    created_by    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_ce_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_ce_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ce_request FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_ce_creator FOREIGN KEY (created_by) REFERENCES employees (id) ON DELETE SET NULL
);

CREATE INDEX idx_ce_dates ON calendar_events (start_date, end_date);
CREATE INDEX idx_ce_scope ON calendar_events (scope);

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

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT,
    actor_name  VARCHAR(100),
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60),
    entity_id   VARCHAR(60),
    detail      VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON audit_logs (created_at);
