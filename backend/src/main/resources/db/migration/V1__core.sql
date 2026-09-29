-- =====================================================================
-- V1: 핵심 스키마 (부서, 사용자, 권한)
-- =====================================================================

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT,
    lead_id     BIGINT,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE employees (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(100) NOT NULL,
    employee_no    VARCHAR(50),
    department_id  BIGINT,
    position       VARCHAR(50),
    phone          VARCHAR(30),
    hire_date      DATE NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    resigned_date  DATE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_employees_email UNIQUE (email)
);

CREATE UNIQUE INDEX uq_employees_employee_no
    ON employees (employee_no) WHERE employee_no IS NOT NULL;

CREATE TABLE employee_roles (
    employee_id BIGINT NOT NULL,
    role        VARCHAR(30) NOT NULL,
    PRIMARY KEY (employee_id, role)
);

-- Foreign keys (부서 self-reference 및 상호 참조는 테이블 생성 후 부여)
ALTER TABLE departments
    ADD CONSTRAINT fk_departments_parent
        FOREIGN KEY (parent_id) REFERENCES departments (id) ON DELETE RESTRICT;
ALTER TABLE departments
    ADD CONSTRAINT fk_departments_lead
        FOREIGN KEY (lead_id) REFERENCES employees (id) ON DELETE SET NULL;
ALTER TABLE employees
    ADD CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL;
ALTER TABLE employee_roles
    ADD CONSTRAINT fk_employee_roles_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE;

CREATE INDEX idx_departments_parent ON departments (parent_id);
CREATE INDEX idx_employees_department ON employees (department_id);
CREATE INDEX idx_employees_status ON employees (status);
