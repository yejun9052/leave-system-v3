-- 직원 수와 연차 대상에 포함되지 않는 개인별 시스템 관리자 로그인 계정.
CREATE TABLE system_admin_account
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    login_id      VARCHAR(80)  NOT NULL COMMENT '개인별 로그인 ID',
    owner_name    VARCHAR(100) NOT NULL COMMENT '실제 계정 사용자',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 비밀번호 해시',
    active        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '로그인 허용 여부',
    created_at    DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_admin_account__login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '개인별 시스템 관리자 계정';
