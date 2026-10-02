-- =====================================================================
-- V12: 비밀번호 수명주기
--   1) 첫 로그인 비밀번호 변경 강제 플래그. 기존 계정은 전부 변경 대상(TRUE).
--      DEFAULT FALSE: 새 행은 서비스 코드가 값을 명시적으로 정한다
--      (기본값이 TRUE 면 초기 데이터 생성 같은 경로가 의도치 않게 막힐 수 있음).
--   2) 비밀번호 재설정 1회용 토큰. 원문은 저장하지 않고 SHA-256 hex 만 저장.
-- =====================================================================

ALTER TABLE employees ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE employees SET password_change_required = TRUE;

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT      NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,   -- SHA-256 hex, 원문 저장 금지
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_employee ON password_reset_tokens (employee_id);
