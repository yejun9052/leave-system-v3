-- 초기설계-V01 7장 데이터 모델의 도메인 테이블.
-- department.leader_id 와 employee.department_id 는 서로를 참조하므로
-- 두 테이블을 만든 뒤 마지막에 FK 를 추가한다.

CREATE TABLE department
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    name              VARCHAR(100) NOT NULL COMMENT '부서명',
    parent_id         BIGINT       NULL COMMENT '상위 부서. 최상위는 NULL',
    leader_id         BIGINT       NULL COMMENT '팀장 사원 ID. 미지정 시 상위 부서장이 1차 확인 대행',
    min_staff_on_duty INT          NOT NULL DEFAULT 0 COMMENT '최소 잔류 인원. 0이면 제한 없음',
    created_at        DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at        DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_department__parent_id (parent_id),
    CONSTRAINT fk_department__parent_id FOREIGN KEY (parent_id) REFERENCES department (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '부서';

CREATE TABLE employee
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    name          VARCHAR(50)  NOT NULL COMMENT '이름',
    email         VARCHAR(150) NOT NULL COMMENT '이메일. 사번 대신 식별자로 사용',
    hire_date     DATE         NOT NULL COMMENT '입사일. 연차 부여·소멸 기준',
    role          VARCHAR(20)  NOT NULL COMMENT 'MEMBER / LEADER / ADMIN',
    department_id BIGINT       NULL COMMENT '소속 부서',
    active        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '재직 여부. 퇴사자는 삭제 대신 0',
    created_at    DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at    DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee__email (email),
    KEY idx_employee__department_id__active (department_id, active),
    CONSTRAINT fk_employee__department_id FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '사원';

ALTER TABLE department
    ADD KEY idx_department__leader_id (leader_id),
    ADD CONSTRAINT fk_department__leader_id FOREIGN KEY (leader_id) REFERENCES employee (id);

CREATE TABLE leave_type
(
    code                VARCHAR(30) NOT NULL COMMENT '휴가 코드 (PK)',
    name                VARCHAR(50) NOT NULL COMMENT '표시 이름',
    deduct_days         DECIMAL(3, 1) NOT NULL COMMENT '근무일 하루당 차감 일수. 반차 0.5, 병가·공가 0',
    single_day_only     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '하루 신청만 허용 여부',
    only_when_empty     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '잔여 0일일 때만 노출',
    staff_limit_applied TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '최소 잔류 인원 제한 적용 여부',
    active              TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '신청 화면 노출 여부',
    sort_order          INT         NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    created_at          DATETIME(6) NOT NULL COMMENT '등록 시각',
    updated_at          DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '휴가 종류';

CREATE TABLE leave_request
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
    employee_id          BIGINT        NOT NULL COMMENT '신청자',
    leave_type_code      VARCHAR(30)   NOT NULL COMMENT '휴가 종류',
    start_date           DATE          NOT NULL COMMENT '시작일',
    end_date             DATE          NOT NULL COMMENT '종료일',
    days                 DECIMAL(4, 1) NOT NULL COMMENT '계산된 신청 일수 스냅샷',
    reason               VARCHAR(500)  NULL COMMENT '사유',
    status               VARCHAR(20)   NOT NULL COMMENT 'PENDING / LEADER_OK / APPROVED / REJECTED / CANCELED / CANCEL_REQUESTED / CANCELED_DONE',
    staff_limit_override TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '팀장의 잔류 인원 초과 통과 플래그',
    escalated_to         BIGINT        NULL COMMENT '1차 확인을 대행한 상위 부서장',
    created_at           DATETIME(6)   NOT NULL COMMENT '등록 시각',
    updated_at           DATETIME(6)   NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_leave_request__employee_id__start_date (employee_id, start_date),
    KEY idx_leave_request__status__start_date (status, start_date),
    KEY idx_leave_request__leave_type_code (leave_type_code),
    KEY idx_leave_request__escalated_to (escalated_to),
    CONSTRAINT fk_leave_request__employee_id FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_leave_request__leave_type_code FOREIGN KEY (leave_type_code) REFERENCES leave_type (code),
    CONSTRAINT fk_leave_request__escalated_to FOREIGN KEY (escalated_to) REFERENCES employee (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '휴가 신청';

CREATE TABLE leave_history
(
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
    employee_id BIGINT        NOT NULL COMMENT '대상 사원',
    type        VARCHAR(20)   NOT NULL COMMENT 'GRANT / USE / CANCEL / ADJUST / EXPIRE',
    days        DECIMAL(4, 1) NOT NULL COMMENT '증감 일수. 사용·소멸은 음수',
    occurred_on DATE          NOT NULL COMMENT '발생일',
    request_id  BIGINT        NULL COMMENT '원장을 발생시킨 신청. 부여·소멸은 NULL',
    source      VARCHAR(20)   NOT NULL COMMENT 'SYSTEM / ADMIN / EXCEL_IMPORT',
    memo        VARCHAR(500)  NULL COMMENT '비고',
    created_at  DATETIME(6)   NOT NULL COMMENT '등록 시각',
    updated_at  DATETIME(6)   NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_history__request_id__type (request_id, type),
    KEY idx_leave_history__employee_id__occurred_on (employee_id, occurred_on),
    CONSTRAINT fk_leave_history__employee_id FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_leave_history__request_id FOREIGN KEY (request_id) REFERENCES leave_request (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '연차 원장. 잔여 연차는 days 합계';

CREATE TABLE holiday
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    start_date DATE         NOT NULL COMMENT '시작일',
    end_date   DATE         NOT NULL COMMENT '종료일. 하루도 기간으로 표현',
    name       VARCHAR(100) NOT NULL COMMENT '명칭',
    type       VARCHAR(20)  NOT NULL COMMENT 'PUBLIC / COMPANY / BLOCKED',
    created_at DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_holiday__start_date__end_date (start_date, end_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '공휴일·사내휴일·신청 금지 기간';

CREATE TABLE notification
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    receiver_id  BIGINT       NOT NULL COMMENT '수신자',
    type         VARCHAR(30)  NOT NULL COMMENT '알림 사유',
    request_id   BIGINT       NULL COMMENT '관련 휴가 신청. 공지 알림 등은 NULL',
    message      VARCHAR(500) NOT NULL COMMENT '표시 문구',
    is_read      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '읽음 여부. READ 는 MySQL 예약어',
    mail_sent    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '메일 발송 성공 여부',
    mail_sent_at DATETIME(6)  NULL COMMENT '메일 발송 시각',
    created_at   DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at   DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_notification__receiver_id__is_read__id (receiver_id, is_read, id),
    KEY idx_notification__request_id (request_id),
    CONSTRAINT fk_notification__receiver_id FOREIGN KEY (receiver_id) REFERENCES employee (id),
    CONSTRAINT fk_notification__request_id FOREIGN KEY (request_id) REFERENCES leave_request (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '앱 내 알림';

CREATE TABLE action_log
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    actor_id    BIGINT      NULL COMMENT '행위자. 시스템 작업은 NULL',
    action      VARCHAR(30) NOT NULL COMMENT '행위 코드 (9.3의 30종)',
    target_type VARCHAR(30) NOT NULL COMMENT '대상 종류',
    target_id   BIGINT      NULL COMMENT '대상 식별자',
    detail      JSON        NULL COMMENT '변경 전후 값 등 상세',
    created_at  DATETIME(6) NOT NULL COMMENT '기록 시각',
    PRIMARY KEY (id),
    KEY idx_action_log__target_type__target_id__created_at (target_type, target_id, created_at),
    KEY idx_action_log__actor_id__created_at (actor_id, created_at),
    KEY idx_action_log__created_at (created_at),
    CONSTRAINT fk_action_log__actor_id FOREIGN KEY (actor_id) REFERENCES employee (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '이벤트로그. append-only';

CREATE TABLE policy
(
    id              BIGINT       NOT NULL COMMENT 'PK. 단일 행이며 항상 1',
    grant_basis     VARCHAR(20)  NOT NULL DEFAULT 'HIRE_DATE' COMMENT 'HIRE_DATE / FISCAL_YEAR',
    promote_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '연차 사용 촉진 메일 자동 발송 여부',
    promote_months  INT          NOT NULL DEFAULT 2 COMMENT '소멸 몇 개월 전에 촉진 메일을 보낼지',
    backup_enabled  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '정기 DB 백업 사용 여부',
    backup_cron     VARCHAR(100) NOT NULL DEFAULT '0 0 3 * * *' COMMENT '백업 실행 주기',
    created_at      DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at      DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '운영 정책. 단일 행';

CREATE TABLE notice
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    title      VARCHAR(200) NOT NULL COMMENT '제목',
    content    TEXT         NOT NULL COMMENT '본문',
    author_id  BIGINT       NULL COMMENT '작성자. 시스템 공지는 NULL',
    type       VARCHAR(20)  NOT NULL COMMENT 'MANUAL / SYSTEM',
    published  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '게시 여부. 시스템 공지는 초안으로 생성',
    pinned     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '상단 고정 여부',
    created_at DATETIME(6)  NOT NULL COMMENT '등록 시각',
    updated_at DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_notice__published__pinned__id (published, pinned, id),
    KEY idx_notice__author_id (author_id),
    CONSTRAINT fk_notice__author_id FOREIGN KEY (author_id) REFERENCES employee (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '공지사항';
