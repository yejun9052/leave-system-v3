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
import com.company.leave.leave.domain.ApprovalStage;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.mail.AccountMailEvents;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);
    private static final String NO_HR_APPROVER_WARNING = "결재할 인사관리자가 없습니다. 관리자에게 문의하세요.";

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
    private final ApplicationEventPublisher eventPublisher;

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
                               BlackoutPeriodRepository blackoutPeriodRepository,
                               ApplicationEventPublisher eventPublisher) {
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
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LeaveRequestDtos.Response create(Long employeeId, LeaveRequestDtos.Create req) {
        Employee employee = employeeService.getEntity(employeeId);
        if (employee.isSystemAccount()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리 전용 계정은 휴가를 신청할 수 없습니다.");
        }
        LeaveType type = leaveTypeService.getEntity(req.leaveTypeId());
        if (!type.isActive()) {
            throw new BusinessException(ErrorCode.LEAVE_TYPE_NOT_FOUND, "사용할 수 없는 휴가 종류입니다.");
        }
        LeavePolicy policy = policyService.getActivePolicy();
        if (!policy.allows(type.getPortion())) {
            throw new BusinessException(ErrorCode.LEAVE_TYPE_DISABLED,
                    "현재 정책에서 사용할 수 없는 휴가 종류입니다: " + type.getName());
        }

        LocalDate start = req.startDate();
        LocalDate end = req.endDate();
        Plan plan = plan(employee, type, policy, start, end, req.hours(), req.specialRuleId(), false);

        if (plan.forfeit().signum() > 0 && !Boolean.TRUE.equals(req.forfeitAcknowledged())) {
            throw new BusinessException(ErrorCode.LEAVE_FORFEIT_NOT_ACKNOWLEDGED,
                    type.getName() + "가 승인되면 남은 연차 " + plain(plan.forfeit())
                            + "일이 소멸됩니다. 안내를 확인한 뒤 신청해 주세요.");
        }

        String hrDirectReason = resolveHrDirect(employee, req.hrDirectReason(), policy);

        LeaveRequest request = new LeaveRequest(
                employee, type, start, end, plan.days(), plan.deduction(), plan.appliedYear(), req.reason());
        SpecialLeaveRule specialRule = plan.specialRule();
        if (specialRule != null) {
            request.attachSpecialRule(specialRule.getId(), specialRule.getName(), specialRule.getDays());
        }
        if (hrDirectReason != null) {
            request.routeDirectToHr(hrDirectReason);
        }
        requestRepository.save(request);

        String warning = notifyApprovers(request, policy);
        return LeaveRequestDtos.Response.from(request).withRequestWarning(warning);
    }

    /**
     * 신청 검증·계산 결과. 신청 생성과 미리보기가 같은 계산({@link #plan})을 써서 화면 안내와 실제 처리가 어긋나지 않게 한다.
     *
     * @param days        기록 일수(종일=근무일 수, 반차 0.5 …)
     * @param workdays    기간 내 근무일 수(주말·공휴일 제외)
     * @param deduction   연차 차감액(비차감 종류는 0)
     * @param specialRule 고른 경조사 규정(없으면 null)
     * @param forfeit     승인 시 소멸될 남은 연차(병가·공가, 그 외 0)
     */
    private record Plan(BigDecimal days, int workdays, BigDecimal deduction, int appliedYear,
                        SpecialLeaveRule specialRule, BigDecimal forfeit) {
    }

    /**
     * 신청 기간·종류에 대한 검증과 일수·차감 계산(저장하지 않음). 규칙 위반은 BusinessException.
     *
     * @param preview 미리보기면 true: 경조사 규정을 아직 고르지 않았으면 규정 검사를 건너뛴다
     */
    private Plan plan(Employee employee, LeaveType type, LeavePolicy policy, LocalDate start, LocalDate end,
                      Integer requestedHours, Long specialRuleId, boolean preview) {
        Long employeeId = employee.getId();
        validatePeriod(start, end, type);
        Integer hours = hoursFor(type, requestedHours);

        Set<LocalDate> holidays = holidaysBetween(start, end);
        // 시작일은 근무일이어야 한다(반차 포함). 기간 중간·끝의 주말·공휴일은 허용하고 차감에서만 뺀다.
        if (!workdayCalculator.isWorkday(start, holidays)) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD,
                    "시작일이 주말 또는 공휴일입니다. 근무일부터 신청해 주세요.");
        }
        BigDecimal days = workdayCalculator.computeLeaveDays(start, end, type, holidays, hours);
        if (days.signum() <= 0) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD, "신청 기간에 근무일이 없습니다.");
        }

        SpecialLeaveRule specialRule = preview && specialRuleId == null
                ? null
                : resolveSpecialRule(type, specialRuleId, days);

        validateNoOverlap(employeeId, start, end, type, days);

        validateUsagePolicy(employee, type, start, end, days, policy);
        int appliedYear = appliedYear(start, policy);

        BigDecimal forfeit = type.isRequiresAnnualExhausted()
                ? requireAnnualExhausted(employeeId, appliedYear, type, false)
                : BigDecimal.ZERO;

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
        int workdays = workdayCalculator.countWorkdays(start, end, holidays);
        return new Plan(days, workdays, deduction, appliedYear, specialRule, forfeit);
    }

    /**
     * 신청자의 결재 경로(신청 화면 안내용). 정책이 ON 이고 1차 결재할 팀장이 있으면 팀장 단계,
     * 팀장이 오늘 종일 휴가로 부재면 인사관리자에게 바로 신청할 수 있다.
     */
    @Transactional(readOnly = true)
    public LeaveRequestDtos.ApprovalRoute approvalRoute(Long employeeId) {
        Employee employee = employeeService.getEntity(employeeId);
        LeavePolicy policy = policyService.getActivePolicy();
        Employee lead = policy.isLeadApprovalRequired() ? leadApproverOf(employee) : null;
        if (lead == null) {
            return new LeaveRequestDtos.ApprovalRoute(policy.isLeadApprovalRequired(), ApprovalStage.HR,
                    null, false, null, false);
        }
        LeaveRequest absence = leadAbsenceToday(lead);
        return new LeaveRequestDtos.ApprovalRoute(true, ApprovalStage.LEAD, lead.getName(), absence != null,
                absence != null ? absence.getLeaveType().getName() : null, absence != null);
    }

    /**
     * 휴가 종류별 신청 가능 여부(신청 화면 안내용). 정책·병가·공가 조건만 본다(기간·잔액 검사는 신청 때).
     *
     * @param startDate 신청 예정 시작일(적용 연도 판단). 없으면 오늘
     */
    @Transactional
    public LeaveRequestDtos.Eligibility eligibility(Long employeeId, Long leaveTypeId, LocalDate startDate) {
        return eligibility(employeeId, leaveTypeId, startDate, null, null, null);
    }

    /**
     * 신청 가능 여부. 시작일과 종료일을 모두 주면 신청 미리보기: 신청과 같은 계산({@link #plan})으로
     * 기간 근무일·연차 차감·신청 후 잔여를 알려 주고, 규칙 위반이면 allowed=false 와 사유를 준다. 저장하지 않는다.
     * 종료일이 없으면 종류 단위 조건(정책·병가·공가)만 본다.
     *
     * @param hours         시간차의 시간 수(시간차 미리보기에 필요)
     * @param specialRuleId 경조사 규정(아직 안 골랐으면 null, 규정 일수 검사를 건너뛴다)
     */
    @Transactional
    public LeaveRequestDtos.Eligibility eligibility(Long employeeId, Long leaveTypeId, LocalDate startDate,
                                                   LocalDate endDate, Integer hours, Long specialRuleId) {
        LeaveType type = leaveTypeService.getEntity(leaveTypeId);
        LeavePolicy policy = policyService.getActivePolicy();
        if (!type.isActive() || !policy.allows(type.getPortion())) {
            return new LeaveRequestDtos.Eligibility(false, "현재 정책에서 사용할 수 없는 휴가 종류입니다.",
                    null, BigDecimal.ZERO);
        }
        int year = appliedYear(startDate != null ? startDate : LocalDate.now(), policy);
        BigDecimal remaining = balanceService.getOrCreate(employeeId, year).remaining();
        if (startDate != null && endDate != null) {
            return preview(employeeId, type, policy, startDate, endDate, hours, specialRuleId, remaining);
        }
        if (!type.isRequiresAnnualExhausted()) {
            return new LeaveRequestDtos.Eligibility(true, null, remaining, BigDecimal.ZERO);
        }
        try {
            BigDecimal forfeit = requireAnnualExhausted(employeeId, year, type, false);
            return new LeaveRequestDtos.Eligibility(true, null, remaining, forfeit);
        } catch (BusinessException ex) {
            return new LeaveRequestDtos.Eligibility(false, ex.getMessage(), remaining, BigDecimal.ZERO);
        }
    }

    /** 신청 미리보기. 신청 후 잔여 = 잔여 − 결재 대기 차감 − 이번 차감 − 소멸 예정. */
    private LeaveRequestDtos.Eligibility preview(Long employeeId, LeaveType type, LeavePolicy policy,
                                                 LocalDate start, LocalDate end, Integer hours,
                                                 Long specialRuleId, BigDecimal remaining) {
        Employee employee = employeeService.getEntity(employeeId);
        if (employee.isSystemAccount()) {
            return new LeaveRequestDtos.Eligibility(false, "관리 전용 계정은 휴가를 신청할 수 없습니다.",
                    remaining, BigDecimal.ZERO);
        }
        Plan plan;
        try {
            plan = plan(employee, type, policy, start, end, hours, specialRuleId, true);
        } catch (BusinessException ex) {
            return new LeaveRequestDtos.Eligibility(false, ex.getMessage(), remaining, BigDecimal.ZERO);
        }
        BigDecimal pending = requestRepository.sumPendingDeductedDays(employeeId, plan.appliedYear());
        BigDecimal remainingAfter = remaining.subtract(pending).subtract(plan.deduction()).subtract(plan.forfeit());
        return new LeaveRequestDtos.Eligibility(true, null, remaining, plan.forfeit(),
                plan.workdays(), plan.deduction(), pending, remainingAfter);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestDtos.Response> myRequests(Long employeeId, Pageable pageable) {
        return requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable)
                .map(LeaveRequestDtos.Response::from);
    }

    /**
     * 결재자가 처리해야 할 목록.
     * <ul>
     *   <li>인사관리자: 인사 단계 건(1차 승인 건, 인사 직행 대기 건, 취소 요청)</li>
     *   <li>팀장: 담당 부서 일반 직원의 팀장 단계 대기 건(정책 ON 일 때만)</li>
     * </ul>
     * 팀장이면서 관리자인 사람은 둘 다 본다.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDtos.Response> pendingForApprover(Long approverId) {
        Employee approver = employeeService.getEntity(approverId);
        if (isSystemAdmin(approver)) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }
        LeavePolicy policy = policyService.getActivePolicy();
        Map<Long, LeaveRequest> inbox = new LinkedHashMap<>();
        if (isHrApprover(approver)) {
            requestRepository.findForApproval(allEmployeeIds(), EnumSet.of(LeaveRequestStatus.PENDING,
                            LeaveRequestStatus.LEAD_APPROVED, LeaveRequestStatus.CANCEL_REQUESTED)).stream()
                    .filter(r -> stageOf(r, policy) == ApprovalStage.HR)
                    .forEach(r -> inbox.put(r.getId(), r));
        }
        if (policy.isLeadApprovalRequired()) {
            Set<Long> memberIds = subordinateEmployeeIds(approver);
            if (!memberIds.isEmpty()) {
                requestRepository.findForApproval(memberIds, EnumSet.of(LeaveRequestStatus.PENDING)).stream()
                        .filter(r -> stageOf(r, policy) == ApprovalStage.LEAD && canLeadApprove(approver, r.getEmployee()))
                        .forEach(r -> inbox.putIfAbsent(r.getId(), r));
            }
        }
        return inbox.values().stream()
                .sorted(java.util.Comparator.comparing(LeaveRequest::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(r -> LeaveRequestDtos.Response.from(r)
                        .withInbox(stageOf(r, policy), teamLimitWarning(r, policy)))
                .toList();
    }

    /**
     * 캘린더 날짜 상세용: 그날에 걸친 휴가 목록(부서·이름 순).
     * <ul>
     *   <li>승인(취소 요청 중 포함): 캘린더처럼 모두에게 보인다</li>
     *   <li>결재 대기(대기·1차 승인): 본인, 인사관리자·최고관리자, 그 신청을 팀장 단계로 결재할 수 있는 팀장에게만</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDtos.DayLeave> leavesOnDay(Long callerId, LocalDate date) {
        Employee caller = employeeService.getEntity(callerId);
        boolean admin = isAdmin(caller);
        return requestRepository.findByStatusInOverlapping(EnumSet.of(LeaveRequestStatus.PENDING,
                        LeaveRequestStatus.LEAD_APPROVED, LeaveRequestStatus.APPROVED,
                        LeaveRequestStatus.CANCEL_REQUESTED), date, date).stream()
                .filter(r -> !r.isAwaitingApproval() || r.getEmployee().getId().equals(callerId)
                        || admin || canLeadApprove(caller, r.getEmployee()))
                .sorted(java.util.Comparator
                        .comparing((LeaveRequest r) -> r.getEmployee().getDepartment() != null
                                        ? r.getEmployee().getDepartment().getName() : null,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(r -> r.getEmployee().getName()))
                .map(r -> LeaveRequestDtos.DayLeave.from(r, r.getEmployee().getId().equals(callerId)))
                .toList();
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
        if (!isAdmin(caller) && !isInChargeOf(caller, target)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 승인. 팀장 단계면 1차 승인(인사 결재 대기로), 인사 단계면 최종 승인(잔액 차감·캘린더 등록·병가 소멸).
     * 팀장 단계 건은 팀장만, 인사 단계 건은 인사관리자만 결재한다.
     */
    @Transactional
    public LeaveRequestDtos.Response approve(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isAwaitingApproval()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING);
        }
        LeavePolicy policy = policyService.getActivePolicy();
        if (stageOf(request, policy) == ApprovalStage.LEAD) {
            if (!canLeadApprove(approver, request.getEmployee())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION,
                        "팀장 1차 승인 단계입니다. 담당 팀장만 승인할 수 있습니다.");
            }
            request.leadApprove(approver, Instant.now());
            notifyLeadApproved(request);
            return LeaveRequestDtos.Response.from(request);
        }
        if (!isHrApprover(approver)) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION, "최종 승인은 인사관리자가 합니다.");
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
        BigDecimal forfeited = BigDecimal.ZERO;
        if (request.getLeaveType().isRequiresAnnualExhausted()) {
            // 신청 후 상황이 바뀌었을 수 있으므로 승인 시점에 조건을 다시 확인하고, 남은 연차(1일 미만)를 소멸
            Long employeeId = request.getEmployee().getId();
            forfeited = requireAnnualExhausted(employeeId, request.getAppliedYear(), request.getLeaveType(), true);
            if (forfeited.signum() > 0) {
                balanceService.getOrCreate(employeeId, request.getAppliedYear()).forfeit(forfeited);
                request.recordForfeit(forfeited);
            }
        }
        request.approve(approver, Instant.now());
        createCalendarEvent(request);

        notificationService.notify(request.getEmployee().getId(), "LEAVE_APPROVED",
                "휴가가 승인되었습니다.",
                request.getLeaveType().getName() + " " + request.getStartDate()
                        + " ~ " + request.getEndDate(), "/my-leaves");
        if (forfeited.signum() > 0) {
            notifyForfeited(request, forfeited);
        }
        return LeaveRequestDtos.Response.from(request);
    }

    /** 반려. 팀장 단계는 팀장이, 인사 단계(1차 승인 건 포함)는 인사관리자가 반려한다. */
    @Transactional
    public LeaveRequestDtos.Response reject(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isAwaitingApproval()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING);
        }
        boolean allowed = stageOf(request, policyService.getActivePolicy()) == ApprovalStage.LEAD
                ? canLeadApprove(approver, request.getEmployee())
                : isHrApprover(approver);
        if (!allowed) {
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
     *   <li>대기·1차 승인(PENDING·LEAD_APPROVED): 본인/인사관리자 → 즉시 취소(아직 확정 전)</li>
     *   <li>승인(APPROVED): 인사관리자 → 즉시 취소(환원), 본인 → 취소 요청(인사관리자 결재 대기)</li>
     *   <li>취소요청(CANCEL_REQUESTED): 인사관리자 → 즉시 확정 취소</li>
     *   <li>이미 시작된 휴가: 취소 불가</li>
     * </ul>
     */
    @Transactional
    public LeaveRequestDtos.Response cancel(Long requestId, Long requesterId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee requester = employeeService.getEntity(requesterId);
        boolean owner = request.getEmployee().getId().equals(requesterId);
        boolean admin = isHrApprover(requester);
        if (!owner && !admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        switch (request.getStatus()) {
            case PENDING, LEAD_APPROVED -> request.cancel();
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

    /** 인사관리자가 취소 요청을 승인 → 확정 취소(잔액 환원, 캘린더 삭제, 팀장에게 안내). */
    @Transactional
    public LeaveRequestDtos.Response approveCancellation(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        if (!isHrApprover(approver)) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION, "취소 요청은 인사관리자가 결재합니다.");
        }
        finalizeCancel(request);
        notificationService.notify(request.getEmployee().getId(), "LEAVE_CANCEL_APPROVED",
                "휴가 취소가 승인되었습니다.",
                request.getLeaveType().getName() + " " + request.getStartDate()
                        + " ~ " + request.getEndDate(), "/my-leaves");
        return LeaveRequestDtos.Response.from(request);
    }

    /** 인사관리자가 취소 요청을 반려 → 승인 상태로 복귀. */
    @Transactional
    public LeaveRequestDtos.Response rejectCancellation(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        if (!isHrApprover(approver)) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION, "취소 요청은 인사관리자가 결재합니다.");
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
        // 병가·공가 승인으로 소멸시켰던 남은 연차 되돌림
        BigDecimal forfeited = request.takeForfeitForRestore();
        if (forfeited.signum() > 0) {
            balanceService.getOrCreate(request.getEmployee().getId(), request.getAppliedYear())
                    .restoreForfeit(forfeited);
        }
        calendarEventRepository.deleteByLeaveRequestId(request.getId());
        request.cancel();
        // 승인됐던 휴가가 취소되면 팀장에게 안내만 보낸다(취소 결재는 인사관리자)
        Employee lead = leadApproverOf(request.getEmployee());
        if (lead != null) {
            notificationService.notify(lead.getId(), "LEAVE_CANCELLED_INFO", "팀원 휴가 취소",
                    request.getEmployee().getName() + "님의 " + request.getLeaveType().getName() + "("
                            + period(request) + ") 사용이 취소되었습니다.", "/calendar");
        }
    }

    /** 승인된 휴가의 취소 요청은 인사관리자에게 바로 간다. */
    private void notifyApproversForCancel(LeaveRequest request) {
        Employee employee = request.getEmployee();
        for (Long adminId : adminIdsExcept(employee)) {
            notificationService.notify(adminId, "LEAVE_CANCEL_REQUESTED",
                    "휴가 취소 요청",
                    employee.getName() + " - " + request.getLeaveType().getName() + " " + period(request),
                    "/approvals");
        }
    }

    /** 팀장 1차 승인 알림: 인사관리자에게 최종 승인 요청, 신청자에게 진행 안내. */
    private void notifyLeadApproved(LeaveRequest request) {
        Employee employee = request.getEmployee();
        String summary = employee.getName() + " - " + request.getLeaveType().getName() + " " + period(request);
        for (Long adminId : adminIdsExcept(employee)) {
            notificationService.notify(adminId, "LEAVE_FINAL_APPROVAL_REQUESTED", "휴가 최종 승인 요청",
                    summary + " (팀장 1차 승인 완료)", "/approvals");
        }
        notificationService.notify(employee.getId(), "LEAVE_LEAD_APPROVED", "팀장이 휴가를 1차 승인했습니다.",
                request.getLeaveType().getName() + " " + period(request) + " · 인사관리자 최종 승인 대기", "/my-leaves");
    }

    // --- 2단계 결재 ---

    /**
     * 신청 건의 현재 결재 단계. 신청 때 고정하지 않고 결재 시점의 정책·조직으로 판단한다
     * (정책을 바꾸면 대기 중인 건도 바뀐 흐름을 따른다).
     */
    private ApprovalStage stageOf(LeaveRequest request, LeavePolicy policy) {
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            return ApprovalStage.HR; // 1차 승인 건, 취소 요청
        }
        if (!policy.isLeadApprovalRequired() || request.getHrDirectReason() != null
                || leadApproverOf(request.getEmployee()) == null) {
            return ApprovalStage.HR;
        }
        return ApprovalStage.LEAD;
    }

    /**
     * 신청자를 1차 결재할 팀장: 소속 부서부터 상위로 올라가며 처음 만나는 재직 중인 부서장(본인 제외).
     * 팀장·인사관리자 본인의 신청이거나 부서장이 없으면 null(인사관리자가 바로 결재).
     * 인사관리자는 팀장 역할·부서장 지정 여부와 무관하게 본인 휴가를 결재할 수 있다.
     */
    private Employee leadApproverOf(Employee applicant) {
        if (isTeamLead(applicant) || isHrApprover(applicant)) {
            return null;
        }
        for (Department d = applicant.getDepartment(); d != null; d = d.getParent()) {
            Employee lead = d.getLead();
            if (lead != null && !isSystemAdmin(lead) && !lead.getId().equals(applicant.getId()) && lead.isActive()) {
                return lead;
            }
        }
        return null;
    }

    /** 팀장 단계 결재 권한: 신청자가 일반 직원이고, 결재자가 담당 부서(하위 포함)의 팀장. */
    private boolean canLeadApprove(Employee approver, Employee target) {
        return !isSystemAdmin(approver) && !isTeamLead(target) && isInChargeOf(approver, target);
    }

    /** 팀장이 오늘 종일 휴가(최종 승인)로 부재인지. 부재면 그 휴가, 아니면 null. 반차·시간차는 부재로 보지 않는다. */
    private LeaveRequest leadAbsenceToday(Employee lead) {
        LocalDate today = LocalDate.now();
        return requestRepository.findApprovedBetween(today, today).stream()
                .filter(r -> r.getEmployee().getId().equals(lead.getId()))
                .filter(r -> !r.getLeaveType().isPartialDay())
                .findFirst().orElse(null);
    }

    /**
     * 팀장 부재로 인사관리자에게 바로 신청하는 경우의 사유 확인.
     * 정책 OFF 이거나 팀장이 없으면 원래 인사관리자에게 가므로 사유를 쓰지 않는다(null).
     */
    private String resolveHrDirect(Employee applicant, String reason, LeavePolicy policy) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        Employee lead = policy.isLeadApprovalRequired() ? leadApproverOf(applicant) : null;
        if (lead == null) {
            return null;
        }
        if (leadAbsenceToday(lead) == null) {
            throw new BusinessException(ErrorCode.LEAVE_HR_DIRECT_NOT_ALLOWED,
                    "팀장(" + lead.getName() + ")님이 오늘 부재 중이 아니어서 인사관리자에게 바로 신청할 수 없습니다.");
        }
        return reason.trim();
    }

    private List<Long> adminIdsExcept(Employee employee) {
        return employeeService.activeAdminIds().stream()
                .filter(id -> !id.equals(employee.getId()))
                .toList();
    }

    private static String period(LeaveRequest request) {
        return request.getStartDate().equals(request.getEndDate())
                ? request.getStartDate().toString()
                : request.getStartDate() + " ~ " + request.getEndDate();
    }

    // --- helpers ---

    /** 정책 기반 사용 통제 검증 (블랙아웃/사전신청/연속일/팀 동시부재). */
    private void validateUsagePolicy(Employee employee, LeaveType type, LocalDate start, LocalDate end,
                                     BigDecimal days, LeavePolicy policy) {
        // 경조사·병가·공가(연차 비차감)는 갑자기 생기거나 회사가 막을 수 없는 휴가라 사용 통제를 적용하지 않는다.
        // 팀 동시 부재 한도 초과는 막지 않고 결재함에 경고로 보여 팀장이 재량으로 판단한다(teamLimitWarning).
        if (!type.isDeductFromAnnual()) {
            return;
        }
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
        if (exceedsTeamLimit(employee, start, end, policy)) {
            throw new BusinessException(ErrorCode.LEAVE_TEAM_LIMIT,
                    "같은 기간 팀 내 최대 " + policy.getMaxConcurrentAbsence() + "명까지 휴가가 가능합니다.");
        }
    }

    /** 같은 기간 같은 부서의 승인된 휴가자(본인 제외) 수. */
    private long teamOnLeave(Employee employee, LocalDate start, LocalDate end) {
        return requestRepository.findApprovedBetween(start, end).stream()
                .filter(r -> employee.getDepartmentId().equals(r.getEmployee().getDepartmentId()))
                .filter(r -> !r.getEmployee().getId().equals(employee.getId()))
                .map(r -> r.getEmployee().getId())
                .distinct()
                .count();
    }

    private boolean exceedsTeamLimit(Employee employee, LocalDate start, LocalDate end, LeavePolicy policy) {
        return policy.getMaxConcurrentAbsence() > 0 && employee.getDepartmentId() != null
                && teamOnLeave(employee, start, end) + 1 > policy.getMaxConcurrentAbsence();
    }

    /**
     * 결재자에게 보여 줄 경고. 사용 통제를 적용하지 않은 비차감 휴가(경조사·병가·공가)가 팀 동시 부재 한도를
     * 넘으면 알려 주고, 승인 여부는 결재자가 판단한다. 해당 없으면 null.
     */
    private String teamLimitWarning(LeaveRequest request, LeavePolicy policy) {
        if (!request.isAwaitingApproval() || request.getLeaveType().isDeductFromAnnual()
                || !exceedsTeamLimit(request.getEmployee(), request.getStartDate(), request.getEndDate(), policy)) {
            return null;
        }
        long others = teamOnLeave(request.getEmployee(), request.getStartDate(), request.getEndDate());
        return "같은 기간 팀 부재 " + (others + 1) + "명으로 한도(" + policy.getMaxConcurrentAbsence()
                + "명)를 넘습니다. 승인 여부를 판단해 주세요.";
    }


    private void validatePeriod(LocalDate start, LocalDate end, LeaveType type) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD, "종료일이 시작일보다 빠릅니다.");
        }
        if (type.isPartialDay() && !start.isEqual(end)) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD,
                    type.getName() + "는 하루만 신청할 수 있습니다.");
        }
    }

    /**
     * 경조사 규정 확인. 종류에 규정이 연결돼 있으면 하나를 골라야 하고,
     * 신청 근무일 수(주말·공휴일 제외)가 규정 일수를 넘을 수 없다. 횟수 제한은 없다.
     *
     * @return 고른 규정(규정이 없는 종류면 null)
     */
    private SpecialLeaveRule resolveSpecialRule(LeaveType type, Long specialRuleId, BigDecimal workdays) {
        List<SpecialLeaveRule> rules = leaveTypeService.specialRulesOf(type);
        if (rules.isEmpty()) {
            if (specialRuleId != null) {
                throw new BusinessException(ErrorCode.LEAVE_SPECIAL_RULE_INVALID,
                        type.getName() + "에는 선택할 경조사 규정이 없습니다.");
            }
            return null;
        }
        if (specialRuleId == null) {
            throw new BusinessException(ErrorCode.LEAVE_SPECIAL_RULE_INVALID,
                    type.getName() + "는 사유(규정)를 선택해야 신청할 수 있습니다.");
        }
        SpecialLeaveRule rule = rules.stream().filter(r -> r.getId().equals(specialRuleId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_SPECIAL_RULE_INVALID,
                        type.getName() + "에 해당하지 않는 규정입니다."));
        if (workdays.compareTo(rule.getDays()) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_SPECIAL_RULE_EXCEEDED,
                    rule.getName() + "은(는) 근무일 기준 최대 " + plain(rule.getDays()) + "일까지 신청할 수 있습니다. (신청 "
                            + plain(workdays) + "일)");
        }
        return rule;
    }

    /** 시간차는 1~3시간 필수, 그 외 종류는 시간 수를 쓰지 않는다. */
    private Integer hoursFor(LeaveType type, Integer hours) {
        if (type.getPortion() != DayPortion.HOURLY) {
            return null;
        }
        if (hours == null || hours < 1 || hours > DayPortion.MAX_HOURLY_HOURS) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_PERIOD,
                    "시간차는 1~" + DayPortion.MAX_HOURLY_HOURS + "시간으로 신청합니다.");
        }
        return hours;
    }

    /**
     * 겹침 검사. 종일 휴가는 대기·승인 중인 어떤 신청과도 겹칠 수 없다.
     * 부분 휴가(반차·반반차·시간차)는 같은 날 부분 휴가끼리 합계 1일까지 허용한다.
     */
    private void validateNoOverlap(Long employeeId, LocalDate start, LocalDate end, LeaveType type,
                                   BigDecimal days) {
        List<LeaveRequest> overlapping = requestRepository.findActiveOverlapping(employeeId, start, end);
        if (overlapping.isEmpty()) {
            return;
        }
        if (!type.isPartialDay() || overlapping.stream().anyMatch(r -> !r.getLeaveType().isPartialDay())) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_OVERLAP);
        }
        BigDecimal sameDay = overlapping.stream().map(LeaveRequest::getDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sameDay.add(days).compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_OVERLAP,
                    "같은 날 반차·반반차·시간차 합계는 1일을 넘을 수 없습니다. (이미 신청 " + plain(sameDay) + "일)");
        }
    }

    /**
     * 잔여 연차를 먼저 소진해야 하는 종류(병가·공가)의 조건:
     * 승인 기준 잔여 연차 1일 미만 + 결재 대기 중인 연차 차감 신청 없음. 신청과 승인 때 모두 확인한다.
     *
     * @param approving 승인 시점 확인이면 true(결재자에게 보이는 문구)
     * @return 승인 시 소멸될 남은 연차(0 이상 1 미만)
     */
    private BigDecimal requireAnnualExhausted(Long employeeId, int year, LeaveType type, boolean approving) {
        BigDecimal remaining = balanceService.getOrCreate(employeeId, year).remaining();
        if (remaining.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.LEAVE_ANNUAL_NOT_EXHAUSTED,
                    (approving ? "신청자의 " : "") + "잔여 연차가 " + plain(remaining) + "일 남아 있습니다. "
                            + type.getName() + "는 잔여 연차가 1일 미만일 때 "
                            + (approving ? "승인할" : "신청할") + " 수 있습니다.");
        }
        if (requestRepository.existsPendingDeducting(employeeId, year)) {
            throw new BusinessException(ErrorCode.LEAVE_PENDING_ANNUAL_EXISTS, approving
                    ? "신청자에게 결재 대기 중인 연차 신청이 있어 승인할 수 없습니다. 연차 신청을 먼저 처리해 주세요."
                    : "결재 대기 중인 연차 신청이 있습니다. 먼저 처리(승인·반려·취소)된 뒤 신청해 주세요.");
        }
        return remaining.max(BigDecimal.ZERO);
    }

    /** 남은 연차 소멸 안내: 앱 알림 + 메일(커밋 후 발송). */
    private void notifyForfeited(LeaveRequest request, BigDecimal forfeited) {
        Employee employee = request.getEmployee();
        String typeName = request.getLeaveType().getName();
        String period = request.getStartDate().equals(request.getEndDate())
                ? request.getStartDate().toString()
                : request.getStartDate() + " ~ " + request.getEndDate();
        notificationService.notify(employee.getId(), "LEAVE_FORFEITED", "잔여 연차 소멸 안내",
                typeName + "(" + period + ") 승인으로 남은 연차 " + plain(forfeited) + "일이 소멸되었습니다.",
                "/my-leaves");
        eventPublisher.publishEvent(new AccountMailEvents.LeaveForfeited(
                employee.getEmail(), employee.getName(), typeName, plain(forfeited), period));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
                .title(e.getName() + " - " + request.getLeaveType().getName()
                        + (request.getSpecialRuleName() != null ? "(" + request.getSpecialRuleName() + ")" : "")
                        + (request.getLeaveType().getPortion() == DayPortion.HOURLY
                                ? " " + WorkdayCalculator.hoursOf(request.getDays()) + "시간" : ""))
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

    /**
     * 새 신청 결재 알림. 팀장 단계면 1차 결재할 팀장, 인사 단계(정책 OFF·팀장 신청·팀장 없음·팀장 부재 직행)면
     * 재직 인사관리자(본인 제외).
     */
    private String notifyApprovers(LeaveRequest request, LeavePolicy policy) {
        Employee employee = request.getEmployee();
        List<Long> hrIds = employeeService.activeAdminIds();
        String warning = null;
        // 본인 결재가 가능한 인사관리자만 있는 경우는 수신자가 없어도 결재자가 없는 것이 아니다.
        if (hrIds.isEmpty()) {
            warning = NO_HR_APPROVER_WARNING;
            log.warn("휴가 신청 id={}, employeeId={}: {}", request.getId(), employee.getId(), warning);
            notificationService.notify(employee.getId(), "LEAVE_NO_HR_APPROVER", "휴가 결재 안내",
                    warning, "/my-leaves");
        }
        List<Long> recipients = stageOf(request, policy) == ApprovalStage.LEAD
                ? List.of(leadApproverOf(employee).getId())
                : hrIds.stream().filter(id -> !id.equals(employee.getId())).toList();
        String message = employee.getName() + " - " + request.getLeaveType().getName() + " " + period(request)
                + (request.getHrDirectReason() != null ? " (팀장 부재로 인사 직행: " + request.getHrDirectReason() + ")" : "");
        for (Long approverId : recipients) {
            notificationService.notify(approverId, "LEAVE_REQUESTED", "새 휴가 결재 요청", message, "/approvals");
        }
        return warning;
    }

    private boolean isSystemAdmin(Employee e) {
        return e.isSystemAccount() || e.hasRole(Role.SUPER_ADMIN);
    }

    /**
     * 인사 결재·대리 취소 권한. 시스템 관리자는 다른 역할이 섞여 있어도 제외한다.
     * 추후 다른 직원 휴가의 대리 등록을 구현할 때도 시스템 관리자는 제외해야 한다.
     */
    private boolean isHrApprover(Employee e) {
        return !isSystemAdmin(e) && e.hasRole(Role.HR_ADMIN);
    }

    private boolean isAdmin(Employee e) {
        return e.hasRole(Role.SUPER_ADMIN) || e.hasRole(Role.HR_ADMIN);
    }

    /** 팀장: TEAM_LEAD 역할이 있거나 부서장으로 지정된 직원. */
    private boolean isTeamLead(Employee e) {
        return e.hasRole(Role.TEAM_LEAD) || !departmentRepository.findByLeadId(e.getId()).isEmpty();
    }

    /** 대상자가 본인이 팀장인 부서(하위 포함)에 속함. 조회 권한 판단에도 쓴다. */
    private boolean isInChargeOf(Employee lead, Employee target) {
        Long targetDeptId = target.getDepartmentId();
        if (targetDeptId == null) {
            return false;
        }
        return subordinateDeptIds(lead).contains(targetDeptId);
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
