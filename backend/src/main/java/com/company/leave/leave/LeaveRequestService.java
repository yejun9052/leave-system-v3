package com.company.leave.leave;

import com.company.leave.audit.AuditService;
import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.common.search.SearchKeywords;
import com.company.leave.common.search.SearchKeywords.DepartmentNode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);
    private static final String NO_APPROVER_WARNING = "결재할 팀장이나 인사관리자가 없습니다. 관리자에게 문의하세요.";

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
    private final LeaveMessenger messenger;
    private final AuditService auditService;

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
                               ApplicationEventPublisher eventPublisher,
                               LeaveMessenger messenger,
                               AuditService auditService) {
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
        this.messenger = messenger;
        this.auditService = auditService;
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
        Plan plan = plan(employee, type, policy, start, end, req.hours(), req.specialRuleId(), false, false);

        if (plan.forfeit().signum() > 0 && !Boolean.TRUE.equals(req.forfeitAcknowledged())) {
            throw new BusinessException(ErrorCode.LEAVE_FORFEIT_NOT_ACKNOWLEDGED,
                    type.getName() + "가 승인되면 남은 연차 " + plain(plan.forfeit())
                            + "일이 소멸됩니다. 안내를 확인한 뒤 신청해 주세요.");
        }

        LeaveRequest request = new LeaveRequest(
                employee, type, start, end, plan.days(), plan.deduction(), plan.appliedYear(), req.reason());
        SpecialLeaveRule specialRule = plan.specialRule();
        if (specialRule != null) {
            request.attachSpecialRule(specialRule.getId(), specialRule.getName(), specialRule.getDays());
        }
        requestRepository.save(request);

        String warning = notifyApprovers(request);
        return LeaveRequestDtos.Response.from(request).withRequestWarning(warning);
    }

    /**
     * 인사관리자(·시스템 관리자) 강제 등록: 다른 직원의 휴가를 바로 승인 상태로 등록한다.
     * <ul>
     *   <li>지난 날짜도 가능. 시작일은 근무일이어야 한다(주말·공휴일 불가)</li>
     *   <li>사용 통제(블랙아웃·사전 신청·연속 일수·팀 동시 부재)는 적용하지 않는다</li>
     *   <li>겹침·잔액(마이너스 연차 정책)·경조사 규정·병가·공가 조건은 신청과 같다. 병가·공가는 남은 연차를 소멸시킨다</li>
     * </ul>
     * 신청자와 담당 팀장에게 알림·메일을 보낸다.
     */
    @Transactional
    public LeaveRequestDtos.Response register(Long registrarId, LeaveRequestDtos.Register req) {
        Employee registrar = employeeService.getEntity(registrarId);
        if (!isHrApprover(registrar)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "휴가 강제 등록은 인사관리자만 할 수 있습니다.");
        }
        Employee employee = employeeService.getEntity(req.employeeId());
        if (employee.isSystemAccount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관리 전용 계정에는 휴가를 등록할 수 없습니다.");
        }
        LeaveType type = leaveTypeService.getEntity(req.leaveTypeId());
        LeavePolicy policy = policyService.getActivePolicy();
        if (!type.isActive() || !policy.allows(type.getPortion())) {
            throw new BusinessException(ErrorCode.LEAVE_TYPE_DISABLED,
                    "현재 정책에서 사용할 수 없는 휴가 종류입니다: " + type.getName());
        }
        Plan plan = plan(employee, type, policy, req.startDate(), req.endDate(), req.hours(), req.specialRuleId(),
                false, true);

        LeaveRequest request = new LeaveRequest(employee, type, req.startDate(), req.endDate(), plan.days(),
                plan.deduction(), plan.appliedYear(), req.reason());
        SpecialLeaveRule specialRule = plan.specialRule();
        if (specialRule != null) {
            request.attachSpecialRule(specialRule.getId(), specialRule.getName(), specialRule.getDays());
        }
        requestRepository.save(request);

        if (type.isDeductFromAnnual()) {
            balanceService.getOrCreate(employee.getId(), plan.appliedYear()).addUsed(plan.deduction());
        }
        if (plan.forfeit().signum() > 0) {
            balanceService.getOrCreate(employee.getId(), plan.appliedYear()).forfeit(plan.forfeit());
            request.recordForfeit(plan.forfeit());
        }
        request.approve(registrar, Instant.now());
        createCalendarEvent(request);
        messenger.registered(request, registrar, informedLead(request, registrar));
        if (plan.forfeit().signum() > 0) {
            notifyForfeited(request, plan.forfeit());
        }
        return LeaveRequestDtos.Response.from(request);
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
     * @param forced  인사관리자 강제 등록이면 true: 사용 통제(블랙아웃·사전 신청·연속 일수·팀 동시 부재)를 건너뛴다
     */
    private Plan plan(Employee employee, LeaveType type, LeavePolicy policy, LocalDate start, LocalDate end,
                      Integer requestedHours, Long specialRuleId, boolean preview, boolean forced) {
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

        if (!forced) {
            validateUsagePolicy(employee, type, start, end, days, policy);
        }
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
     * 신청자의 결재 경로(신청 화면 안내용). 상위 결재 팀장이 있으면 그 팀장, 본인이 자가 승인할 수 있으면(최상위 부서 팀장·
     * 인사관리자) 본인, 그 외(팀장이 없는 부서)는 인사관리자. 어느 경우든 인사관리자도 결재할 수 있다.
     */
    @Transactional(readOnly = true)
    public LeaveRequestDtos.ApprovalRoute approvalRoute(Long employeeId) {
        Employee employee = employeeService.getEntity(employeeId);
        Employee lead = leadApproverOf(employee);
        if (lead != null) {
            return new LeaveRequestDtos.ApprovalRoute(LeaveRequestDtos.ApproverKind.LEAD, lead.getName());
        }
        return new LeaveRequestDtos.ApprovalRoute(canSelfApprove(employee)
                ? LeaveRequestDtos.ApproverKind.SELF : LeaveRequestDtos.ApproverKind.HR, null);
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
            plan = plan(employee, type, policy, start, end, hours, specialRuleId, true, false);
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
     * 결재함 "휴가 목록": 캘린더에서만 보이던 휴가를 목록으로 찾는다(휴가 시작일 최신순).
     * 인사관리자·시스템 관리자는 전 직원, 팀장은 맡은 부서(하위 포함) 소속 직원만. 맡은 부서가 없으면 빈 목록.
     * 검색 조건은 {@link LeaveRequestSearch}.
     */
    @Transactional(readOnly = true)
    public Page<LeaveRequestDtos.Response> search(Long callerId, String keyword, Set<LeaveRequestStatus> statuses,
                                                  LocalDate from, LocalDate to, int page, int size) {
        Employee caller = employeeService.getEntity(callerId);
        Set<Long> scope = isHrApprover(caller) ? null : subordinateDeptIds(caller);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id")));
        if (scope != null && scope.isEmpty()) {
            return Page.empty(pageable);
        }
        List<DepartmentNode> departments = SearchKeywords.tokens(keyword).isEmpty() ? List.of()
                : departmentRepository.findAll().stream()
                        .map(d -> new DepartmentNode(d.getId(),
                                d.getParent() != null ? d.getParent().getId() : null, d.getName()))
                        .toList();
        return requestRepository.findAll(
                        LeaveRequestSearch.of(keyword, statuses, from, to, scope, departments), pageable)
                .map(LeaveRequestDtos.Response::from);
    }

    /**
     * 결재자가 처리할 목록(대기·취소 요청).
     * <ul>
     *   <li>인사관리자·시스템 관리자: 전 직원(본인 포함)</li>
     *   <li>팀장: 맡은 부서(하위 포함) 직원의 신청. 본인 신청은 최상위 부서 팀장일 때만(자가 승인)</li>
     * </ul>
     * 둘 다인 사람은 합쳐서(중복 없이) 본다.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDtos.Response> pendingForApprover(Long approverId) {
        Employee approver = employeeService.getEntity(approverId);
        LeavePolicy policy = policyService.getActivePolicy();
        boolean hr = isHrApprover(approver);
        java.util.Collection<Long> targets = hr ? allEmployeeIds() : subordinateEmployeeIds(approver);
        if (!hr && targets.isEmpty()) {
            return List.of(); // 맡은 부서가 없는 팀장 역할자
        }
        return requestRepository.findForApproval(targets,
                        EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.CANCEL_REQUESTED)).stream()
                .filter(r -> canApprove(approver, r))
                .sorted(java.util.Comparator.comparing(LeaveRequest::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(r -> LeaveRequestDtos.Response.from(r)
                        .withInbox(r.getEmployee().getId().equals(approverId), teamLimitWarning(r, policy)))
                .toList();
    }

    /**
     * 캘린더 날짜 상세용: 그날에 걸친 휴가 목록(부서·이름 순).
     * <ul>
     *   <li>승인(취소 요청 중 포함): 캘린더처럼 모두에게 보인다</li>
     *   <li>결재 대기: 본인, 그 신청을 결재할 수 있는 사람(인사관리자·시스템 관리자·담당 팀장)에게만</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDtos.DayLeave> leavesOnDay(Long callerId, LocalDate date) {
        Employee caller = employeeService.getEntity(callerId);
        return requestRepository.findByStatusInOverlapping(EnumSet.of(LeaveRequestStatus.PENDING,
                        LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED), date, date).stream()
                .filter(r -> !r.isAwaitingApproval() || r.getEmployee().getId().equals(callerId)
                        || canApprove(caller, r))
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
        if (!isHrApprover(caller) && !isInChargeOf(caller, target)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 승인. 결재 권한({@link #canApprove})이 있는 한 명이 승인하면 바로 확정된다(잔액 차감·캘린더 등록·병가 소멸).
     * 자가 승인(최상위 부서 팀장·인사관리자 본인 신청)은 감사 로그에 따로 남긴다.
     */
    @Transactional
    public LeaveRequestDtos.Response approve(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isAwaitingApproval()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING, "이미 처리된 신청입니다.");
        }
        requireApprover(approver, request);

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

        messenger.approved(request, approver, informedLead(request, approver));
        if (forfeited.signum() > 0) {
            notifyForfeited(request, forfeited);
        }
        if (request.getEmployee().getId().equals(approverId)) {
            auditAfterCommit("self_approve", request, "자가 승인: " + summary(request));
        }
        return LeaveRequestDtos.Response.from(request);
    }

    /** 반려. 승인과 같은 결재 권한. */
    @Transactional
    public LeaveRequestDtos.Response reject(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isAwaitingApproval()) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING, "이미 처리된 신청입니다.");
        }
        requireApprover(approver, request);
        request.reject(approver, reason, Instant.now());
        messenger.rejected(request, approver, reason);
        return LeaveRequestDtos.Response.from(request);
    }

    /**
     * 취소 처리.
     * <ul>
     *   <li>대기(PENDING): 본인·인사관리자 → 즉시 취소. 본인이 철회하면 결재자에게, 인사관리자가 취소하면 신청자에게 안내</li>
     *   <li>승인(APPROVED), 본인: 시작일 전까지만 취소 요청(결재자 결재 대기)</li>
     *   <li>승인(APPROVED), 인사관리자: 시작 전후 상관없이 즉시 취소. 다른 직원의 휴가이거나 이미 시작된 휴가면
     *       강제 취소로 보고 사유가 필수이며, 신청자·담당 팀장에게 알리고 감사 로그를 남긴다</li>
     *   <li>취소 요청(CANCEL_REQUESTED): 인사관리자 → 즉시 확정</li>
     * </ul>
     * 팀장은 이 경로로 다른 직원의 휴가를 직접 취소할 수 없다(취소 요청 결재는 {@link #approveCancellation}).
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
            case PENDING -> {
                request.cancel();
                if (owner) {
                    messenger.withdrawn(request, approversToNotify(request.getEmployee()));
                } else {
                    messenger.cancelledByHr(request, requester, reason);
                }
            }
            case APPROVED -> {
                boolean started = !request.getStartDate().isAfter(LocalDate.now());
                if (admin && (!owner || started)) {
                    forceCancel(request, requester, reason);
                } else if (admin) {
                    finalizeCancel(request, requester);
                } else if (started) {
                    throw new BusinessException(ErrorCode.LEAVE_ALREADY_STARTED);
                } else {
                    request.requestCancel(reason);
                    messenger.cancelRequested(request, approversToNotify(request.getEmployee()));
                }
            }
            case CANCEL_REQUESTED -> {
                if (admin) {
                    finalizeCancel(request, requester);
                    if (!owner) {
                        messenger.cancelApproved(request, requester);
                    }
                } else {
                    throw new BusinessException(ErrorCode.CONFLICT, "이미 취소 요청 상태입니다.");
                }
            }
            default -> throw new BusinessException(ErrorCode.LEAVE_NOT_PENDING,
                    "취소할 수 없는 상태입니다.");
        }
        return LeaveRequestDtos.Response.from(request);
    }

    /**
     * 인사관리자 강제 취소: 승인된 휴가를 시작 전후 상관없이 취소한다. 사유 필수.
     * 잔액·소멸분 환원, 캘린더 삭제, 신청자·담당 팀장 알림·메일, 감사 로그.
     */
    private void forceCancel(LeaveRequest request, Employee hr, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "강제 취소 사유를 입력해 주세요.");
        }
        String trimmed = reason.trim();
        restoreAndClearCalendar(request);
        request.forceCancel(trimmed);
        messenger.forceCancelled(request, hr, trimmed, informedLead(request, hr));
        auditAfterCommit("force_cancel", request, "강제 취소: " + summary(request) + " / 사유: " + trimmed);
    }

    /** 취소 요청 승인 → 확정 취소(잔액 환원, 캘린더 삭제, 팀장 안내). 승인과 같은 결재 권한. */
    @Transactional
    public LeaveRequestDtos.Response approveCancellation(Long requestId, Long approverId) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        requireApprover(approver, request);
        finalizeCancel(request, approver);
        messenger.cancelApproved(request, approver);
        return LeaveRequestDtos.Response.from(request);
    }

    /** 취소 요청 반려 → 승인 상태로 복귀. 승인과 같은 결재 권한. */
    @Transactional
    public LeaveRequestDtos.Response rejectCancellation(Long requestId, Long approverId, String reason) {
        LeaveRequest request = getRequest(requestId);
        Employee approver = employeeService.getEntity(approverId);
        if (!request.isCancelRequested()) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소 요청 상태가 아닙니다.");
        }
        requireApprover(approver, request);
        request.rejectCancel();
        messenger.cancelRejected(request, approver, reason);
        return LeaveRequestDtos.Response.from(request);
    }

    /** 확정 취소 공통 처리: 잔액 환원 + 캘린더 일정 삭제 + 상태 CANCELLED + 팀장 안내(처리자 actor 포함). */
    private void finalizeCancel(LeaveRequest request, Employee actor) {
        restoreAndClearCalendar(request);
        request.cancel();
        // 승인됐던 휴가가 취소되면 담당 팀장에게 안내(취소를 처리한 사람이 그 팀장이면 생략)
        Employee lead = informedLead(request, actor);
        if (lead != null) {
            messenger.leadCancelledInfo(request, lead, actor);
        }
    }

    /** 승인 때 반영한 것 되돌리기: 연차 차감 환원, 병가·공가 소멸분 환원, 캘린더 일정 삭제. */
    private void restoreAndClearCalendar(LeaveRequest request) {
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
    }

    // --- 결재 권한·결재자 ---

    /**
     * 결재 권한(승인·반려·취소 요청 승인·반려 공통). 한 번의 승인으로 확정된다.
     * <ul>
     *   <li>인사관리자·시스템 관리자: 모든 신청(본인 신청 포함)</li>
     *   <li>팀장: 맡은 부서(하위 포함) 직원의 신청. 본인 신청은 안 됨</li>
     *   <li>최상위 부서 팀장(위에 결재할 팀장이 없음): 본인 신청도 자가 승인</li>
     * </ul>
     */
    private boolean canApprove(Employee approver, LeaveRequest request) {
        if (isHrApprover(approver)) {
            return true;
        }
        Employee applicant = request.getEmployee();
        if (approver.getId().equals(applicant.getId())) {
            return canSelfApprove(applicant);
        }
        return isInChargeOf(approver, applicant);
    }

    private void requireApprover(Employee approver, LeaveRequest request) {
        if (!canApprove(approver, request)) {
            throw new BusinessException(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION);
        }
    }

    /** 자가 승인 가능: 인사관리자·시스템 관리자, 또는 위에 결재할 팀장이 없는 부서장(최상위 부서 팀장). */
    private boolean canSelfApprove(Employee e) {
        return isHrApprover(e)
                || (!departmentRepository.findByLeadId(e.getId()).isEmpty() && leadApproverOf(e) == null);
    }

    /**
     * 신청자를 결재할 팀장: 소속 부서부터 상위로 올라가며 처음 만나는 재직 중인 부서장(본인·관리 전용 계정 제외).
     * 팀장 본인의 신청이면 자기 부서는 건너뛰어 상위 부서 팀장이 된다. 끝까지 없으면 null.
     */
    private Employee leadApproverOf(Employee applicant) {
        for (Department d = applicant.getDepartment(); d != null; d = d.getParent()) {
            Employee lead = d.getLead();
            if (lead != null && !lead.isSystemAccount() && !lead.getId().equals(applicant.getId()) && lead.isActive()) {
                return lead;
            }
        }
        return null;
    }

    /**
     * 신청·철회·취소 요청 알림을 받을 결재자: 결재 팀장이 있으면 그 팀장, 없으면(팀장 없는 부서) 인사관리자 전원.
     * 자가 승인할 수 있는 사람(최상위 부서 팀장·인사관리자)의 본인 신청은 아무에게도 보내지 않는다.
     */
    private List<Employee> approversToNotify(Employee applicant) {
        Employee lead = leadApproverOf(applicant);
        if (lead != null) {
            return List.of(lead);
        }
        if (canSelfApprove(applicant)) {
            return List.of();
        }
        return adminIdsExcept(applicant).stream().map(employeeService::getEntity).toList();
    }

    /** 승인·등록·강제 취소 결과를 따로 안내받을 담당 팀장(결재·처리한 사람 본인이면 생략). */
    private Employee informedLead(LeaveRequest request, Employee actor) {
        Employee lead = leadApproverOf(request.getEmployee());
        return lead != null && !lead.getId().equals(actor.getId()) ? lead : null;
    }

    /** 결재 트랜잭션이 커밋된 뒤에만 감사 로그를 남긴다(동시 승인 충돌 등으로 롤백되면 남기지 않음). */
    private void auditAfterCommit(String action, LeaveRequest request, String detail) {
        String id = String.valueOf(request.getId());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditService.record(action, "leave-requests", id, detail, true);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditService.record(action, "leave-requests", id, detail, true);
            }
        });
    }

    private static String summary(LeaveRequest request) {
        String period = request.getStartDate().equals(request.getEndDate())
                ? request.getStartDate().toString()
                : request.getStartDate() + " ~ " + request.getEndDate();
        return request.getEmployee().getName() + " " + request.getLeaveType().getName() + " " + period;
    }

    private List<Long> adminIdsExcept(Employee employee) {
        return employeeService.activeAdminIds().stream()
                .filter(id -> !id.equals(employee.getId()))
                .toList();
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
     * 새 신청 알림·메일: 신청자에게 접수 안내, 결재자({@link #approversToNotify})에게 결재 요청.
     * 결재할 사람이 아무도 없으면(팀장 없는 부서인데 인사관리자도 없음) 신청자에게 안내한다.
     */
    private String notifyApprovers(LeaveRequest request) {
        Employee employee = request.getEmployee();
        List<Employee> approvers = approversToNotify(employee);
        String warning = null;
        if (approvers.isEmpty() && !canSelfApprove(employee)) {
            warning = NO_APPROVER_WARNING;
            log.warn("휴가 신청 id={}, employeeId={}: {}", request.getId(), employee.getId(), warning);
            notificationService.notify(employee.getId(), "LEAVE_NO_HR_APPROVER", "휴가 결재 안내",
                    warning, "/my-leaves");
        }
        messenger.submitted(request, approvers, routeText(employee));
        return warning;
    }

    /** 신청자에게 보여 줄 결재 경로 문구({@link #approvalRoute}와 같은 판단). */
    private String routeText(Employee applicant) {
        Employee lead = leadApproverOf(applicant);
        if (lead != null) {
            return "팀장 " + lead.getName() + "님 승인(인사관리자도 승인 가능)";
        }
        return canSelfApprove(applicant) ? "본인 승인(자가 승인 가능)" : "인사관리자 승인";
    }

    /** 전사 결재·강제 취소·강제 등록 권한: 인사관리자와 시스템 관리자(같은 권한, 회사 합의). */
    private boolean isHrApprover(Employee e) {
        return e.hasRole(Role.HR_ADMIN) || e.hasRole(Role.SYSTEM_ADMIN);
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
