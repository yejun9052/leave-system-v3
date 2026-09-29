package com.company.leave.leave;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository requestRepository;
    private final LeaveTypeService leaveTypeService;
    private final EmployeeService employeeService;
    private final LeaveBalanceService balanceService;
    private final HolidayRepository holidayRepository;
    private final WorkdayCalculator workdayCalculator;
    private final PolicyService policyService;
    private final LeaveAccrualCalculator accrualCalculator;
    private final CalendarEventRepository calendarEventRepository;
    private final NotificationService notificationService;
    private final DepartmentRepository departmentRepository;
    private final BlackoutPeriodRepository blackoutPeriodRepository;

    public LeaveRequestService(LeaveRequestRepository requestRepository,
                               LeaveTypeService leaveTypeService,
                               EmployeeService employeeService,
                               LeaveBalanceService balanceService,
                               HolidayRepository holidayRepository,
                               WorkdayCalculator workdayCalculator,
                               PolicyService policyService,
                               LeaveAccrualCalculator accrualCalculator,
                               CalendarEventRepository calendarEventRepository,
                               NotificationService notificationService,
                               DepartmentRepository departmentRepository,
                               BlackoutPeriodRepository blackoutPeriodRepository) {
        this.requestRepository = requestRepository;
        this.leaveTypeService = leaveTypeService;
        this.employeeService = employeeService;
        this.balanceService = balanceService;
        this.holidayRepository = holidayRepository;
        this.workdayCalculator = workdayCalculator;
        this.policyService = policyService;
        this.accrualCalculator = accrualCalculator;
        this.calendarEventRepository = calendarEventRepository;
        this.notificationService = notificationService;
        this.departmentRepository = departmentRepository;
        this.blackoutPeriodRepository = blackoutPeriodRepository;
    }

    @Transactional
    public LeaveRequestDtos.Response create(Long employeeId, LeaveRequestDtos.Create req) {
        Employee employee = employeeService.getEntity(employeeId);
        LeaveType type = leaveTypeService.getEntity(req.leaveTypeId());
        if (!type.isActive()) {
            throw new BusinessException(ErrorCode.LEAVE_TYPE_NOT_FOUND, "사용할 수 없는 휴가 종류입니다.");
        }

        LocalDate start = req.startDate();
        LocalDate end = req.endDate();
        validatePeriod(start, end, type);

        Set<LocalDate> holidays = holidaysBetween(start, end);
        BigDecimal days = workdayCalculator.computeLeaveDays(start, end, type, holidays);
        if (days.signum() <= 0) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD, "신청 기간에 근무일이 없습니다.");
        }

        if (requestRepository.existsOverlap(employeeId, start, end)) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_OVERLAP);
        }

        LeavePolicy policy = policyService.getActivePolicy();
        validateUsagePolicy(employee, start, end, days, policy);
        int appliedYear = appliedYear(start, policy);

        // 실제 차감액 = 근무일수 × 휴가유형 deductDays (반차는 deductDays, 비차감 유형은 0)
        BigDecimal deduction = workdayCalculator.deductionFor(type, days);

        if (type.isDeductFromAnnual()) {
            LeaveBalance balance = balanceService.getOrCreate(employeeId, appliedYear);
            BigDecimal pending = requestRepository.sumPendingDeductedDays(employeeId, appliedYear);
            BigDecimal available = balance.remaining().subtract(pending);
            if (!policy.isAllowNegative() && deduction.compareTo(available) > 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE,
                        "잔여 연차가 부족합니다. (차감 " + deduction + "일 / 사용가능 " + available + "일)");
            }
        }

        LeaveRequest request = new LeaveRequest(
                employee, type, start, end, days, deduction, appliedYear, req.reason());
        requestRepository.save(request);

        notifyApprovers(employee, request);
        return LeaveRequestDtos.Response.from(request);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestDtos.Response> myRequests(Long employeeId, Pageable pageable) {
        return requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable)
                .map(LeaveRequestDtos.Response::from);
    }

    /** 결재자(팀장/관리자)가 처리해야 할 목록: 신규 신청 + 취소 요청. */
    @Transactional(readOnly = true)
    public List<LeaveRequestDtos.Response> pendingForApprover(Long approverId) {
        Employee approver = employeeService.getEntity(approverId);
        var statuses = java.util.EnumSet.of(
                com.company.leave.leave.domain.LeaveRequestStatus.PENDING,
                com.company.leave.leave.domain.LeaveRequestStatus.CANCEL_REQUESTED);
        List<LeaveRequest> list;
        if (isAdmin(approver)) {
            list = requestRepository.findForApproval(allEmployeeIds(), statuses);
        } else {
            Set<Long> memberIds = subordinateEmployeeIds(approver);
            list = memberIds.isEmpty() ? List.of()
                    : requestRepository.findForApproval(memberIds, statuses);
        }
        return list.stream().map(LeaveRequestDtos.Response::from).toList();
    }

    /**
     * 다른 직원의 데이터(연차 잔액 등) 조회 권한 검증.
     * 본인 · 관리자 · 대상자의 팀장(하위 부서 포함)만 허용. 그 외 FORBIDDEN.
     */
    @Transactional(readOnly = true)
    public void assertCanViewEmployeeData(Long callerId, Long targetEmployeeId) {
        if (callerId.equals(targetEmployeeId)) {
            return;
        }
        Employee caller = employeeService.getEntity(callerId);
        Employee target = employeeService.getEntity(targetEmployeeId);
        if (!canApprove(caller, target)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional
    public LeaveRequestDtos.Response approve(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING);
        }
        if (!canApprove(approver, request.getEmployee())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }

        if (request.getLeaveType().isDeductFromAnnual()) {
            LeaveBalance balance = balanceService.getOrCreate(
                    request.getEmployee().getId(), request.getAppliedYear());
            BigDecimal deduction = request.getDeductedDays();
            // 승인 시점 재검증: 그 사이 잔액이 줄어 초과되면 차단(동시 승인 over-spend 방지, @Version 과 병행)
            if (!policyService.getActivePolicy().isAllowNegative()
                    && deduction.compareTo(balance.remaining()) > 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE,
                        "승인 시점 잔여 연차가 부족합니다. (차감 " + deduction
                                + "일 / 잔여 " + balance.remaining() + "일)");
            }
            balance.addUsed(deduction);
        }
        request.approve(approver, Instant.now());
        createCalendarEvent(request);

        notificationService.notify(request.getEmployee().getId(), "LEAVE_APPROVED",
                "휴가가 승인되었습니다.",
                request.getLeaveType().getName() + " " + request.getStartDate()
                        + " ~ " + request.getEndDate(), "/my-leaves");
        return LeaveRequestDtos.Response.from(request);
    }

    @Transactional
    public LeaveRequestDtos.Response reject(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING);
        }
        if (!canApprove(approver, request.getEmployee())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }
        request.reject(approver, reason, Instant.now());
        notificationService.notify(request.getEmployee().getId(), "LEAVE_REJECTED",
                "휴가가 반려되었습니다.",
                (reason != null ? reason : "사유 미기재"), "/my-leaves");
        return LeaveRequestDtos.Response.from(request);
    }

    /**
     * 취소 처리.
     * <ul>
     *   <li>대기(PENDING): 본인/관리자 → 즉시 취소</li>
     *   <li>승인(APPROVED): 관리자 → 즉시 취소(환원), 본인 → 취소 요청(팀장 재승인 대기)</li>
     *   <li>취소요청(CANCEL_REQUESTED): 관리자 → 즉시 확정 취소</li>
     *   <li>이미 시작된 휴가: 취소 불가</li>
     * </ul>
     */
    @Transactional
    public LeaveRequestDtos.Response cancel(Long requestId, Long requesterId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee requester = employeeService.getEntity(requesterId);
        boolean owner = request.getEmployee().getId().equals(requesterId);
        boolean admin = isAdmin(requester);
        if (!owner && !admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        switch (request.getStatus()) {
            case PENDING -> request.cancel();
            case APPROVED -> {
                if (!request.getStartDate().isAfter(LocalDate.now())) {
                    throw new BusinessException(ErrorCode.LEAVE_ALREADY_STARTED);
                }
                if (admin) {
                    finalizeCancel(request);
                } else {
                    request.requestCancel(reason);
                    notifyApproversForCancel(request);
                }
            }
            case CANCEL_REQUESTED -> {
                if (admin) {
                    finalizeCancel(request);
                } else {
                    throw new BusinessException(ErrorCode.CONFLICT, "이미 취소 요청 상태입니다.");
                }
            }
            default -> throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING,
                    "취소할 수 없는 상태입니다.");
        }
        return LeaveRequestDtos.Response.from(request);
    }

    /** 팀장/관리자가 취소 요청을 승인 → 확정 취소(잔액 환원, 캘린더 삭제). */
    @Transactional
    public LeaveRequestDtos.Response approveCancellation(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        if (!canApprove(approver, request.getEmployee())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }
        finalizeCancel(request);
        notificationService.notify(request.getEmployee().getId(), "LEAVE_CANCEL_APPROVED",
                "휴가 취소가 승인되었습니다.",
                request.getLeaveType().getName() + " " + request.getStartDate()
                        + " ~ " + request.getEndDate(), "/my-leaves");
        return LeaveRequestDtos.Response.from(request);
    }

    /** 팀장/관리자가 취소 요청을 반려 → 승인 상태로 복귀. */
    @Transactional
    public LeaveRequestDtos.Response rejectCancellation(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        if (!canApprove(approver, request.getEmployee())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }
        request.rejectCancel();
        notificationService.notify(request.getEmployee().getId(), "LEAVE_CANCEL_REJECTED",
                "휴가 취소 요청이 반려되었습니다.",
                (reason != null ? reason : "사유 미기재"), "/my-leaves");
        return LeaveRequestDtos.Response.from(request);
    }

    /** 확정 취소 공통 처리: 잔액 환원 + 캘린더 일정 삭제 + 상태 CANCELLED. */
    private void finalizeCancel(LeaveRequest request) {
        if (request.getLeaveType().isDeductFromAnnual()) {
            LeaveBalance balance = balanceService.getOrCreate(
                    request.getEmployee().getId(), request.getAppliedYear());
            balance.restoreUsed(request.getDeductedDays());
        }
        calendarEventRepository.deleteByLeaveRequestId(request.getId());
        request.cancel();
    }

    private void notifyApproversForCancel(LeaveRequest request) {
        Employee employee = request.getEmployee();
        Department dept = employee.getDepartment();
        if (dept != null && dept.getLead() != null
                && !dept.getLead().getId().equals(employee.getId())) {
            notificationService.notify(dept.getLead().getId(), "LEAVE_CANCEL_REQUESTED",
                    "휴가 취소 요청",
                    employee.getName() + " - " + request.getLeaveType().getName() + " "
                            + request.getStartDate() + " ~ " + request.getEndDate(), "/approvals");
        }
    }

    // --- helpers ---

    /** 정책 기반 사용 통제 검증 (블랙아웃/사전신청/연속일/팀 동시부재). */
    private void validateUsagePolicy(Employee employee, LocalDate start, LocalDate end,
                                     BigDecimal days, LeavePolicy policy) {
        if (blackoutPeriodRepository.existsOverlap(start, end)) {
            throw new BusinessException(ErrorCode.LEAVE_BLACKOUT);
        }
        if (policy.getMinAdvanceDays() > 0) {
            LocalDate earliest = LocalDate.now().plusDays(policy.getMinAdvanceDays());
            if (start.isBefore(earliest)) {
                throw new BusinessException(ErrorCode.LEAVE_MIN_ADVANCE,
                        "최소 " + policy.getMinAdvanceDays() + "일 전에 신청해야 합니다.");
            }
        }
        if (policy.getMaxConsecutiveDays() > 0
                && days.compareTo(BigDecimal.valueOf(policy.getMaxConsecutiveDays())) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_MAX_CONSECUTIVE,
                    "최대 연속 " + policy.getMaxConsecutiveDays() + "일까지 사용할 수 있습니다.");
        }
        if (policy.getMaxConcurrentAbsence() > 0 && employee.getDepartmentId() != null) {
            long teamOnLeave = requestRepository.findApprovedBetween(start, end).stream()
                    .filter(r -> employee.getDepartmentId().equals(r.getEmployee().getDepartmentId()))
                    .filter(r -> !r.getEmployee().getId().equals(employee.getId()))
                    .map(r -> r.getEmployee().getId())
                    .distinct()
                    .count();
            if (teamOnLeave + 1 > policy.getMaxConcurrentAbsence()) {
                throw new BusinessException(ErrorCode.LEAVE_TEAM_LIMIT,
                        "같은 기간 팀 내 최대 " + policy.getMaxConcurrentAbsence() + "명까지 휴가가 가능합니다.");
            }
        }
    }


    private void validatePeriod(LocalDate start, LocalDate end, LeaveType type) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD, "종료일이 시작일보다 빠릅니다.");
        }
        if (type.isHalfDay() && !start.isEqual(end)) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD, "반차는 하루만 신청할 수 있습니다.");
        }
    }

    private Set<LocalDate> holidaysBetween(LocalDate start, LocalDate end) {
        return holidayRepository.findByDateBetweenOrderByDateAsc(start, end).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());
    }

    private int appliedYear(LocalDate start, LeavePolicy policy) {
        if (policy.getGrantBasis() == GrantBasis.FISCAL_YEAR) {
            return accrualCalculator.currentFiscalStart(start, policy).getYear();
        }
        return start.getYear();
    }

    private void createCalendarEvent(LeaveRequest request) {
        Employee e = request.getEmployee();
        CalendarEvent event = CalendarEvent.builder()
                .title(e.getName() + " - " + request.getLeaveType().getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .allDay(true)
                .scope(CalendarEventScope.COMPANY)
                .source(CalendarEventSource.LEAVE_REQUEST)
                .colorHex(request.getLeaveType().getColorHex())
                .departmentId(e.getDepartmentId())
                .employeeId(e.getId())
                .leaveRequestId(request.getId())
                .createdBy(request.getApprover() != null ? request.getApprover().getId() : null)
                .build();
        calendarEventRepository.save(event);
    }

    private void notifyApprovers(Employee employee, LeaveRequest request) {
        Department dept = employee.getDepartment();
        if (dept != null && dept.getLead() != null
                && !dept.getLead().getId().equals(employee.getId())) {
            notificationService.notify(dept.getLead().getId(), "LEAVE_REQUESTED",
                    "새 휴가 결재 요청",
                    employee.getName() + " - " + request.getLeaveType().getName() + " "
                            + request.getStartDate() + " ~ " + request.getEndDate(), "/approvals");
        }
    }

    private boolean isAdmin(Employee e) {
        return e.hasRole(Role.SUPER_ADMIN) || e.hasRole(Role.HR_ADMIN);
    }

    /** 결재 권한: 관리자이거나, 대상자가 본인이 팀장인 부서(하위 포함)에 속함 */
    private boolean canApprove(Employee approver, Employee target) {
        if (isAdmin(approver)) {
            return true;
        }
        Long targetDeptId = target.getDepartmentId();
        if (targetDeptId == null) {
            return false;
        }
        return subordinateDeptIds(approver).contains(targetDeptId);
    }

    private Set<Long> subordinateDeptIds(Employee lead) {
        Set<Long> deptIds = new HashSet<>();
        for (Department led : departmentRepository.findByLeadId(lead.getId())) {
            deptIds.addAll(departmentRepository.findSubtreeIds(led.getId()));
        }
        return deptIds;
    }

    private Set<Long> subordinateEmployeeIds(Employee lead) {
        Set<Long> deptIds = subordinateDeptIds(lead);
        if (deptIds.isEmpty()) {
            return Set.of();
        }
        return employeeService.employeeIdsInDepartments(deptIds);
    }

    private java.util.Collection<Long> allEmployeeIds() {
        return employeeService.allEmployeeIds();
    }

    private LeaveRequest getRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_REQUEST_NOT_FOUND));
    }
}
