package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휴가 결재 권한.
 * <ul>
 *   <li>팀장의 신청(본인 건 포함)은 인사관리자만 결재. 시스템 관리자·상위 부서 팀장도 불가</li>
 *   <li>팀장은 담당 부서(하위 포함)의 일반 직원만 결재</li>
 *   <li>인사관리자는 본인 휴가도 직접 승인 가능</li>
 * </ul>
 * 조직: 제품개발팀(팀장 개발팀장) ⊃ 플랫폼파트(팀장 파트장). 경영지원팀 팀장은 인사관리자.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 결재 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceApprovalTest {

    private static final LocalDate TUE = LocalDate.of(2027, 5, 4);

    @Mock
    private LeaveRequestRepository requestRepository;
    @Mock
    private LeaveTypeService leaveTypeService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private LeaveBalanceService balanceService;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private LeaveAccrualCalculator accrualCalculator;
    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private BlackoutPeriodRepository blackoutPeriodRepository;
    @Mock
    private LeavePolicy policy;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LeaveRequestService service;

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final Map<Long, Employee> employees = new HashMap<>();
    private final Map<Long, LeaveRequest> requests = new HashMap<>();
    private final LeaveBalance balance = new LeaveBalance(0L, 2027);

    private Employee 최고관리자;
    private Employee 인사관리자;
    private Employee 개발팀장;
    private Employee 파트장;
    private Employee 부파트장;
    private Employee 개발팀원;
    private Employee 파트원;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService, accrualCalculator,
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher);

        Department 본사 = 부서(1L, "본사", null);
        Department 제품개발팀 = 부서(2L, "제품개발팀", 본사);
        Department 플랫폼파트 = 부서(3L, "플랫폼파트", 제품개발팀);
        Department 경영지원팀 = 부서(5L, "경영지원팀", 본사);

        최고관리자 = 직원(1L, 본사, Role.SUPER_ADMIN);
        ReflectionTestUtils.setField(최고관리자, "systemAccount", true);
        인사관리자 = 직원(11L, 경영지원팀, Role.EMPLOYEE, Role.TEAM_LEAD, Role.HR_ADMIN);
        개발팀장 = 직원(2L, 제품개발팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        파트장 = 직원(7L, 플랫폼파트, Role.EMPLOYEE, Role.TEAM_LEAD);
        부파트장 = 직원(20L, 플랫폼파트, Role.EMPLOYEE, Role.TEAM_LEAD); // 팀장 역할만 있고 부서장은 아님
        개발팀원 = 직원(6L, 제품개발팀, Role.EMPLOYEE);
        파트원 = 직원(13L, 플랫폼파트, Role.EMPLOYEE);

        제품개발팀.assignLead(개발팀장);
        플랫폼파트.assignLead(파트장);
        경영지원팀.assignLead(인사관리자);
        lenient().when(departmentRepository.findByLeadId(2L)).thenReturn(List.of(제품개발팀));
        lenient().when(departmentRepository.findByLeadId(7L)).thenReturn(List.of(플랫폼파트));
        lenient().when(departmentRepository.findByLeadId(11L)).thenReturn(List.of(경영지원팀));
        lenient().when(departmentRepository.findSubtreeIds(2L)).thenReturn(List.of(2L, 3L));
        lenient().when(departmentRepository.findSubtreeIds(3L)).thenReturn(List.of(3L));
        lenient().when(departmentRepository.findSubtreeIds(5L)).thenReturn(List.of(5L));

        lenient().when(employeeService.getEntity(anyLong())).thenAnswer(inv -> employees.get(inv.<Long>getArgument(0)));
        lenient().when(employeeService.activeAdminIds()).thenReturn(List.of(11L));
        lenient().when(requestRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<Long>getArgument(0))));
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(policy.isAllowNegative()).thenReturn(true);
        lenient().when(policy.allows(any())).thenReturn(true);
        lenient().when(policy.isLeadApprovalRequired()).thenReturn(true); // 기본: 팀장 1차 → 인사 최종
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

    // --- 승인 ---

    @Test
    void 팀장은_자기_휴가를_승인할_수_없다() {
        LeaveRequest request = 대기_신청(개발팀장);

        승인_권한_없음(() -> service.approve(request.getId(), 개발팀장.getId()));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
    }

    @Test
    void 상위_부서_팀장도_하위_부서_팀장의_휴가는_승인할_수_없다() {
        LeaveRequest request = 대기_신청(파트장);

        승인_권한_없음(() -> service.approve(request.getId(), 개발팀장.getId()));
    }

    @Test
    void 부서장이_아니어도_팀장_역할이_있으면_팀장_휴가로_보고_관리자만_승인한다() {
        LeaveRequest request = 대기_신청(부파트장);

        승인_권한_없음(() -> service.approve(request.getId(), 파트장.getId()));
        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 팀장은_담당_부서와_하위_부서_일반_직원의_휴가를_1차_승인한다() {
        LeaveRequest 팀원_신청 = 대기_신청(개발팀원);
        LeaveRequest 하위부서_신청 = 대기_신청(파트원);

        service.approve(팀원_신청.getId(), 개발팀장.getId());
        service.approve(하위부서_신청.getId(), 개발팀장.getId());

        assertThat(팀원_신청.getStatus()).isEqualTo(LeaveRequestStatus.LEAD_APPROVED);
        assertThat(하위부서_신청.getStatus()).isEqualTo(LeaveRequestStatus.LEAD_APPROVED);
    }

    @Test
    void 인사관리자는_팀장의_휴가를_승인한다() {
        LeaveRequest request = 대기_신청(개발팀장);

        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 인사관리자는_자기_휴가를_직접_승인할_수_있다() {
        LeaveRequest request = 대기_신청(인사관리자);

        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    // --- 반려·취소 결재 ---

    @Test
    void 팀장은_자기_휴가를_반려할_수도_없다() {
        LeaveRequest request = 대기_신청(개발팀장);

        승인_권한_없음(() -> service.reject(request.getId(), 개발팀장.getId(), "사유"));
    }

    @Test
    void 팀장은_자기_휴가의_취소_요청을_스스로_승인하거나_반려할_수_없다() {
        LeaveRequest request = 대기_신청(개발팀장);
        request.approve(인사관리자, Instant.now());
        request.requestCancel("일정 변경");

        승인_권한_없음(() -> service.approveCancellation(request.getId(), 개발팀장.getId()));
        승인_권한_없음(() -> service.rejectCancellation(request.getId(), 개발팀장.getId(), "사유"));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCEL_REQUESTED);
    }

    // --- 결재함 ---

    @Test
    void 팀장_결재함에는_본인과_하위_팀장의_신청이_보이지_않는다() {
        when(employeeService.employeeIdsInDepartments(Set.of(2L, 3L))).thenReturn(Set.of(2L, 6L, 7L, 13L, 20L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(
                대기_신청(개발팀장), 대기_신청(개발팀원), 대기_신청(파트장), 대기_신청(파트원), 대기_신청(부파트장)));

        List<LeaveRequestDtos.Response> inbox = service.pendingForApprover(개발팀장.getId());

        assertThat(inbox).extracting(LeaveRequestDtos.Response::employeeId).containsExactly(6L, 13L);
    }

    // --- 결재 알림 ---

    @Test
    void 일반_직원이_신청하면_소속_부서_팀장에게만_알림이_간다() {
        service.create(파트원.getId(), 신청서());

        verify(notificationService).notify(eq(7L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(11L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 팀장이_신청하면_재직_인사관리자에게만_알림이_간다() {
        service.create(부파트장.getId(), 신청서());

        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(7L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 인사관리자가_혼자여도_본인_결재가_가능하므로_결재자_부재_경고가_없다() {
        LeaveRequestDtos.Response response = service.create(인사관리자.getId(), 신청서());

        assertThat(response.requestWarning()).isNull();
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(11L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 팀장의_취소_요청도_관리자에게_알림이_간다() {
        LeaveRequest request = 대기_신청(개발팀장);
        request.approve(인사관리자, Instant.now());
        ReflectionTestUtils.setField(request, "startDate", LocalDate.now().plusDays(30));

        service.cancel(request.getId(), 개발팀장.getId(), "일정 변경");

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCEL_REQUESTED);
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(11L), eq("LEAVE_CANCEL_REQUESTED"), anyString(), anyString(), anyString());
    }

    // --- 2단계 결재(팀장 1차 → 인사 최종) ---

    @Test
    void 팀장이_1차_승인하면_잔액은_그대로이고_인사관리자에게_최종_승인_요청이_간다() {
        LeaveRequest request = 대기_신청(개발팀원);

        service.approve(request.getId(), 개발팀장.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.LEAD_APPROVED);
        assertThat(request.getLeadApprover()).isSameAs(개발팀장);
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
        verify(calendarEventRepository, never()).save(any());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(11L), eq("LEAVE_FINAL_APPROVAL_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(6L), eq("LEAVE_LEAD_APPROVED"), anyString(), anyString(), anyString());
    }

    @Test
    void 팀장_단계_건은_인사관리자도_먼저_승인할_수_없다() {
        LeaveRequest request = 대기_신청(개발팀원);

        승인_권한_없음(() -> service.approve(request.getId(), 인사관리자.getId()));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
    }

    @Test
    void 인사관리자가_최종_승인하면_잔액을_차감하고_팀장은_최종_승인할_수_없다() {
        LeaveRequest request = 대기_신청(개발팀원);
        service.approve(request.getId(), 개발팀장.getId());

        승인_권한_없음(() -> service.approve(request.getId(), 개발팀장.getId()));
        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(balance.getUsed()).isEqualByComparingTo("1");
        verify(calendarEventRepository).save(any());
    }

    @Test
    void 인사관리자는_1차_승인_건을_반려할_수_있다() {
        LeaveRequest request = 대기_신청(개발팀원);
        service.approve(request.getId(), 개발팀장.getId());

        service.reject(request.getId(), 인사관리자.getId(), "인원 부족");

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
    }

    @Test
    void 정책을_끄면_팀장을_거치지_않고_인사관리자가_바로_최종_결재한다() {
        when(policy.isLeadApprovalRequired()).thenReturn(false);
        LeaveRequest request = 대기_신청(개발팀원);

        승인_권한_없음(() -> service.approve(request.getId(), 개발팀장.getId()));
        service.approve(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 정책을_끄면_팀장_결재함은_비고_신청_알림은_인사관리자에게_간다() {
        when(policy.isLeadApprovalRequired()).thenReturn(false);

        service.create(파트원.getId(), 신청서());

        verify(notificationService).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(7L), anyString(), anyString(), anyString(), anyString());
        assertThat(service.pendingForApprover(파트장.getId())).isEmpty();
    }

    @Test
    void 인사관리자_결재함에는_인사_단계_건만_보인다() {
        LeaveRequest 팀원_대기 = 대기_신청(개발팀원);          // 팀장 단계 → 안 보임
        LeaveRequest 팀원_1차승인 = 대기_신청(파트원);
        팀원_1차승인.leadApprove(파트장, Instant.now());        // 인사 단계
        LeaveRequest 팀장_대기 = 대기_신청(파트장);            // 팀장 본인 신청 → 인사 단계
        when(employeeService.allEmployeeIds()).thenReturn(List.of(1L, 2L, 6L, 7L, 13L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(팀원_대기, 팀원_1차승인, 팀장_대기));

        List<LeaveRequestDtos.Response> inbox = service.pendingForApprover(인사관리자.getId());

        assertThat(inbox).extracting(LeaveRequestDtos.Response::id)
                .containsExactlyInAnyOrder(팀원_1차승인.getId(), 팀장_대기.getId());
        assertThat(inbox).allSatisfy(r -> assertThat(r.approvalStage()).isEqualTo(ApprovalStage.HR));
    }

    @Test
    void 팀장이_오늘_종일_휴가로_부재면_사유를_적어_인사관리자에게_바로_신청한다() {
        팀장_오늘_연차(파트장);

        LeaveRequestDtos.Response created = service.create(파트원.getId(), new LeaveRequestDtos.Create(
                1L, TUE, TUE, "사유", null, null, null, "팀장 부재로 인사관리자에게 신청합니다."));

        LeaveRequest request = requests.get(created.id());
        assertThat(request.getHrDirectReason()).isEqualTo("팀장 부재로 인사관리자에게 신청합니다.");
        verify(notificationService).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(7L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        service.approve(request.getId(), 인사관리자.getId());
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test
    void 팀장이_부재가_아니면_인사관리자_직행_신청을_거부한다() {
        assertThatThrownBy(() -> service.create(파트원.getId(), new LeaveRequestDtos.Create(
                1L, TUE, TUE, "사유", null, null, null, "빨리 처리해 주세요")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_HR_DIRECT_NOT_ALLOWED));
    }

    @Test
    void 결재_경로는_팀장_부재_여부와_인사_직행_가능을_알려준다() {
        LeaveRequestDtos.ApprovalRoute normal = service.approvalRoute(파트원.getId());
        assertThat(normal.firstStage()).isEqualTo(ApprovalStage.LEAD);
        assertThat(normal.hrDirectAvailable()).isFalse();

        팀장_오늘_연차(파트장);
        LeaveRequestDtos.ApprovalRoute absent = service.approvalRoute(파트원.getId());
        assertThat(absent.leadAbsent()).isTrue();
        assertThat(absent.leadAbsenceType()).isEqualTo("연차");
        assertThat(absent.hrDirectAvailable()).isTrue();
    }

    @Test
    void 승인된_휴가의_취소_요청은_인사관리자가_결재하고_확정되면_팀장에게_안내가_간다() {
        LeaveRequest request = 대기_신청(파트원);
        request.approve(인사관리자, Instant.now());
        ReflectionTestUtils.setField(request, "startDate", LocalDate.now().plusDays(30));
        service.cancel(request.getId(), 파트원.getId(), "일정 변경");
        verify(notificationService).notify(eq(11L), eq("LEAVE_CANCEL_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(7L), eq("LEAVE_CANCEL_REQUESTED"), anyString(), anyString(), anyString());

        승인_권한_없음(() -> service.approveCancellation(request.getId(), 파트장.getId()));
        service.approveCancellation(request.getId(), 인사관리자.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        verify(notificationService).notify(eq(7L), eq("LEAVE_CANCELLED_INFO"), anyString(),
                org.mockito.ArgumentMatchers.contains("취소되었습니다"), anyString());
    }

    private void 팀장_오늘_연차(Employee lead) {
        LeaveRequest leave = new LeaveRequest(lead, 연차, LocalDate.now(), LocalDate.now(),
                BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().getYear(), "휴가");
        leave.approve(인사관리자, Instant.now());
        lenient().when(requestRepository.findApprovedBetween(any(), any())).thenReturn(List.of(leave));
    }

    // --- 관리 전용 계정 ---

    @Test
    void 관리_전용_계정은_휴가를_신청할_수_없다() {
        ReflectionTestUtils.setField(최고관리자, "systemAccount", true);

        assertThatThrownBy(() -> service.create(최고관리자.getId(), 신청서()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("관리 전용 계정");
                });
        verify(requestRepository, never()).save(any());
    }

    // --- 조회 권한은 그대로 ---
    @Test
    void 시스템_관리자는_팀장_단계와_인사_단계_모두_승인과_반려를_할_수_없다() {
        for (Employee applicant : List.of(개발팀원, 개발팀장)) {
            LeaveRequest request = 대기_신청(applicant);
            승인_권한_없음(() -> service.approve(request.getId(), 최고관리자.getId()));
            승인_권한_없음(() -> service.reject(request.getId(), 최고관리자.getId(), "사유"));
            assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }
        verify(calendarEventRepository, never()).save(any());
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
    }

    @Test
    void 시스템_관리자는_취소_요청을_승인하거나_반려할_수_없다() {
        LeaveRequest request = 대기_신청(개발팀장);
        request.approve(인사관리자, Instant.now());
        request.requestCancel("변경");
        승인_권한_없음(() -> service.approveCancellation(request.getId(), 최고관리자.getId()));
        승인_권한_없음(() -> service.rejectCancellation(request.getId(), 최고관리자.getId(), "사유"));
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCEL_REQUESTED);
        verify(calendarEventRepository, never()).deleteByLeaveRequestId(anyLong());
    }

    @Test
    void 시스템_관리자는_대기_1차승인_승인_취소대기_건을_대리_취소할_수_없다() {
        for (LeaveRequestStatus status : List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.LEAD_APPROVED,
                LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED)) {
            LeaveRequest request = 대기_신청(개발팀원);
            ReflectionTestUtils.setField(request, "status", status);
            assertThatThrownBy(() -> service.cancel(request.getId(), 최고관리자.getId(), "대리 취소"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(request.getStatus()).isEqualTo(status);
        }
        verify(calendarEventRepository, never()).deleteByLeaveRequestId(anyLong());
    }

    @Test
    void 시스템_관리자는_서비스를_직접_호출해도_결재함이_거부된다() {
        승인_권한_없음(() -> service.pendingForApprover(최고관리자.getId()));
        verify(requestRepository, never()).findForApproval(any(), any());
    }

    @Test
    void 시스템_관리자에게_인사와_팀장_역할과_부서장이_섞여도_결재할_수_없다() {
        최고관리자.replaceRoles(Set.of(Role.SUPER_ADMIN, Role.HR_ADMIN, Role.TEAM_LEAD));
        lenient().when(departmentRepository.findByLeadId(1L)).thenReturn(List.of(개발팀원.getDepartment()));
        LeaveRequest request = 대기_신청(개발팀원);
        승인_권한_없음(() -> service.approve(request.getId(), 최고관리자.getId()));
        승인_권한_없음(() -> service.reject(request.getId(), 최고관리자.getId(), "사유"));
        request.leadApprove(개발팀장, Instant.now());
        승인_권한_없음(() -> service.approve(request.getId(), 최고관리자.getId()));
        승인_권한_없음(() -> service.reject(request.getId(), 최고관리자.getId(), "사유"));
    }

    @Test
    void 인사관리자는_본인_신청을_결재함에서_보고_승인과_반려할_수_있다() {
        LeaveRequest 승인건 = 대기_신청(인사관리자);
        LeaveRequest 반려건 = 대기_신청(인사관리자);
        when(employeeService.allEmployeeIds()).thenReturn(List.of(11L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(승인건, 반려건));
        assertThat(service.pendingForApprover(11L)).extracting(LeaveRequestDtos.Response::id)
                .containsExactlyInAnyOrder(승인건.getId(), 반려건.getId());
        service.approve(승인건.getId(), 11L);
        service.reject(반려건.getId(), 11L, "변경");
        assertThat(승인건.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(반려건.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
    }

    @Test
    void 인사관리자는_다른_직원_휴가의_대리_취소를_계속_할_수_있다() {
        LeaveRequest request = 대기_신청(개발팀원);
        request.approve(인사관리자, Instant.now());
        ReflectionTestUtils.setField(request, "startDate", LocalDate.now().plusDays(30));
        balance.addUsed(BigDecimal.ONE);
        service.cancel(request.getId(), 인사관리자.getId(), "대리 취소");
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
    }

    @Test
    void 팀장_역할과_부서장_지정이_없는_인사관리자도_본인_결재를_할_수_있다() {
        Employee hrOnly = 직원(22L, 개발팀원.getDepartment(), Role.HR_ADMIN);
        assertThat(service.approvalRoute(22L).firstStage()).isEqualTo(ApprovalStage.HR);
        LeaveRequest approve = 대기_신청(hrOnly);
        LeaveRequest reject = 대기_신청(hrOnly);
        when(employeeService.allEmployeeIds()).thenReturn(List.of(22L));
        when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(approve, reject));
        assertThat(service.pendingForApprover(22L)).extracting(LeaveRequestDtos.Response::id)
                .containsExactlyInAnyOrder(approve.getId(), reject.getId());
        service.approve(approve.getId(), 22L);
        service.reject(reject.getId(), 22L, "변경");
        assertThat(approve.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(reject.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
    }

    @Test
    void 시스템_관리자가_부서장으로_지정되어도_결재_경로와_알림에서_제외한다() {
        개발팀원.getDepartment().assignLead(최고관리자);
        assertThat(service.approvalRoute(개발팀원.getId()).firstStage()).isEqualTo(ApprovalStage.HR);
        service.create(개발팀원.getId(), 신청서());
        verify(notificationService).notify(eq(11L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 시스템_관리자_부서장은_건너뛰고_상위의_일반_팀장에게_1차_결재를_요청한다() {
        파트원.getDepartment().assignLead(최고관리자);
        assertThat(service.approvalRoute(파트원.getId()).firstStage()).isEqualTo(ApprovalStage.LEAD);
        service.create(파트원.getId(), 신청서());
        verify(notificationService).notify(eq(2L), eq("LEAVE_REQUESTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 인사관리자가_없어도_팀장_신청은_저장되고_응답과_알림과_로그에_경고한다() {
        when(employeeService.activeAdminIds()).thenReturn(List.of());
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(LeaveRequestService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LeaveRequestDtos.Response response = service.create(개발팀장.getId(), 신청서());
            String warning = "결재할 인사관리자가 없습니다. 관리자에게 문의하세요.";
            assertThat(response.status()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(requests).containsKey(response.id());
            assertThat(response.requestWarning()).isEqualTo(warning);
            verify(notificationService).notify(eq(2L), eq("LEAVE_NO_HR_APPROVER"), anyString(),
                    eq(warning), eq("/my-leaves"));
            verify(notificationService, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getFormattedMessage()).contains(warning);
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 시스템_관리자는_다른_직원의_연차_정보를_계속_조회할_수_있다() {
        assertThatCode(() -> service.assertCanViewEmployeeData(최고관리자.getId(), 개발팀장.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void 상위_부서_팀장은_하위_부서_팀장의_연차_정보를_계속_조회할_수_있다() {
        assertThatCode(() -> service.assertCanViewEmployeeData(개발팀장.getId(), 파트장.getId()))
                .doesNotThrowAnyException();
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

    private static LeaveRequestDtos.Create 신청서() {
        return new LeaveRequestDtos.Create(1L, TUE, TUE, "사유");
    }

    private static void 승인_권한_없음(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_NO_APPROVAL_PERMISSION));
    }
}
