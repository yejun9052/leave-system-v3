-- 부서를 지우면 그 부서로 기록된 휴가 일정까지 함께 삭제되던 문제(V3 의 ON DELETE CASCADE).
-- 휴가 일정에는 승인 당시 신청자의 부서가 기록되므로, 직원을 다른 부서로 옮긴 뒤 옛 부서를 지우면
-- 지난 휴가가 캘린더에서 사라졌다. 이제 부서를 지우면 일정은 남기고 부서 칸만 비운다.
-- (관리자가 등록한 부서 전용 일정은 DepartmentService.delete 가 따로 지운다)
ALTER TABLE calendar_events DROP CONSTRAINT fk_ce_department;
ALTER TABLE calendar_events
    ADD CONSTRAINT fk_ce_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL;

-- 일정이 없는 승인 휴가(승인·취소 요청 중)의 캘린더 일정을 다시 만든다. 형식은 LeaveRequestService.createCalendarEvent 와 같다.
INSERT INTO calendar_events (title, start_date, end_date, all_day, scope, source, color_hex,
                             department_id, employee_id, leave_request_id, created_by)
SELECT e.name || ' - ' || t.name
           || COALESCE('(' || r.special_rule_name || ')', '')
           || CASE WHEN t.portion = 'HOURLY' THEN ' ' || ROUND(r.days / 0.125)::int || '시간' ELSE '' END,
       r.start_date, r.end_date, TRUE, 'COMPANY', 'LEAVE_REQUEST', t.color_hex,
       e.department_id, e.id, r.id, r.approver_id
  FROM leave_requests r
  JOIN employees e ON e.id = r.employee_id
  JOIN leave_types t ON t.id = r.leave_type_id
 WHERE r.status IN ('APPROVED', 'CANCEL_REQUESTED')
   AND NOT EXISTS (SELECT 1 FROM calendar_events c WHERE c.leave_request_id = r.id);
