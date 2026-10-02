package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
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
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.dto.LeaveRequestDtos.ApproverKind;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.LeaveMail;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휴가 결재(단일 결재): 권한 있는 한 명이 승인하면 확정.
 * <ul>
 *   <li>팀장: 맡은 부서(하위 포함) 직원의 신청. 본인 신청은 상위 부서 팀장에게 가고, 최상위 부서 팀장만 자가 승인</li>
 *   <li>인사관리자·시스템 관리자: 모든 신청(본인 포함), 강제 취소·강제 등록</li>
 * </ul>
 * 조직: 본사(부서장 없음) ⊃ 제품개발팀(개발팀장, 최상위 팀장) ⊃ 플랫폼파트(파트장).
 * 본사 ⊃ 경영지원팀(인사관리자), 영업팀(영업팀장), 총무팀(부서장 없음).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 결재")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceApprovalTest {

    private static final LocalDate TUE = LocalDate.of(2027, 5, 4);
    private static final LocalDate PAST_TUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate PAST_SAT = LocalDate.of(2026, 9, 5);

    @Mock private LeaveRequestRepository requestRepository;
    @Mock private LeaveTypeService leaveTypeService;
    @Mock private EmployeeService employeeService;
    @Mock private LeaveBalanceService balanceService;
    @Mock private HolidayRepository holidayRepository;
    @Mock private PolicyService policyService;
    @Mock private LeaveAccrualCalculator accrualCalculator;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private NotificationService notificationService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private BlackoutPeriodRepository blackoutPeriodRepository;
    @Mock private LeavePolicy policy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;

    private LeaveRequestService service;

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final Map<Long, Employee> employees = new HashMap<>();
    private final Map<Long, LeaveRequest> requests = new HashMap<>();
    private final LeaveBalance balance = new LeaveBalance(0L, 2027);

    private Department 본사;
    private Department 제품개발팀;
    private Department 플랫폼파트;
    private Department 총무팀;

    private Employee 시스템관리자;
    private Employee 인사관리자;
    private Employee 개발팀장;
    private Employee 파트장;
    private Employee 부파트장;
    private Employee 개발팀원;
    private Employee 파트원;
    private Employee 영업팀장;
    private Employee 총무팀원;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService,
                new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator()),
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher,
                        new AccountMailProperties("noreply@company.com", "http://localhost:5173")),
                auditService);

        본사 = 부서(1L, "본사", null);
        제품개발팀 = 부서(2L, "제품개발팀", 본사);
        플랫폼파트 = 부서(3L, "플랫폼파트", 제품개발팀);
        Department 경영지원팀 = 부서(5L, "경영지원팀", 본사);
        Department 영업팀 = 부서(9L, "영업팀", 본사);
        총무팀 = 부서(10L, "총무팀", 본사);

        시스템관리자 = 직원(1L, 본사, Role.SYSTEM_ADMIN);
        ReflectionTestUtils.setField(시스템관리자, "systemAccount", true);
        인사관리자 = 직원(11L, 경영지원팀, Role.EMPLOYEE, Role.TEAM_LEAD, Role.HR_ADMIN);
        개발팀장 = 직원(2L, 제품개발팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        파트장 = 직원(7L, 플랫폼파트, Role.EMPLOYEE, Role.TEAM_LEAD);
        부파트장 = 직원(20L, 플랫폼파트, Role.EMPLOYEE, Role.TEAM_LEAD); // 팀장 역할만 있고 부서장은 아님
        개발팀원 = 직원(6L, 제품개발팀, Role.EMPLOYEE);
        파트원 = 직원(13L, 플랫폼파트, Role.EMPLOYEE);
        영업팀장 = 직원(8L, 영업팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        총무팀원 = 직원(14L, 총무팀, Role.EMPLOYEE);

        제품개발팀.assignLead(개발팀장);
        플랫폼파트.assignLead(파트장);
        경영지원팀.assignLead(인사관리자);
        영업팀.assignLead(영업팀장);
        lenient().when(departmentRepository.findByLeadId(anyLong())).thenReturn(List.of());
        lenient().when(departmentRepository.findByLeadId(2L)).thenReturn(List.of(제품개발팀));
        lenient().when(departmentRepository.findByLeadId(7L)).thenReturn(List.of(플랫폼파트));
        lenient().when(departmentRepository.findByLeadId(11L)).thenReturn(List.of(경영지원팀));
        lenient().when(departmentRepository.findByLeadId(8L)).thenReturn(List.of(영업팀));
        lenient().when(departmentRepository.findSubtreeIds(2L)).thenReturn(List.of(2L, 3L));
        lenient().when(departmentRepository.findSubtreeIds(3L)).thenReturn(List.of(3L));
        lenient().when(departmentRepository.findSubtreeIds(5L)).thenReturn(List.of(5L));
        lenient().when(departmentRepository.findSubtreeIds(9L)).thenReturn(List.of(9L));

        lenient().when(employeeService.getEntity(anyLong())).thenAnswer(inv -> employees.get(inv.<Long>getArgument(0)));
        lenient().when(employeeService.activeAdminIds()).thenReturn(List.of(1L, 11L));
        lenient().when(requestRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<Long>getArgument(0))));
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(policy.isAllowNegative()).thenReturn(true);
        lenient().when(policy.isNextPeriodReservationEnabled()).thenReturn(true);
        lenient().when(policy.allows(any())).thenReturn(true);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt())).thenReturn(balance);
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(leaveTypeService.getEntity(1L)).thenReturn(연차);
        lenient().when(requestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            long id = 1000L + requests.size();
            ReflectionTestUtils.setField(r, "id", id);
            requests.put(id, r);
            return r;
        });
    }

    // --- 결재 권한 ---

    @Test
    void 팀원_신청은_담당_팀장이_승인하면_바로_확정된다() {
        LeaveRequest request = 대기_신청(파트원);

        service.approve(request.getId(), 파트장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(request.getApprover()).isSameAs(파트장);
        assertThat(balance.getUsed()).isEqualByComparingTo("1");
        verify(calendarEventRepository).save(any());
    }

    @Test
    void 다른_팀_팀장은_승인할_수_없다() {
        LeaveRequest request = 대기_신청(파트원);

        승인_권한_없음(() -> service.approve(request.getId(), 영업팀장.getId()));
        승인_권한_없음(() -> service.reject(request.getId(), 영업팀장.getId(), "사유"));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
    }

    @Test
    void 인사관리자가_승인해도_바로_확정된다() {
        LeaveRequest request = 대기_신청(파트원);

        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(request.getApprover()).isSameAs(인사관리자);
    }

    @Test
    void 상위_부서_팀장도_하위_부서_팀원의_신청을_승인한다() {
        LeaveRequest request = 대기_신청(파트원);

        service.approve(request.getId(), 개발팀장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 팀장_역할만_있는_팀원도_그_부서_팀장이_승인한다() {
        LeaveRequest request = 대기_신청(부파트장);

        service.approve(request.getId(), 파트장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 팀장_신청은_본인이_승인할_수_없고_상위_부서_팀장이나_인사관리자가_승인한다() {
        LeaveRequest 상위팀장_승인건 = 대기_신청(파트장);
        LeaveRequest 인사_승인건 = 대기_신청(파트장);

        승인_권한_없음(() -> service.approve(상위팀장_승인건.getId(), 파트장.getId()));
        service.approve(상위팀장_승인건.getId(), 개발팀장.getId());
        service.approve(인사_승인건.getId(), 인사관리자.getId());

        assertThat(상위팀장_승인건.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(인사_승인건.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 팀장이_신청하면_상위_부서_팀장에게_결재_요청이_간다() {
        service.create(파트장.getId(), 신청서());

        verify(notificationService).notify(eq(2L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(7L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void 바로_위_부서에_부서장이_없으면_그_위_부서장이_결재한다() {
        ReflectionTestUtils.setField(플랫폼파트, "lead", null);

        assertThat(service.approvalRoute(파트원.getId()))
                .isEqualTo(new LeaveRequestDtos.ApprovalRoute(ApproverKind.LEAD, 개발팀장.getName()));
        service.create(파트원.getId(), 신청서());
        verify(notificationService).notify(eq(2L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void 상위_부서장을_겸하는_팀장의_신청은_그_위_부서장에게_간다() {
        Employee 대표 = 직원(30L, 본사, Role.EMPLOYEE, Role.TEAM_LEAD);
        본사.assignLead(대표);
        제품개발팀.assignLead(파트장); // 파트장이 플랫폼파트·제품개발팀 부서장 겸임

        assertThat(service.approvalRoute(파트장.getId()))
                .isEqualTo(new LeaveRequestDtos.ApprovalRoute(ApproverKind.LEAD, 대표.getName()));
    }

    @Test
    void 최상위_부서_팀장은_본인_신청을_자가_승인하고_감사_로그에_남는다() {
        LeaveRequest request = 대기_신청(개발팀장);

        service.approve(request.getId(), 개발팀장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(request.getApprover()).isSameAs(개발팀장);
        verify(auditService).record(eq("self_approve"), eq("leave-requests"), eq(String.valueOf(request.getId())),
                contains("자가 승인"), eq(true));
    }

    @Test
    void 최상위_부서_팀장의_결재함에는_본인_신청도_보인다() {
        LeaveRequest 본인 = 대기_신청(개발팀장);
        LeaveRequest 팀원 = 대기_신청(개발팀원);
        when(employeeService.employeeIdsInDepartments(Set.of(2L, 3L))).thenReturn(Set.of(2L, 6L, 7L, 13L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(본인, 팀원));

        List<LeaveRequestDtos.Response> inbox = service.pendingForApprover(개발팀장.getId());

        assertThat(inbox).extracting(LeaveRequestDtos.Response::id).containsExactly(본인.getId(), 팀원.getId());
        assertThat(inbox).extracting(LeaveRequestDtos.Response::ownRequest).containsExactly(true, false);
        assertThat(service.approvalRoute(개발팀장.getId()).approverKind()).isEqualTo(ApproverKind.SELF);
    }

    @Test
    void 하위_팀장의_결재함에는_본인_신청이_보이지_않는다() {
        LeaveRequest 본인 = 대기_신청(파트장);
        LeaveRequest 팀원 = 대기_신청(파트원);
        when(employeeService.employeeIdsInDepartments(Set.of(3L))).thenReturn(Set.of(7L, 13L, 20L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(본인, 팀원));

        assertThat(service.pendingForApprover(파트장.getId())).extracting(LeaveRequestDtos.Response::id)
                .containsExactly(팀원.getId());
    }

    @Test
    void 인사관리자는_본인_신청을_승인할_수_있다() {
        LeaveRequest request = 대기_신청(인사관리자);

        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        verify(auditService).record(eq("self_approve"), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void 인사관리자_결재함에는_전_직원의_대기와_취소_요청이_보인다() {
        LeaveRequest 대기 = 대기_신청(파트원);
        LeaveRequest 취소요청 = 대기_신청(개발팀장);
        취소요청.approve(인사관리자, Instant.now());
        취소요청.requestCancel("변경");
        when(employeeService.allEmployeeIds()).thenReturn(List.of(2L, 13L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(대기, 취소요청));

        assertThat(service.pendingForApprover(인사관리자.getId())).extracting(LeaveRequestDtos.Response::id)
                .containsExactlyInAnyOrder(대기.getId(), 취소요청.getId());
    }

    @Test
    void 팀장이_없는_부서_직원의_신청은_인사관리자에게_가고_인사관리자만_승인한다() {
        assertThat(service.approvalRoute(총무팀원.getId()).approverKind()).isEqualTo(ApproverKind.HR);
        service.create(총무팀원.getId(), 신청서());
        verify(notificationService).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());

        LeaveRequest request = 대기_신청(총무팀원);
        승인_권한_없음(() -> service.approve(request.getId(), 개발팀장.getId()));
        service.approve(request.getId(), 인사관리자.getId());
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 시스템_관리자는_인사관리자와_같이_모든_신청을_결재한다() {
        LeaveRequest 팀원건 = 대기_신청(파트원);
        LeaveRequest 팀장건 = 대기_신청(파트장);
        when(employeeService.allEmployeeIds()).thenReturn(List.of(7L, 13L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(팀원건, 팀장건));

        assertThat(service.pendingForApprover(시스템관리자.getId())).hasSize(2);
        service.approve(팀원건.getId(), 시스템관리자.getId());
        service.reject(팀장건.getId(), 시스템관리자.getId(), "사유");

        assertThat(팀원건.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(팀장건.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
    }

    @Test
    void 이미_처리된_신청은_다시_승인할_수_없다() {
        LeaveRequest request = 대기_신청(파트원);
        service.approve(request.getId(), 파트장.getId());

        assertThatThrownBy(() -> service.approve(request.getId(), 인사관리자.getId()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_NOT_PENDING);
                    assertThat(ex.getMessage()).contains("이미 처리된 신청");
                });
    }

    @Test
    void 팀장은_팀원의_취소_요청을_승인하거나_반려한다() {
        LeaveRequest 승인건 = 승인된_휴가(파트원, LocalDate.now().plusDays(30));
        승인건.requestCancel("일정 변경");
        LeaveRequest 반려건 = 승인된_휴가(파트원, LocalDate.now().plusDays(40));
        반려건.requestCancel("일정 변경");

        service.approveCancellation(승인건.getId(), 파트장.getId());
        service.rejectCancellation(반려건.getId(), 파트장.getId(), "인력 부족");

        assertThat(승인건.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(반려건.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        승인_권한_없음(() -> service.approveCancellation(대기_취소요청(파트원).getId(), 영업팀장.getId()));
    }

    @Test
    void 승인된_휴가의_취소_요청은_결재_팀장에게_가고_확정되면_팀장에게_안내가_간다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().plusDays(30));

        service.cancel(request.getId(), 파트원.getId(), "일정 변경");
        verify(notificationService).notify(eq(7L), eq("LEAVE_CANCEL_REQUESTED"), anyString(), anyString(), anyString());

        service.approveCancellation(request.getId(), 인사관리자.getId());
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        verify(notificationService).notify(eq(7L), eq("LEAVE_CANCELLED_INFO"), anyString(), anyString(), anyString());
    }

    @Test
    void 담당_팀장이_직접_취소_요청을_승인하면_자기에게_팀원_취소_안내를_보내지_않는다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().plusDays(30));
        request.requestCancel("일정 변경");

        service.approveCancellation(request.getId(), 파트장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        verify(notificationService).notify(eq(13L), eq("LEAVE_CANCEL_APPROVED"), anyString(), anyString(), anyString());
        verify(notificationService, never())
                .notify(eq(7L), eq("LEAVE_CANCELLED_INFO"), anyString(), anyString(), anyString());
    }

    @Test
    void 결재_대기_중_본인이_취소하면_결재_팀장에게_알린다() {
        LeaveRequest request = 대기_신청(파트원);

        service.cancel(request.getId(), 파트원.getId(), null);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        verify(notificationService).notify(eq(7L), eq("LEAVE_WITHDRAWN"), anyString(), anyString(), anyString());
    }

    // --- 강제 취소 ---

    @Test
    void 인사관리자는_이미_시작한_승인_휴가도_사유와_함께_강제_취소하고_잔액과_소멸분을_되돌린다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().minusDays(3));
        balance.addUsed(BigDecimal.ONE);
        balance.forfeit(new BigDecimal("0.5"));
        request.recordForfeit(new BigDecimal("0.5"));

        service.cancel(request.getId(), 인사관리자.getId(), "근태 정정");

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).isEqualTo("근태 정정");
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
        assertThat(balance.getExpired()).isEqualByComparingTo("0");
        verify(calendarEventRepository).deleteByLeaveRequestId(request.getId());
        verify(notificationService).notify(eq(13L), eq("LEAVE_FORCE_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(7L), eq("LEAVE_CANCELLED_INFO"), anyString(), anyString(), anyString());
        verify(auditService).record(eq("force_cancel"), eq("leave-requests"), anyString(), contains("근태 정정"), eq(true));
    }

    @Test
    void 강제_취소는_사유가_없으면_거부한다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().minusDays(10));

        for (String reason : new String[] {null, " "}) {
            assertThatThrownBy(() -> service.cancel(request.getId(), 인사관리자.getId(), reason))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 인사관리자_본인의_시작된_휴가_취소도_강제_취소로_사유가_필요하다() {
        LeaveRequest request = 승인된_휴가(인사관리자, LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.cancel(request.getId(), 인사관리자.getId(), null))
                .isInstanceOf(BusinessException.class);
        service.cancel(request.getId(), 인사관리자.getId(), "출근함");
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
    }

    @Test
    void 일반_직원은_시작된_휴가를_취소할_수_없다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now());

        assertThatThrownBy(() -> service.cancel(request.getId(), 파트원.getId(), "변경"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_ALREADY_STARTED));
    }

    @Test
    void 팀장은_다른_직원의_승인_휴가를_직접_취소할_수_없다() {
        LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().plusDays(30));

        assertThatThrownBy(() -> service.cancel(request.getId(), 파트장.getId(), "변경"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    // --- 강제 등록 ---

    @Test
    void 인사관리자는_지난_날짜의_휴가를_바로_승인_상태로_등록한다() {
        LeaveRequestDtos.Response response = service.register(인사관리자.getId(), 등록서(파트원, PAST_TUE));

        LeaveRequest request = requests.get(response.id());
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(request.getApprover()).isSameAs(인사관리자);
        assertThat(balance.getUsed()).isEqualByComparingTo("1");
        verify(calendarEventRepository).save(any());
        verify(notificationService).notify(eq(13L), eq("LEAVE_REGISTERED"), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(7L), eq("LEAVE_APPROVED_INFO"), anyString(), anyString(), anyString());
    }

    @Test
    void 강제_등록은_블랙아웃과_사전_신청_기한을_적용하지_않는다() {
        lenient().when(blackoutPeriodRepository.existsOverlap(any(), any())).thenReturn(true);
        lenient().when(policy.getMinAdvanceDays()).thenReturn(14);

        service.register(인사관리자.getId(), 등록서(파트원, PAST_TUE));

        assertThatThrownBy(() -> service.create(파트원.getId(), 신청서()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_BLACKOUT));
    }

    @Test
    void 강제_등록도_주말이나_공휴일에는_할_수_없다() {
        assertThatThrownBy(() -> service.register(인사관리자.getId(), 등록서(파트원, PAST_SAT)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INVALID_PERIOD));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void 팀장은_강제_등록을_할_수_없고_시스템_관리자는_할_수_있다() {
        assertThatThrownBy(() -> service.register(파트장.getId(), 등록서(파트원, PAST_TUE)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        LeaveRequestDtos.Response response = service.register(시스템관리자.getId(), 등록서(파트원, PAST_TUE));
        assertThat(response.status()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    // --- 관리 전용 계정·경고·조회 ---

    @Test
    void 관리_전용_계정은_휴가를_신청할_수_없고_등록_대상도_될_수_없다() {
        assertThatThrownBy(() -> service.create(시스템관리자.getId(), 신청서()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("관리 전용 계정");
                });
        assertThatThrownBy(() -> service.register(인사관리자.getId(), 등록서(시스템관리자, PAST_TUE)))
                .isInstanceOf(BusinessException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void 결재할_사람이_아무도_없으면_신청은_저장되고_경고한다() {
        when(employeeService.activeAdminIds()).thenReturn(List.of());

        LeaveRequestDtos.Response response = service.create(총무팀원.getId(), 신청서());

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(response.requestWarning()).contains("결재할 팀장이나 인사관리자가 없습니다");
        verify(notificationService).notify(eq(14L), eq("LEAVE_NO_HR_APPROVER"), anyString(), anyString(), anyString());
    }

    @Test
    void 관리_전용_계정이_부서장이어도_결재_팀장으로_보지_않는다() {
        플랫폼파트.assignLead(시스템관리자);

        assertThat(service.approvalRoute(파트원.getId()))
                .isEqualTo(new LeaveRequestDtos.ApprovalRoute(ApproverKind.LEAD, 개발팀장.getName()));
    }

    @Test
    void 인사관리자_시스템_관리자_상위_팀장은_다른_직원의_연차_정보를_조회할_수_있다() {
        assertThatCode(() -> service.assertCanViewEmployeeData(시스템관리자.getId(), 개발팀장.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewEmployeeData(개발팀장.getId(), 파트장.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertCanViewEmployeeData(영업팀장.getId(), 파트원.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // --- 휴가 목록 ---

    @Test
    void 휴가_목록은_휴가_시작일_최신순으로_찾고_검색어가_없으면_부서를_읽지_않는다() {
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search(인사관리자.getId(), null, Set.of(LeaveRequestStatus.APPROVED), null, null, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(requestRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("startDate"), Sort.Order.desc("id"));
        verify(departmentRepository, never()).findAll();
    }

    @Test
    void 맡은_부서가_없는_팀장은_휴가_목록이_비어_있다() {
        Page<LeaveRequestDtos.Response> page =
                service.search(부파트장.getId(), "연구소", Set.of(), null, null, 0, 20);

        assertThat(page.getContent()).isEmpty();
        verify(requestRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // --- 결재 메일 ---

    @Nested
    @DisplayName("결재 메일")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 결재_메일 {

        private static final String 파트원_메일 = "e13@company.com";
        private static final String 파트장_메일 = "e7@company.com";

        @Test
        void 신청하면_신청자에게_접수_메일_결재_팀장에게_결재_요청_메일이_간다() {
            service.create(파트원.getId(), 신청서());

            List<LeaveMail> mails = 보낸_메일();
            assertThat(mails).extracting(LeaveMail::to)
                    .containsExactlyInAnyOrder(List.of(파트원_메일), List.of(파트장_메일));
            assertThat(받은(mails, 파트원_메일).subject()).startsWith("[연차관리] 내 휴가 - 연차");
            assertThat(받은(mails, 파트원_메일).text()).contains("팀장 직원7님 승인(인사관리자도 승인 가능)");
            assertThat(받은(mails, 파트장_메일).subject()).startsWith("[연차관리] 휴가 결재 요청 - 직원13");
        }

        @Test
        void 메일은_받는_사람마다_따로_가고_표의_수신자에_자기_이름이_들어간다() {
            service.create(파트원.getId(), 신청서());

            List<LeaveMail> mails = 보낸_메일();
            assertThat(받은(mails, 파트원_메일).text()).contains("수신자: 직원13\n");
            assertThat(받은(mails, 파트장_메일).text()).contains("수신자: 직원7\n");
            assertThat(받은(mails, 파트장_메일).html()).contains(">휴가 결재 요청</h1>").contains(">직원7</td>");
        }

        @Test
        void 담당_팀장이_승인하면_신청자에게만_답장_메일이_간다() {
            LeaveRequest request = 대기_신청(파트원);

            service.approve(request.getId(), 파트장.getId());

            List<LeaveMail> mails = 보낸_메일();
            assertThat(mails).extracting(LeaveMail::to).containsExactly(List.of(파트원_메일));
            assertThat(mails.get(0).subject()).startsWith("RE: [연차관리] 내 휴가");
            assertThat(mails.get(0).inReplyTo()).isEqualTo("<leave-" + request.getId() + "-0.applicant@company.com>");
            assertThat(mails.get(0).text()).contains("직원7님이 휴가를 승인했습니다");
        }

        @Test
        void 인사관리자가_승인하면_담당_팀장에게도_안내_메일이_간다() {
            LeaveRequest request = 대기_신청(파트원);

            service.approve(request.getId(), 인사관리자.getId());

            List<LeaveMail> mails = 보낸_메일();
            assertThat(mails).extracting(LeaveMail::to)
                    .containsExactlyInAnyOrder(List.of(파트원_메일), List.of(파트장_메일));
            LeaveMail 팀장 = 받은(mails, 파트장_메일);
            assertThat(팀장.subject()).startsWith("[연차관리] 팀원 휴가 - 직원13");
            assertThat(팀장.text()).contains("직원11님이 직원13님의 휴가를 승인했습니다");
        }

        @Test
        void 자가_승인_메일은_자가_승인으로_안내한다() {
            LeaveRequest request = 대기_신청(개발팀장);

            service.approve(request.getId(), 개발팀장.getId());

            assertThat(보낸_메일()).singleElement()
                    .satisfies(m -> assertThat(m.text()).contains("자가 승인으로 휴가가 확정되었습니다"));
        }

        @Test
        void 반려하면_신청자에게_반려_사유와_함께_메일이_간다() {
            LeaveRequest request = 대기_신청(파트원);

            service.reject(request.getId(), 파트장.getId(), "프로젝트 마감");

            assertThat(보낸_메일()).singleElement().satisfies(m -> {
                assertThat(m.to()).containsExactly(파트원_메일);
                assertThat(m.text()).contains("직원7님이 휴가 신청을 반려했습니다").contains("반려 사유: 프로젝트 마감");
            });
        }

        @Test
        void 강제_취소하면_신청자와_담당_팀장에게_사유와_함께_메일이_간다() {
            LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().minusDays(2));

            service.cancel(request.getId(), 인사관리자.getId(), "근태 정정");

            List<LeaveMail> mails = 보낸_메일();
            assertThat(받은(mails, 파트원_메일).text()).contains("승인된 휴가를 취소했습니다").contains("취소 사유: 근태 정정");
            assertThat(받은(mails, 파트장_메일).subject()).startsWith("RE: [연차관리] 팀원 휴가");
        }

        @Test
        void 강제_등록하면_신청자와_담당_팀장에게_등록_메일이_간다() {
            service.register(인사관리자.getId(), 등록서(파트원, PAST_TUE));

            List<LeaveMail> mails = 보낸_메일();
            assertThat(받은(mails, 파트원_메일).text()).contains("직원11님이 휴가를 등록했습니다");
            assertThat(받은(mails, 파트원_메일).messageId()).endsWith(".applicant@company.com>");
            assertThat(받은(mails, 파트장_메일).text()).contains("직원13님의 휴가를 등록했습니다");
        }

        @Test
        void 시스템_관리자가_승인하면_시스템_관리자로_안내하고_관리_전용_계정에는_메일을_보내지_않는다() {
            LeaveRequest request = 대기_신청(총무팀원);

            service.approve(request.getId(), 시스템관리자.getId());

            assertThat(보낸_메일()).singleElement().satisfies(m -> {
                assertThat(m.to()).containsExactly("e14@company.com");
                assertThat(m.text()).contains("직원1님이 휴가를 승인했습니다");
            });
        }

        @Test
        void 승인_반려_강제_취소_메일에는_처리자의_이름_자격_부서가_들어간다() {
            service.approve(대기_신청(파트원).getId(), 파트장.getId());
            service.reject(대기_신청(파트원).getId(), 인사관리자.getId(), "마감");
            service.cancel(승인된_휴가(파트원, LocalDate.now().minusDays(2)).getId(), 인사관리자.getId(), "근태 정정");

            assertThat(보낸_메일()).filteredOn(m -> m.to().contains(파트원_메일)).extracting(LeaveMail::text)
                    .satisfiesExactly(
                            approved -> assertThat(approved).contains("처리자: 직원7 (팀장)"),
                            rejected -> assertThat(rejected).contains("처리자: 직원11 (인사관리자)"),
                            forced -> assertThat(forced).contains("처리자: 직원11 (인사관리자)"));
        }

        @Test
        void 취소_요청을_승인하거나_반려하면_처리자가_메일에_들어간다() {
            LeaveRequest 승인건 = 승인된_휴가(파트원, LocalDate.now().plusDays(30));
            승인건.requestCancel("일정 변경");
            LeaveRequest 반려건 = 승인된_휴가(파트원, LocalDate.now().plusDays(40));
            반려건.requestCancel("일정 변경");

            service.approveCancellation(승인건.getId(), 파트장.getId());
            service.rejectCancellation(반려건.getId(), 인사관리자.getId(), "인력 부족");

            assertThat(보낸_메일()).extracting(LeaveMail::text).satisfiesExactly(
                    approved -> assertThat(approved)
                            .contains("직원7님이 휴가 취소 요청을 승인해 휴가가 취소되었습니다")
                            .contains("처리자: 직원7 (팀장)"),
                    rejected -> assertThat(rejected)
                            .contains("직원11님이 휴가 취소 요청을 반려했습니다")
                            .contains("처리자: 직원11 (인사관리자)"));
        }

        @Test
        void 인사관리자가_취소_요청을_승인하면_팀장_안내_메일에도_처리자가_들어간다() {
            LeaveRequest request = 승인된_휴가(파트원, LocalDate.now().plusDays(30));
            request.requestCancel("일정 변경");

            service.approveCancellation(request.getId(), 인사관리자.getId());

            assertThat(받은(보낸_메일(), 파트장_메일).text()).contains("처리자: 직원11 (인사관리자)");
        }

        @Test
        void 신청자가_대기_중인_신청을_철회하면_처리자는_신청자_본인이다() {
            service.cancel(대기_신청(파트원).getId(), 파트원.getId(), null);

            assertThat(받은(보낸_메일(), 파트장_메일).text()).contains("처리자: 직원13 (신청자)");
        }

        private List<LeaveMail> 보낸_메일() {
            ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, Mockito.atLeast(0)).publishEvent(events.capture());
            return events.getAllValues().stream()
                    .filter(LeaveMail.class::isInstance).map(LeaveMail.class::cast).toList();
        }

        private LeaveMail 받은(List<LeaveMail> mails, String to) {
            return mails.stream().filter(m -> m.to().contains(to)).findFirst().orElseThrow();
        }
    }

    // --- helpers ---

    private Department 부서(Long id, String name, Department parent) {
        Department department = new Department(name, parent, 0);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    private Employee 직원(Long id, Department department, Role... roles) {
        Employee employee = Employee.builder()
                .email("e" + id + "@company.com").passwordHash("h").name("직원" + id)
                .department(department).roles(Set.of(roles))
                .build();
        ReflectionTestUtils.setField(employee, "id", id);
        employees.put(id, employee);
        return employee;
    }

    private LeaveRequest 대기_신청(Employee employee) {
        LeaveRequest request = new LeaveRequest(employee, 연차, TUE, TUE,
                BigDecimal.ONE, BigDecimal.ONE, 2027, "사유");
        long id = 1000L + requests.size();
        ReflectionTestUtils.setField(request, "id", id);
        requests.put(id, request);
        return request;
    }

    private LeaveRequest 승인된_휴가(Employee employee, LocalDate start) {
        LeaveRequest request = 대기_신청(employee);
        ReflectionTestUtils.setField(request, "startDate", start);
        ReflectionTestUtils.setField(request, "endDate", start);
        request.approve(인사관리자, Instant.now());
        return request;
    }

    private LeaveRequest 대기_취소요청(Employee employee) {
        LeaveRequest request = 승인된_휴가(employee, LocalDate.now().plusDays(50));
        request.requestCancel("변경");
        return request;
    }

    private static LeaveRequestDtos.Create 신청서() {
        return new LeaveRequestDtos.Create(1L, TUE, TUE, "사유");
    }

    private static LeaveRequestDtos.Register 등록서(Employee employee, LocalDate date) {
        return new LeaveRequestDtos.Register(employee.getId(), 1L, date, date, "병원", null, null);
    }

    private static void 승인_권한_없음(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION));
    }
}
