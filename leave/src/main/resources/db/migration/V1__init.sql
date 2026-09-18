-- 스케줄러(@Scheduled) 실행 로그 테이블
-- 도메인 테이블(사원/휴가/결재 등)은 스키마 확정 후 V2 이상에서 추가한다.
CREATE TABLE scheduled_job_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    job_name       VARCHAR(100) NOT NULL COMMENT '작업 이름',
    started_at     DATETIME(6)  NOT NULL COMMENT '시작 시각',
    finished_at    DATETIME(6)  NULL COMMENT '종료 시각',
    status         VARCHAR(20)  NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
    affected_count INT          NULL COMMENT '처리 건수',
    message        TEXT         NULL COMMENT '결과 메시지 / 에러 내용',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
    PRIMARY KEY (id),
    KEY idx_scheduled_job_log__job_name__started_at (job_name, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '스케줄러 실행 로그';
