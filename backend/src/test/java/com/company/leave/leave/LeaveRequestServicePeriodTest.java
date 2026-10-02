package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
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
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
 * 입사일 기준 연차 기간과 휴가 신청·승인·취소(v2 "미래 차감 분리"와 같은 규칙).
 * 기산일(다음 입사 기념일)을 오늘부터 한 달 넘게 뒤의 월요일로 잡아, 기산일 앞 금요일 ~ 뒤 화요일 휴가는
 * 금요일 1일을 지금 기간에서, 월·화 2일을 다음 기간에서 뺀다. 입사 3년차라 다음 기간 예상 부여는 16일.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 신청과 연차 기간")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServicePeriodTest {

    private static final long ANNUAL_ID = 1L;
    /** 다음 기산일(월요일). */
    private static final LocalDate 기산일 = LocalDate.now().plusDays(35).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate 입사 = 기산일.minusYears(3);
    /** 지금 기간: 1년 전 기념일에 시작. */
    private static final int 지금_기간 = 기산일.getYear() - 1;
    private static final LocalDate 금 = 기산일.minusDays(3);
    private static final LocalDate 화 = 기산일.plusDays(1);

    @Mock private LeaveRequestRepository requestRepository;
    @Mock private LeaveTypeService leaveTypeService;
    @Mock private EmployeeService employeeService;
    @Mock private LeaveBalanceService balanceService;
    @Mock private HolidayRepository holidayRepository;
    @Mock private PolicyService policyService;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private NotificationService notificationService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private BlackoutPeriodRepository blackoutPeriodRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LeaveRequestService service;
    private final LeavePolicy policy = LeavePolicy.createDefault(); // 입사일 기준, 마이너스 연차 불가, 다음 기간 예약 허용
    private final Map<Integer, LeaveBalance> balances = new HashMap<>();
    private final Map<Long, LeaveRequest> requests = new HashMap<>();
    private Employee 직원;
    private Employee 인사관리자;

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService,
                new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator()),
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher, new com.company.leave.mail.AccountMailProperties(
                        "noreply@company.com", "http://localhost:5173")),
                org.mockito.Mockito.mock(com.company.leave.audit.AuditService.class));

        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동")
                .hireDate(입사).roles(Set.of(Role.EMPLOYEE)).build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        인사관리자 = Employee.builder().email("hr@company.com").passwordHash("h").name("인사")
                .hireDate(LocalDate.of(2015, 1, 2)).roles(Set.of(Role.EMPLOYEE, Role.HR_ADMIN)).build();
        ReflectionTestUtils.setField(인사관리자, "id", 11L);
        lenient().when(employeeService.getEntity(10L)).thenReturn(직원);
        lenient().when(employeeService.getEntity(11L)).thenReturn(인사관리자);
        lenient().when(leaveTypeService.getEntity(ANNUAL_ID)).thenReturn(연차);
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
        lenient().when(departmentRepository.findByLeadId(anyLong())).thenReturn(List.of());
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt())).thenAnswer(inv ->
                balances.computeIfAbsent(inv.<Integer>getArgument(1), y -> new LeaveBalance(10L, y)));
        lenient().when(balanceService.find(anyLong(), anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(balances.get(inv.<Integer>getArgument(1))));
        lenient().when(requestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1000L + requests.size());
            requests.put(r.getId(), r);
            return r;
        });
        lenient().when(requestRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<Long>getArgument(0))));

        잔액(지금_기간).setGranted(new BigDecimal("15"));
    }

    @Test
    void 기산일을_걸친_휴가는_기산일부터의_날짜를_다음_기간에서_뺀다() {
        LeaveRequest saved = 신청(금, 화);

        assertThat(saved.getAppliedYear()).isEqualTo(지금_기간);
        assertThat(saved.getDeductedDays()).isEqualByComparingTo("3");
        assertThat(saved.getCurrentPeriodDeductedDays()).isEqualByComparingTo("1");
        assertThat(saved.getNextPeriodDeductedDays()).isEqualByComparingTo("2");
    }

    @Test
    void 승인하면_두_기간_잔액에서_나눠_빼고_취소하면_나눠_돌려준다() {
        LeaveRequest saved = 신청(금, 화);

        service.approve(saved.getId(), 인사관리자.getId());

        assertThat(잔액(지금_기간).getUsed()).isEqualByComparingTo("1");
        assertThat(잔액(지금_기간 + 1).getUsed()).isEqualByComparingTo("2");

        service.cancel(saved.getId(), 인사관리자.getId(), "일정 변경");

        assertThat(saved.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(잔액(지금_기간).getUsed()).isZero();
        assertThat(잔액(지금_기간 + 1).getUsed()).isZero();
    }

    @Test
    void 지금_기간_잔여가_0이어도_다음_기간_날짜는_신청할_수_있다() {
        잔액(지금_기간).addUsed(new BigDecimal("15"));

        LeaveRequest saved = 신청(화, 화);

        assertThat(saved.getAppliedYear()).isEqualTo(지금_기간 + 1);
        assertThat(saved.getNextPeriodDeductedDays()).isZero();
    }

    @Test
    void 기산일을_걸친_휴가도_지금_기간_몫이_잔여를_넘으면_신청할_수_없다() {
        잔액(지금_기간).addUsed(new BigDecimal("15"));

        assertThatThrownBy(() -> 신청(금, 화))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_LEAVE_BALANCE));
    }

    @Test
    void 다음_기간_예약은_예상_부여_일수를_넘을_수_없다() {
        잔액(지금_기간 + 1).addUsed(new BigDecimal("16")); // 입사 3년차 예상 부여 16일을 이미 예약

        assertThatThrownBy(() -> 신청(화, 화))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_LEAVE_BALANCE);
                    assertThat(ex.getMessage()).contains("다음 연차 기간(" + 기산일 + "부터)", "예약 가능 0일");
                });
    }

    @Test
    void 다음_기간이_끝난_뒤의_날짜는_신청할_수_없다() {
        LocalDate 다다음_기간 = 기산일.plusYears(1).plusDays(1); // 화요일 근처, 시작일 검사보다 범위 검사가 먼저

        assertThatThrownBy(() -> 신청(평일(다다음_기간), 평일(다다음_기간)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_TOO_FAR);
                    assertThat(ex.getMessage()).contains(기산일.plusYears(1).minusDays(1).toString());
                });
    }

    @Test
    void 다음_기간_예약을_끄면_기산일_이후_날짜는_신청할_수_없지만_인사관리자_등록은_된다() {
        ReflectionTestUtils.setField(policy, "nextPeriodReservationEnabled", false);

        assertThatThrownBy(() -> 신청(금, 화))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_TOO_FAR);
                    assertThat(ex.getMessage()).contains("지금 연차 기간", 기산일.minusDays(1).toString());
                });

        service.register(인사관리자.getId(), new LeaveRequestDtos.Register(10L, ANNUAL_ID, 금, 화, "등록", null, null));

        assertThat(잔액(지금_기간 + 1).getUsed()).isEqualByComparingTo("2");
    }

    @Test
    void 미리보기는_지금_기간_몫만_잔여에서_빼고_다음_기간_몫을_따로_알려준다() {
        LeaveRequestDtos.Eligibility e = service.eligibility(10L, ANNUAL_ID, 금, 화, null, null);

        assertThat(e.allowed()).isTrue();
        assertThat(e.deduction()).isEqualByComparingTo("3");
        assertThat(e.remainingDays()).isEqualByComparingTo("15");
        assertThat(e.remainingAfter()).isEqualByComparingTo("14");
        assertThat(e.nextPeriodDeduction()).isEqualByComparingTo("2");
        assertThat(e.periodEnd()).isEqualTo(기산일.minusDays(1));
    }

    @Test
    void 다음_기간에_시작하는_휴가의_미리보기_잔여는_예상_부여_일수다() {
        LeaveRequestDtos.Eligibility e = service.eligibility(10L, ANNUAL_ID, 화, 화, null, null);

        assertThat(e.remainingDays()).isEqualByComparingTo("16");
        assertThat(e.periodStart()).isEqualTo(기산일);
    }

    // --- helpers ---

    private LeaveBalance 잔액(int year) {
        return balances.computeIfAbsent(year, y -> new LeaveBalance(10L, y));
    }

    private LeaveRequest 신청(LocalDate start, LocalDate end) {
        LeaveRequestDtos.Response r = service.create(10L, new LeaveRequestDtos.Create(ANNUAL_ID, start, end, "사유"));
        return requests.get(r.id());
    }

    private static LocalDate 평일(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6 ? date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : date;
    }
}
