package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.dto.DateSpan;
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
import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 달력에서 여러 날짜를 골라 신청(이어진 묶음마다 신청 한 건). 묶음마다 단건 신청과 같은 검증을 하고,
 * 앞 묶음의 차감을 다음 묶음의 잔액 검사에 넣으며, 걸린 묶음은 날짜를 붙여 알린다.
 * 입사 기념일은 신청 주보다 반년쯤 뒤라 모든 묶음이 같은 연차 기간이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("여러 날짜 묶음 신청")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceManyTest {

    private static final long ANNUAL_ID = 1L;
    private static final long HALF_ID = 2L;
    private static final LeavePeriodCalculator PERIODS =
            new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator());
    /** 3주 뒤 월요일부터 한 주(월~금). */
    private static final LocalDate 월 = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate 화 = 월.plusDays(1);
    private static final LocalDate 수 = 월.plusDays(2);
    private static final LocalDate 목 = 월.plusDays(3);
    private static final LocalDate 금 = 월.plusDays(4);
    private static final LocalDate 입사 = 월.plusMonths(6).minusYears(5);

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
    private final LeavePolicy policy = LeavePolicy.createDefault();
    private final Map<Integer, LeaveBalance> balances = new HashMap<>();
    private final Map<Long, LeaveRequest> requests = new LinkedHashMap<>();
    private Employee 직원;
    private int 기간;

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 오전반차 = new LeaveType("HALF_AM", "오전 반차", new BigDecimal("0.5"), true, true, true, "#000", 2);

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService, PERIODS,
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher, new com.company.leave.mail.AccountMailProperties(
                        "noreply@company.com", "http://localhost:5173")),
                org.mockito.Mockito.mock(com.company.leave.audit.AuditService.class));

        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동")
                .hireDate(입사).role(Role.EMPLOYEE).build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        ReflectionTestUtils.setField(연차, "id", ANNUAL_ID);
        ReflectionTestUtils.setField(오전반차, "id", HALF_ID);
        lenient().when(employeeService.getEntity(10L)).thenReturn(직원);
        lenient().when(leaveTypeService.getEntity(ANNUAL_ID)).thenReturn(연차);
        lenient().when(leaveTypeService.getEntity(HALF_ID)).thenReturn(오전반차);
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
        lenient().when(departmentRepository.findByLeadId(anyLong())).thenReturn(List.of());
        // 저장된 결재 대기 신청의 차감 합계(앞 묶음이 저장되면 다음 묶음에서 보인다)
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenAnswer(inv -> requests
                .values().stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.PENDING
                        && r.getAppliedYear() == inv.<Integer>getArgument(1))
                .map(LeaveRequest::getDeductedDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
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

        기간 = PERIODS.yearOf(입사, 월, policy);
        balances.computeIfAbsent(기간, y -> new LeaveBalance(10L, y)).setGranted(new BigDecimal("15"));
    }

    private void 부여(String days) {
        balances.get(기간).setGranted(new BigDecimal(days));
    }

    private static DateSpan 묶음(LocalDate start, LocalDate end) {
        return new DateSpan(start, end);
    }

    private LeaveRequestDtos.CreateMany 신청(long typeId, DateSpan... spans) {
        return new LeaveRequestDtos.CreateMany(typeId, List.of(spans), null, null, null, null, null);
    }

    private static String 표시(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth();
    }

    @Test
    void 떨어진_묶음_둘은_신청_두_건이_되고_각각_결재_대기다() {
        List<LeaveRequestDtos.Response> created = service.createMany(10L, 신청(ANNUAL_ID, 묶음(월, 화), 묶음(목, 목)));

        assertThat(created).hasSize(2);
        assertThat(requests.values()).extracting(LeaveRequest::getStartDate).containsExactly(월, 목);
        assertThat(requests.values()).extracting(LeaveRequest::getDeductedDays)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2"), new BigDecimal("1"));
        assertThat(requests.values()).allMatch(r -> r.getStatus() == LeaveRequestStatus.PENDING);
    }

    @Test
    void 보낸_순서와_상관없이_날짜순으로_신청한다() {
        service.createMany(10L, 신청(ANNUAL_ID, 묶음(목, 금), 묶음(월, 월)));

        assertThat(requests.values()).extracting(LeaveRequest::getStartDate).containsExactly(월, 목);
    }

    @Test
    void 뒤_묶음이_앞_묶음까지_합쳐_잔여를_넘으면_그_날짜를_붙여_거부한다() {
        부여("3");

        assertThatThrownBy(() -> service.createMany(10L, 신청(ANNUAL_ID, 묶음(월, 화), 묶음(목, 금))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_LEAVE_BALANCE);
                    assertThat(ex.getMessage()).startsWith(표시(목) + " ~ " + 표시(금) + ": ");
                });
    }

    @Test
    void 묶음_하나면_사유_앞에_날짜를_붙이지_않는다() {
        부여("1");

        assertThatThrownBy(() -> service.createMany(10L, 신청(ANNUAL_ID, 묶음(월, 화))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).startsWith("잔여 연차가 부족합니다"));
    }

    @Test
    void 미리보기는_앞_묶음의_차감을_결재_대기에_더해_신청_후_잔여를_차례로_줄인다() {
        부여("5");

        LeaveRequestDtos.ManyEligibility e = service.eligibilityMany(10L, ANNUAL_ID,
                List.of(묶음(월, 화), 묶음(목, 금)), null, null, null);

        assertThat(e.allowed()).isTrue();
        assertThat(e.items()).hasSize(2);
        assertThat(e.items().get(0).pendingDays()).isEqualByComparingTo("0");
        assertThat(e.items().get(0).remainingAfter()).isEqualByComparingTo("3");
        assertThat(e.items().get(1).pendingDays()).isEqualByComparingTo("2");
        assertThat(e.items().get(1).remainingAfter()).isEqualByComparingTo("1");
        assertThat(requests).as("미리보기는 저장하지 않는다").isEmpty();
    }

    @Test
    void 미리보기에서_뒤_묶음이_잔여를_넘으면_불가와_그_날짜를_알려_주고_멈춘다() {
        부여("3");

        LeaveRequestDtos.ManyEligibility e = service.eligibilityMany(10L, ANNUAL_ID,
                List.of(묶음(월, 화), 묶음(목, 금), 묶음(금.plusDays(3), 금.plusDays(3))), null, null, null);

        assertThat(e.allowed()).isFalse();
        assertThat(e.reason()).startsWith(표시(목) + " ~ " + 표시(금) + ": 잔여 연차가 부족합니다");
        assertThat(e.items()).hasSize(2);
    }

    @Test
    void 반차는_날짜_하나만_신청할_수_있다() {
        assertThatThrownBy(() -> service.createMany(10L, 신청(HALF_ID, 묶음(월, 월), 묶음(수, 수))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("반차·시간차는 날짜 하나만"));
        assertThat(requests).isEmpty();
    }

    @Test
    void 겹치는_묶음은_받지_않는다() {
        assertThatThrownBy(() -> service.createMany(10L, 신청(ANNUAL_ID, 묶음(월, 수), 묶음(수, 금))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).startsWith("고른 날짜가 겹칩니다"));
        assertThat(requests).isEmpty();
    }

    @Test
    void 금지_기간_여러_묶음에_걸친_휴가는_한_번만_처리하고_유지_목록에_남기지_않는다() {
        // 월~금 결재 대기 휴가 하나가 금지 묶음 월·목 둘 다에 걸친다
        LeaveRequest 걸친_휴가 = new LeaveRequest(직원, 연차, 월, 금, new BigDecimal("5"), new BigDecimal("5"), 기간, null);
        ReflectionTestUtils.setField(걸친_휴가, "id", 77L);
        when(requestRepository.findByStatusInOverlapping(any(), any(), any())).thenReturn(List.of(걸친_휴가));

        LeaveRequestService.BlackoutPlan plan = service.planBlackouts(
                List.of(묶음(월, 월), 묶음(목, 목)), BlackoutConflictMode.KEEP_APPROVED);

        assertThat(plan.reject()).containsExactly(걸친_휴가);
        assertThat(plan.kept()).isEmpty();
    }
}
