-- Demo data for local review of the annual-leave UI.
-- All demo accounts use the local admin password: admin1234!
-- Safe to run repeatedly: employees/departments are keyed by their demo email/name,
-- requests/events/notifications/logs are guarded by their natural demo identifiers.
-- For Windows, copy this UTF-8 file into the Postgres container and run it there
-- (docker cp + psql -f) so Hangul is not converted by the host console code page.

BEGIN;

-- ---------------------------------------------------------------------
-- Organization
-- Reuse the existing test rows so old development-team fixtures disappear
-- without leaving foreign-key or sequence gaps.
-- ---------------------------------------------------------------------
UPDATE departments SET name = '제품개발팀', parent_id = 1, lead_id = NULL, sort_order = 10 WHERE id = 2;
UPDATE departments SET name = '플랫폼파트', parent_id = 2, lead_id = NULL, sort_order = 11 WHERE id = 3;
UPDATE departments SET name = '서비스파트', parent_id = 2, lead_id = NULL, sort_order = 12 WHERE id = 4;
UPDATE departments SET name = '경영지원팀', parent_id = 1, lead_id = NULL, sort_order = 20 WHERE id = 5;
UPDATE departments SET name = '디자인팀', parent_id = 1, lead_id = NULL, sort_order = 30 WHERE id = 6;
UPDATE departments SET name = '영업마케팅팀', parent_id = 1, lead_id = NULL, sort_order = 40 WHERE id = 7;

INSERT INTO departments (name, parent_id, sort_order)
SELECT '고객성공팀', 1, 50
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '고객성공팀');

INSERT INTO departments (name, parent_id, sort_order)
SELECT '데이터전략팀', 1, 60
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '데이터전략팀');

-- ---------------------------------------------------------------------
-- Employees
-- The hash is the BCrypt hash already used by the local admin account.
-- ---------------------------------------------------------------------
INSERT INTO employees (
    email, password_hash, name, department_id, position, phone,
    hire_date, status, system_account
)
SELECT v.email, '$2a$10$aM6NzZE.r0pbj/iIFH4Fk.J593PVuIXwvA52GWAAzlA0azODGdGt.',
       v.name,
       (SELECT d.id FROM departments d WHERE d.name = v.department_name ORDER BY d.id LIMIT 1),
       v.position, v.phone, v.hire_date, 'ACTIVE', FALSE
FROM (VALUES
    ('minjun.kim@example.com', '김민준', '경영지원팀', '인사팀장', '010-2000-1001', DATE '2022-03-14'),
    ('seoyeon.park@example.com', '박서연', '플랫폼파트', '플랫폼 파트장', '010-2000-1002', DATE '2023-01-09'),
    ('jihoon.choi@example.com', '최지훈', '서비스파트', '서비스 파트장', '010-2000-1003', DATE '2024-02-19'),
    ('haeun.jeong@example.com', '정하은', '디자인팀', '디자인팀장', '010-2000-1004', DATE '2021-07-05'),
    ('yujin.han@example.com', '한유진', '영업마케팅팀', '영업마케팅팀장', '010-2000-1005', DATE '2022-11-21'),
    ('jimin.oh@example.com', '오지민', '고객성공팀', '고객성공팀장', '010-2000-1006', DATE '2023-04-03'),
    ('hyunwoo.kang@example.com', '강현우', '데이터전략팀', '데이터전략팀장', '010-2000-1007', DATE '2021-09-13'),
    ('seoah.yoon@example.com', '윤서아', '플랫폼파트', '백엔드 엔지니어', '010-2000-1008', DATE '2024-05-13'),
    ('jiho.song@example.com', '송지호', '서비스파트', '프론트엔드 엔지니어', '010-2000-1009', DATE '2024-08-26'),
    ('seojun.kim@example.com', '김서준', '영업마케팅팀', '영업 매니저', '010-2000-1010', DATE '2025-01-06'),
    ('jieun.park@example.com', '박지은', '고객성공팀', '고객성공 매니저', '010-2000-1011', DATE '2024-10-07'),
    ('sujin.bae@example.com', '배수진', '경영지원팀', '재무 담당', '010-2000-1012', DATE '2023-06-12'),
    ('yerin.moon@example.com', '문예린', '디자인팀', '브랜드 디자이너', '010-2000-1013', DATE '2022-12-05'),
    ('doyoon.lee@example.com', '이도윤', '데이터전략팀', '데이터 분석가', '010-2000-1014', DATE '2025-02-03'),
    ('daeun.choi@example.com', '최다은', '제품개발팀', 'QA 엔지니어', '010-2000-1015', DATE '2025-03-10')
) AS v(email, name, department_name, position, phone, hire_date)
WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE e.email = v.email);

-- Also normalize rows when this seed is re-run after a non-UTF-8 import.
WITH demo_employees (email, name, department_name, position, phone, hire_date) AS (
    VALUES
    ('minjun.kim@example.com', '김민준', '경영지원팀', '인사팀장', '010-2000-1001', DATE '2022-03-14'),
    ('seoyeon.park@example.com', '박서연', '플랫폼파트', '플랫폼 파트장', '010-2000-1002', DATE '2023-01-09'),
    ('jihoon.choi@example.com', '최지훈', '서비스파트', '서비스 파트장', '010-2000-1003', DATE '2024-02-19'),
    ('haeun.jeong@example.com', '정하은', '디자인팀', '디자인팀장', '010-2000-1004', DATE '2021-07-05'),
    ('yujin.han@example.com', '한유진', '영업마케팅팀', '영업마케팅팀장', '010-2000-1005', DATE '2022-11-21'),
    ('jimin.oh@example.com', '오지민', '고객성공팀', '고객성공팀장', '010-2000-1006', DATE '2023-04-03'),
    ('hyunwoo.kang@example.com', '강현우', '데이터전략팀', '데이터전략팀장', '010-2000-1007', DATE '2021-09-13'),
    ('seoah.yoon@example.com', '윤서아', '플랫폼파트', '백엔드 엔지니어', '010-2000-1008', DATE '2024-05-13'),
    ('jiho.song@example.com', '송지호', '서비스파트', '프론트엔드 엔지니어', '010-2000-1009', DATE '2024-08-26'),
    ('seojun.kim@example.com', '김서준', '영업마케팅팀', '영업 매니저', '010-2000-1010', DATE '2025-01-06'),
    ('jieun.park@example.com', '박지은', '고객성공팀', '고객성공 매니저', '010-2000-1011', DATE '2024-10-07'),
    ('sujin.bae@example.com', '배수진', '경영지원팀', '재무 담당', '010-2000-1012', DATE '2023-06-12'),
    ('yerin.moon@example.com', '문예린', '디자인팀', '브랜드 디자이너', '010-2000-1013', DATE '2022-12-05'),
    ('doyoon.lee@example.com', '이도윤', '데이터전략팀', '데이터 분석가', '010-2000-1014', DATE '2025-02-03'),
    ('daeun.choi@example.com', '최다은', '제품개발팀', 'QA 엔지니어', '010-2000-1015', DATE '2025-03-10')
)
UPDATE employees e
SET name = d.name,
    department_id = (SELECT dept.id FROM departments dept WHERE dept.name = d.department_name ORDER BY dept.id LIMIT 1),
    position = d.position,
    phone = d.phone,
    hire_date = d.hire_date,
    updated_at = now()
FROM demo_employees d
WHERE e.email = d.email;

-- Keep the existing review account in the new organization.
UPDATE employees
SET department_id = (SELECT id FROM departments WHERE name = '제품개발팀' ORDER BY id LIMIT 1)
WHERE email = 'leey217423@gmail.com';

-- Roles for the demo managers and team leads.
INSERT INTO employee_roles (employee_id, role)
SELECT e.id, v.role
FROM employees e
JOIN (VALUES
    ('minjun.kim@example.com', 'HR_ADMIN'),
    ('minjun.kim@example.com', 'TEAM_LEAD'),
    ('minjun.kim@example.com', 'EMPLOYEE'),
    ('seoyeon.park@example.com', 'TEAM_LEAD'),
    ('seoyeon.park@example.com', 'EMPLOYEE'),
    ('jihoon.choi@example.com', 'TEAM_LEAD'),
    ('jihoon.choi@example.com', 'EMPLOYEE'),
    ('haeun.jeong@example.com', 'TEAM_LEAD'),
    ('haeun.jeong@example.com', 'EMPLOYEE'),
    ('yujin.han@example.com', 'TEAM_LEAD'),
    ('yujin.han@example.com', 'EMPLOYEE'),
    ('jimin.oh@example.com', 'TEAM_LEAD'),
    ('jimin.oh@example.com', 'EMPLOYEE'),
    ('hyunwoo.kang@example.com', 'TEAM_LEAD'),
    ('hyunwoo.kang@example.com', 'EMPLOYEE')
) AS v(email, role) ON v.email = e.email
ON CONFLICT (employee_id, role) DO NOTHING;

UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'leey217423@gmail.com') WHERE name = '제품개발팀';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'seoyeon.park@example.com') WHERE name = '플랫폼파트';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'jihoon.choi@example.com') WHERE name = '서비스파트';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'minjun.kim@example.com') WHERE name = '경영지원팀';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'haeun.jeong@example.com') WHERE name = '디자인팀';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'yujin.han@example.com') WHERE name = '영업마케팅팀';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'jimin.oh@example.com') WHERE name = '고객성공팀';
UPDATE departments SET lead_id = (SELECT id FROM employees WHERE email = 'hyunwoo.kang@example.com') WHERE name = '데이터전략팀';

-- ---------------------------------------------------------------------
-- Leave balances for both the current year and history.
-- ---------------------------------------------------------------------
WITH demo_balances (employee_email, balance_year, granted, used, carried_over, expired) AS (
    VALUES
    ('leey217423@gmail.com', 2025, 15.0, 12.0, 0.0, 3.0),
    ('minjun.kim@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('seoyeon.park@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('jihoon.choi@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('haeun.jeong@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('yujin.han@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('jimin.oh@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('hyunwoo.kang@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('seoah.yoon@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('jiho.song@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('seojun.kim@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('jieun.park@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('sujin.bae@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('yerin.moon@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('doyoon.lee@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('daeun.choi@example.com', 2025, 15.0, 10.0, 0.0, 5.0),
    ('leey217423@gmail.com', 2026, 15.0, 2.0, 0.0, 0.0),
    ('minjun.kim@example.com', 2026, 18.0, 4.0, 0.0, 0.0),
    ('seoyeon.park@example.com', 2026, 16.0, 2.0, 0.0, 0.0),
    ('jihoon.choi@example.com', 2026, 15.0, 1.5, 0.0, 0.0),
    ('haeun.jeong@example.com', 2026, 16.0, 4.0, 0.0, 0.0),
    ('yujin.han@example.com', 2026, 15.0, 2.5, 0.0, 0.0),
    ('jimin.oh@example.com', 2026, 15.0, 3.0, 0.0, 0.0),
    ('hyunwoo.kang@example.com', 2026, 16.0, 2.0, 0.0, 0.0),
    ('seoah.yoon@example.com', 2026, 15.0, 2.0, 0.0, 0.0),
    ('jiho.song@example.com', 2026, 15.0, 2.0, 0.0, 0.0),
    ('seojun.kim@example.com', 2026, 15.0, 1.5, 0.0, 0.0),
    ('jieun.park@example.com', 2026, 15.0, 2.0, 0.0, 0.0),
    ('sujin.bae@example.com', 2026, 16.0, 0.5, 0.0, 0.0),
    ('yerin.moon@example.com', 2026, 15.0, 3.0, 0.0, 0.0),
    ('doyoon.lee@example.com', 2026, 15.0, 2.0, 0.0, 0.0),
    ('daeun.choi@example.com', 2026, 15.0, 2.0, 0.0, 0.0)
)
INSERT INTO leave_balances (employee_id, year, granted, used, carried_over, expired)
SELECT e.id, d.balance_year, d.granted, d.used, d.carried_over, d.expired
FROM demo_balances d
JOIN employees e ON e.email = d.employee_email
ON CONFLICT (employee_id, year) DO UPDATE SET
    granted = EXCLUDED.granted,
    used = EXCLUDED.used,
    carried_over = EXCLUDED.carried_over,
    expired = EXCLUDED.expired,
    updated_at = now();

-- ---------------------------------------------------------------------
-- Leave requests in every UI state and across several months/departments.
-- ---------------------------------------------------------------------
WITH demo_requests (
    employee_email, leave_code, start_date, end_date, days, deducted_days,
    status, reason, approver_email, approved_at, reject_reason, cancel_reason, created_at
) AS (
    VALUES
    ('leey217423@gmail.com', 'ANNUAL', DATE '2026-10-12', DATE '2026-10-12', 1.0, 1.0, 'APPROVED', '가을 휴식', 'admin', TIMESTAMPTZ '2026-09-22 09:40:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-16 09:00:00+09'),
    ('leey217423@gmail.com', 'ANNUAL', DATE '2026-11-05', DATE '2026-11-05', 1.0, 1.0, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 10:15:00+09'),
    ('leey217423@gmail.com', 'ANNUAL', DATE '2026-08-18', DATE '2026-08-18', 1.0, 1.0, 'REJECTED', '휴가 신청', 'admin', TIMESTAMPTZ '2026-08-15 11:00:00+09', '팀 릴리스 일정과 중복', NULL::text, TIMESTAMPTZ '2026-08-14 10:00:00+09'),
    ('leey217423@gmail.com', 'ANNUAL', DATE '2026-07-01', DATE '2026-07-01', 1.0, 1.0, 'CANCELLED', '여름휴가', 'admin', TIMESTAMPTZ '2026-06-25 10:00:00+09', NULL::text, '개인 일정 변경', TIMESTAMPTZ '2026-06-20 10:00:00+09'),
    ('leey217423@gmail.com', 'OFFICIAL', DATE '2026-08-10', DATE '2026-08-10', 1.0, 0.0, 'APPROVED', '사내 교육 참석', 'admin', TIMESTAMPTZ '2026-08-05 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-08-04 09:00:00+09'),
    ('minjun.kim@example.com', 'ANNUAL', DATE '2026-07-06', DATE '2026-07-08', 3.0, 3.0, 'APPROVED', '가족 여행', 'admin', TIMESTAMPTZ '2026-06-25 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-06-22 09:00:00+09'),
    ('minjun.kim@example.com', 'ANNUAL', DATE '2026-10-19', DATE '2026-10-19', 1.0, 1.0, 'CANCEL_REQUESTED', '휴가 계획 변경', 'admin', TIMESTAMPTZ '2026-09-18 10:00:00+09', NULL::text, '프로젝트 일정 변경', TIMESTAMPTZ '2026-09-15 09:00:00+09'),
    ('minjun.kim@example.com', 'ANNUAL', DATE '2026-11-09', DATE '2026-11-09', 1.0, 1.0, 'PENDING', '가족 행사', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:00:00+09'),
    ('seoyeon.park@example.com', 'ANNUAL', DATE '2026-08-10', DATE '2026-08-11', 2.0, 2.0, 'APPROVED', '여름휴가', 'admin', TIMESTAMPTZ '2026-07-30 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-07-28 09:00:00+09'),
    ('seoyeon.park@example.com', 'HALF_PM', DATE '2026-10-30', DATE '2026-10-30', 0.5, 0.5, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:10:00+09'),
    ('seoyeon.park@example.com', 'SICK', DATE '2026-06-17', DATE '2026-06-17', 1.0, 0.0, 'APPROVED', '건강검진', 'admin', TIMESTAMPTZ '2026-06-16 09:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-06-15 09:00:00+09'),
    ('jihoon.choi@example.com', 'HALF_AM', DATE '2026-06-22', DATE '2026-06-22', 0.5, 0.5, 'APPROVED', '병원 방문', 'admin', TIMESTAMPTZ '2026-06-19 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-06-18 09:00:00+09'),
    ('jihoon.choi@example.com', 'ANNUAL', DATE '2026-09-14', DATE '2026-09-14', 1.0, 1.0, 'APPROVED', '개인 휴식', 'admin', TIMESTAMPTZ '2026-09-10 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-08 09:00:00+09'),
    ('jihoon.choi@example.com', 'OFFICIAL', DATE '2026-07-13', DATE '2026-07-13', 1.0, 0.0, 'APPROVED', '고객사 방문', 'admin', TIMESTAMPTZ '2026-07-10 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-07-09 09:00:00+09'),
    ('jihoon.choi@example.com', 'CONDOLENCE', DATE '2026-11-12', DATE '2026-11-12', 1.0, 0.0, 'PENDING', '경조사 참석', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:20:00+09'),
    ('haeun.jeong@example.com', 'ANNUAL', DATE '2026-05-11', DATE '2026-05-14', 4.0, 4.0, 'APPROVED', '제주도 여행', 'admin', TIMESTAMPTZ '2026-04-24 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-04-20 09:00:00+09'),
    ('haeun.jeong@example.com', 'ANNUAL', DATE '2026-12-14', DATE '2026-12-14', 1.0, 1.0, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:30:00+09'),
    ('haeun.jeong@example.com', 'ANNUAL', DATE '2026-08-24', DATE '2026-08-25', 2.0, 2.0, 'REJECTED', '휴가 신청', 'admin', TIMESTAMPTZ '2026-08-20 10:00:00+09', '디자인 시안 마감 기간', NULL::text, TIMESTAMPTZ '2026-08-19 09:00:00+09'),
    ('yujin.han@example.com', 'ANNUAL', DATE '2026-04-20', DATE '2026-04-21', 2.0, 2.0, 'APPROVED', '봄 휴가', 'admin', TIMESTAMPTZ '2026-04-10 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-04-08 09:00:00+09'),
    ('yujin.han@example.com', 'HALF_PM', DATE '2026-04-22', DATE '2026-04-22', 0.5, 0.5, 'APPROVED', '가족 행사', 'admin', TIMESTAMPTZ '2026-04-15 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-04-14 09:00:00+09'),
    ('yujin.han@example.com', 'OFFICIAL', DATE '2026-10-23', DATE '2026-10-23', 1.0, 0.0, 'PENDING', '파트너 행사 참석', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:40:00+09'),
    ('jimin.oh@example.com', 'ANNUAL', DATE '2026-09-22', DATE '2026-09-22', 1.0, 1.0, 'APPROVED', '개인 휴식', 'admin', TIMESTAMPTZ '2026-09-18 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-15 09:00:00+09'),
    ('jimin.oh@example.com', 'ANNUAL', DATE '2026-12-07', DATE '2026-12-08', 2.0, 2.0, 'APPROVED', '겨울 휴가', 'admin', TIMESTAMPTZ '2026-09-10 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-08 09:00:00+09'),
    ('jimin.oh@example.com', 'HALF_AM', DATE '2026-10-07', DATE '2026-10-07', 0.5, 0.5, 'PENDING', '병원 방문', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 11:50:00+09'),
    ('hyunwoo.kang@example.com', 'ANNUAL', DATE '2026-03-16', DATE '2026-03-17', 2.0, 2.0, 'APPROVED', '데이터 컨퍼런스 후 휴식', 'admin', TIMESTAMPTZ '2026-03-06 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-03-04 09:00:00+09'),
    ('hyunwoo.kang@example.com', 'SICK', DATE '2026-06-30', DATE '2026-06-30', 1.0, 0.0, 'APPROVED', '건강검진', 'admin', TIMESTAMPTZ '2026-06-29 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-06-29 09:00:00+09'),
    ('hyunwoo.kang@example.com', 'ANNUAL', DATE '2026-11-23', DATE '2026-11-23', 1.0, 1.0, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:00:00+09'),
    ('seoah.yoon@example.com', 'ANNUAL', DATE '2026-02-09', DATE '2026-02-10', 2.0, 2.0, 'APPROVED', '설 연휴 여행', 'admin', TIMESTAMPTZ '2026-01-30 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-01-28 09:00:00+09'),
    ('seoah.yoon@example.com', 'OFFICIAL', DATE '2026-09-30', DATE '2026-09-30', 1.0, 0.0, 'APPROVED', '기술 세미나 참석', 'admin', TIMESTAMPTZ '2026-09-25 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-24 09:00:00+09'),
    ('seoah.yoon@example.com', 'ANNUAL', DATE '2026-10-26', DATE '2026-10-26', 1.0, 1.0, 'PENDING', '개인 휴식', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:10:00+09'),
    ('jiho.song@example.com', 'ANNUAL', DATE '2026-01-19', DATE '2026-01-20', 2.0, 2.0, 'APPROVED', '신년 휴가', 'admin', TIMESTAMPTZ '2026-01-09 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-01-07 09:00:00+09'),
    ('jiho.song@example.com', 'HALF_PM', DATE '2026-11-30', DATE '2026-11-30', 0.5, 0.5, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:20:00+09'),
    ('seojun.kim@example.com', 'ANNUAL', DATE '2026-08-31', DATE '2026-08-31', 1.0, 1.0, 'APPROVED', '영업 보상 휴가', 'admin', TIMESTAMPTZ '2026-08-25 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-08-24 09:00:00+09'),
    ('seojun.kim@example.com', 'HALF_AM', DATE '2026-09-01', DATE '2026-09-01', 0.5, 0.5, 'APPROVED', '병원 방문', 'admin', TIMESTAMPTZ '2026-08-28 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-08-27 09:00:00+09'),
    ('seojun.kim@example.com', 'ANNUAL', DATE '2026-12-21', DATE '2026-12-21', 1.0, 1.0, 'PENDING', '연말 개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:30:00+09'),
    ('jieun.park@example.com', 'ANNUAL', DATE '2026-07-27', DATE '2026-07-28', 2.0, 2.0, 'APPROVED', '여름 휴가', 'admin', TIMESTAMPTZ '2026-07-17 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-07-15 09:00:00+09'),
    ('jieun.park@example.com', 'OFFICIAL', DATE '2026-10-07', DATE '2026-10-07', 1.0, 0.0, 'PENDING', '고객 교육 참석', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:40:00+09'),
    ('sujin.bae@example.com', 'HALF_PM', DATE '2026-09-07', DATE '2026-09-07', 0.5, 0.5, 'APPROVED', '병원 방문', 'admin', TIMESTAMPTZ '2026-09-03 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-09-02 09:00:00+09'),
    ('sujin.bae@example.com', 'ANNUAL', DATE '2026-11-16', DATE '2026-11-16', 1.0, 1.0, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 12:50:00+09'),
    ('yerin.moon@example.com', 'ANNUAL', DATE '2026-05-18', DATE '2026-05-20', 3.0, 3.0, 'APPROVED', '가족 여행', 'admin', TIMESTAMPTZ '2026-05-08 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-05-06 09:00:00+09'),
    ('yerin.moon@example.com', 'ANNUAL', DATE '2026-06-08', DATE '2026-06-08', 1.0, 1.0, 'REJECTED', '휴가 신청', 'admin', TIMESTAMPTZ '2026-06-05 10:00:00+09', '브랜드 캠페인 마감', NULL::text, TIMESTAMPTZ '2026-06-04 09:00:00+09'),
    ('doyoon.lee@example.com', 'ANNUAL', DATE '2026-06-01', DATE '2026-06-02', 2.0, 2.0, 'APPROVED', '개인 휴식', 'admin', TIMESTAMPTZ '2026-05-22 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-05-20 09:00:00+09'),
    ('doyoon.lee@example.com', 'HALF_AM', DATE '2026-10-19', DATE '2026-10-19', 0.5, 0.5, 'PENDING', '병원 방문', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 13:00:00+09'),
    ('daeun.choi@example.com', 'ANNUAL', DATE '2026-08-03', DATE '2026-08-04', 2.0, 2.0, 'APPROVED', '여름 휴가', 'admin', TIMESTAMPTZ '2026-07-24 10:00:00+09', NULL::text, NULL::text, TIMESTAMPTZ '2026-07-22 09:00:00+09'),
    ('daeun.choi@example.com', 'ANNUAL', DATE '2026-11-02', DATE '2026-11-02', 1.0, 1.0, 'PENDING', '개인 일정', NULL::text, NULL::timestamptz, NULL::text, NULL::text, TIMESTAMPTZ '2026-09-22 13:10:00+09')
)
INSERT INTO leave_requests (
    employee_id, leave_type_id, start_date, end_date, days, deducted_days, applied_year,
    status, reason, approver_id, approved_at, reject_reason, cancel_reason, created_at, updated_at
)
SELECT e.id, lt.id, d.start_date, d.end_date, d.days, d.deducted_days, 2026,
       d.status, d.reason, approver.id, d.approved_at, d.reject_reason, d.cancel_reason,
       d.created_at, d.created_at
FROM demo_requests d
JOIN employees e ON e.email = d.employee_email
JOIN leave_types lt ON lt.code = d.leave_code
LEFT JOIN employees approver ON approver.email = d.approver_email
WHERE NOT EXISTS (
    SELECT 1 FROM leave_requests r
    WHERE r.employee_id = e.id AND r.start_date = d.start_date
);

-- Normalize the text fields of the demo requests if an earlier import used
-- the console code page instead of UTF-8.
UPDATE leave_requests
SET reason = CASE status
                 WHEN 'APPROVED' THEN '휴가 일정'
                 WHEN 'PENDING' THEN '휴가 신청 검토 요청'
                 WHEN 'REJECTED' THEN '개인 일정'
                 WHEN 'CANCEL_REQUESTED' THEN '휴가 취소 요청'
                 WHEN 'CANCELLED' THEN '휴가 일정 변경'
                 ELSE reason
             END,
    reject_reason = CASE WHEN status = 'REJECTED' THEN '업무 일정과 중복' ELSE reject_reason END,
    cancel_reason = CASE
                        WHEN status = 'CANCEL_REQUESTED' THEN '프로젝트 일정 변경'
                        WHEN status = 'CANCELLED' THEN '개인 일정 변경'
                        ELSE cancel_reason
                    END,
    updated_at = now()
WHERE id > 1;

-- Approved and cancellation-requested leave requests appear in the calendar.
INSERT INTO calendar_events (
    title, start_date, end_date, all_day, scope, source, color_hex,
    department_id, employee_id, leave_request_id, created_by
)
SELECT e.name || ' - ' || lt.name, r.start_date, r.end_date, TRUE, 'COMPANY', 'LEAVE_REQUEST',
       lt.color_hex, e.department_id, e.id, r.id, r.approver_id
FROM leave_requests r
JOIN employees e ON e.id = r.employee_id
JOIN leave_types lt ON lt.id = r.leave_type_id
WHERE r.status IN ('APPROVED', 'CANCEL_REQUESTED')
  AND NOT EXISTS (SELECT 1 FROM calendar_events ce WHERE ce.leave_request_id = r.id);

UPDATE calendar_events ce
SET title = e.name || ' - ' || lt.name
FROM leave_requests r
JOIN employees e ON e.id = r.employee_id
JOIN leave_types lt ON lt.id = r.leave_type_id
WHERE ce.leave_request_id = r.id;

UPDATE calendar_events SET title = '전사 타운홀 미팅'
WHERE source = 'ADMIN_EVENT' AND start_date = DATE '2026-10-16';
UPDATE calendar_events SET title = '제품개발 워크숍'
WHERE source = 'ADMIN_EVENT' AND start_date = DATE '2026-10-20';
UPDATE calendar_events SET title = '하반기 건강검진'
WHERE source = 'ADMIN_EVENT' AND start_date = DATE '2026-11-06';

-- A few non-leave company/departments events make the calendar useful on its own.
INSERT INTO calendar_events (
    title, start_date, end_date, all_day, scope, source, color_hex, department_id, created_by
)
SELECT '전사 타운홀 미팅', DATE '2026-10-16', DATE '2026-10-16', TRUE, 'COMPANY', 'ADMIN_EVENT', '#4f46e5', NULL,
       (SELECT id FROM employees WHERE email = 'admin')
WHERE NOT EXISTS (
    SELECT 1 FROM calendar_events WHERE title = '전사 타운홀 미팅' AND start_date = DATE '2026-10-16'
);

INSERT INTO calendar_events (
    title, start_date, end_date, all_day, scope, source, color_hex, department_id, created_by
)
SELECT '제품개발 워크숍', DATE '2026-10-20', DATE '2026-10-21', TRUE, 'DEPARTMENT', 'ADMIN_EVENT', '#06b6d4',
       (SELECT id FROM departments WHERE name = '제품개발팀' ORDER BY id LIMIT 1),
       (SELECT id FROM employees WHERE email = 'admin')
WHERE NOT EXISTS (
    SELECT 1 FROM calendar_events WHERE title = '제품개발 워크숍' AND start_date = DATE '2026-10-20'
);

INSERT INTO calendar_events (
    title, start_date, end_date, all_day, scope, source, color_hex, department_id, created_by
)
SELECT '하반기 건강검진', DATE '2026-11-06', DATE '2026-11-06', TRUE, 'COMPANY', 'ADMIN_EVENT', '#22c55e', NULL,
       (SELECT id FROM employees WHERE email = 'admin')
WHERE NOT EXISTS (
    SELECT 1 FROM calendar_events WHERE title = '하반기 건강검진' AND start_date = DATE '2026-11-06'
);

-- ---------------------------------------------------------------------
-- Notifications, policy exceptions and audit samples.
-- ---------------------------------------------------------------------
DELETE FROM notifications
WHERE created_at IN (
    TIMESTAMPTZ '2026-09-22 13:30:00+09',
    TIMESTAMPTZ '2026-09-22 12:00:00+09',
    TIMESTAMPTZ '2026-09-22 09:40:00+09',
    TIMESTAMPTZ '2026-09-22 13:10:00+09',
    TIMESTAMPTZ '2026-09-22 12:10:00+09',
    TIMESTAMPTZ '2026-09-10 10:00:00+09'
);
WITH demo_notifications (employee_email, notification_type, title, message, link, is_read, created_at) AS (
    VALUES
    ('admin', 'LEAVE_REQUESTED', '새 휴가 결재 요청', '구성원의 휴가 신청이 결재를 기다리고 있습니다.', '/approvals', FALSE, TIMESTAMPTZ '2026-09-22 13:30:00+09'),
    ('admin', 'SYSTEM_NOTICE', '연차 정책을 확인해주세요', '하반기 휴가 운영 정책과 블랙아웃 기간이 등록되었습니다.', '/admin/policy', FALSE, TIMESTAMPTZ '2026-09-22 12:00:00+09'),
    ('leey217423@gmail.com', 'LEAVE_APPROVED', '휴가가 승인되었습니다.', '연차 2026-10-12 ~ 2026-10-12', '/my-leaves', FALSE, TIMESTAMPTZ '2026-09-22 09:40:00+09'),
    ('leey217423@gmail.com', 'LEAVE_REQUESTED', '새 휴가 결재 요청', '최다은 - 연차 2026-11-02 ~ 2026-11-02', '/approvals', FALSE, TIMESTAMPTZ '2026-09-22 13:10:00+09'),
    ('seoyeon.park@example.com', 'LEAVE_REQUESTED', '새 휴가 결재 요청', '윤서아 - 연차 2026-10-26 ~ 2026-10-26', '/approvals', FALSE, TIMESTAMPTZ '2026-09-22 12:10:00+09'),
    ('jimin.oh@example.com', 'LEAVE_APPROVED', '휴가가 승인되었습니다.', '연차 2026-12-07 ~ 2026-12-08', '/my-leaves', TRUE, TIMESTAMPTZ '2026-09-10 10:00:00+09')
)
INSERT INTO notifications (employee_id, type, title, message, link, is_read, created_at)
SELECT e.id, d.notification_type, d.title, d.message, d.link, d.is_read, d.created_at
FROM demo_notifications d
JOIN employees e ON e.email = d.employee_email
WHERE NOT EXISTS (
    SELECT 1 FROM notifications n
    WHERE n.employee_id = e.id AND n.title = d.title AND n.message = d.message
);

INSERT INTO blackout_periods (start_date, end_date, name)
SELECT DATE '2026-10-19', DATE '2026-10-23', '제품 릴리스 집중 기간'
WHERE NOT EXISTS (
    SELECT 1 FROM blackout_periods WHERE start_date = DATE '2026-10-19' AND end_date = DATE '2026-10-23'
);

UPDATE blackout_periods SET name = '제품 릴리스 집중 기간'
WHERE start_date = DATE '2026-10-19' AND end_date = DATE '2026-10-23';

INSERT INTO blackout_periods (start_date, end_date, name)
SELECT DATE '2026-12-24', DATE '2026-12-31', '연말 결산 기간'
WHERE NOT EXISTS (
    SELECT 1 FROM blackout_periods WHERE start_date = DATE '2026-12-24' AND end_date = DATE '2026-12-31'
);

UPDATE blackout_periods SET name = '연말 결산 기간'
WHERE start_date = DATE '2026-12-24' AND end_date = DATE '2026-12-31';

DELETE FROM audit_logs
WHERE created_at IN (
    TIMESTAMPTZ '2026-09-22 13:05:00+09',
    TIMESTAMPTZ '2026-09-22 13:06:00+09',
    TIMESTAMPTZ '2026-09-22 09:40:00+09',
    TIMESTAMPTZ '2026-07-30 10:00:00+09',
    TIMESTAMPTZ '2026-08-20 10:00:00+09',
    TIMESTAMPTZ '2026-09-22 08:50:00+09',
    TIMESTAMPTZ '2026-09-15 09:00:00+09',
    TIMESTAMPTZ '2026-09-22 08:00:00+09',
    TIMESTAMPTZ '2026-09-22 13:20:00+09',
    TIMESTAMPTZ '2026-09-22 13:25:00+09'
);
WITH demo_audit (actor_email, action, entity_type, entity_id, detail, success, created_at) AS (
    VALUES
    ('admin', 'POST', 'departments', '8', '고객성공팀 부서를 생성했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 13:05:00+09'),
    ('admin', 'POST', 'employees', '8', '오지민 직원을 등록했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 13:06:00+09'),
    ('admin', 'approve', 'leave-requests', '1', '이예준의 연차 신청을 승인했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 09:40:00+09'),
    ('admin', 'approve', 'leave-requests', '12', '박서연의 연차 신청을 승인했습니다.', TRUE, TIMESTAMPTZ '2026-07-30 10:00:00+09'),
    ('admin', 'reject', 'leave-requests', '21', '정하은의 연차 신청을 반려했습니다. 사유: 디자인 시안 마감 기간', TRUE, TIMESTAMPTZ '2026-08-20 10:00:00+09'),
    ('leey217423@gmail.com', 'PUT', 'employees', '2', '내 프로필 정보를 수정했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 08:50:00+09'),
    ('minjun.kim@example.com', 'POST', 'leave-requests', '10', '휴가 취소 승인을 요청했습니다.', TRUE, TIMESTAMPTZ '2026-09-15 09:00:00+09'),
    ('admin', 'grant', 'leave', '2026', '2026년 연차를 전체 직원에게 부여했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 08:00:00+09'),
    ('admin', 'CALL', 'reports', NULL, '2026년 연차 사용 리포트를 조회했습니다.', TRUE, TIMESTAMPTZ '2026-09-22 13:20:00+09'),
    ('admin', 'DELETE', 'departments', '7', '존재하지 않는 테스트 부서 삭제 요청이 거부되었습니다.', FALSE, TIMESTAMPTZ '2026-09-22 13:25:00+09')
)
INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, detail, success, created_at)
SELECT e.id, e.name, d.action, d.entity_type, d.entity_id, d.detail, d.success, d.created_at
FROM demo_audit d
JOIN employees e ON e.email = d.actor_email
WHERE NOT EXISTS (
    SELECT 1 FROM audit_logs a WHERE a.detail = d.detail
);

COMMIT;
