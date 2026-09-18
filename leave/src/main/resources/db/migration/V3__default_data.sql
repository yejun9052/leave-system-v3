-- 초기설계-V01 5.3 휴가 종류 기본값과 7장 단일 행 정책.

INSERT INTO leave_type (code, name, deduct_days, single_day_only, only_when_empty, staff_limit_applied,
                        active, sort_order, created_at, updated_at)
VALUES ('ANNUAL', '연차', 1.0, 0, 0, 1, 1, 1, NOW(6), NOW(6)),
       ('HALF_AM', '오전 반차', 0.5, 1, 0, 0, 1, 2, NOW(6), NOW(6)),
       ('HALF_PM', '오후 반차', 0.5, 1, 0, 0, 1, 3, NOW(6), NOW(6)),
       ('SICK', '병가', 0.0, 0, 1, 0, 1, 4, NOW(6), NOW(6)),
       ('OFFICIAL', '공가', 0.0, 0, 1, 0, 1, 5, NOW(6), NOW(6)),
       ('FAMILY_EVENT', '경조사', 0.0, 0, 0, 1, 1, 6, NOW(6), NOW(6));

INSERT INTO policy (id, grant_basis, promote_enabled, promote_months, backup_enabled, backup_cron,
                    created_at, updated_at)
VALUES (1, 'HIRE_DATE', 0, 2, 0, '0 0 3 * * *', NOW(6), NOW(6));
