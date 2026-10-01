package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휴가 신청 시작일 규칙: 시작일은 근무일이어야 한다(반차 포함).
 * 평일이 하루라도 끼면 기간 중간·끝의 주말·공휴일은 허용하고 차감에서만 뺀다.
 * 기준 주: 2027-04-30(금), 05-01(토), 05-02(일), 05-03(월, 대체공휴일), 05-04(화), 05-05(수, 어린이날).
 * 일수 계산은 실제 WorkdayCalculator 를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 신청 시작일 규칙")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceStartDateTest {

    private static final LocalDate FRI = LocalDate.of(2027, 4, 30);
    private static final LocalDate SAT = LocalDate.of(2027, 5, 1);
    private static final LocalDate SUN = LocalDate.of(2027, 5, 2);
    private static final LocalDate HOLIDAY_MON = LocalDate.of(2027, 5, 3);
    private static final LocalDate TUE = LocalDate.of(2027, 5, 4);
    private static final LocalDate HOLIDAY_WED = LocalDate.of(2027, 5, 5);

    private static final long ANNUAL_ID = 1L;
    private static final long HALF_ID = 2L;

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

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 오전반차 = new LeaveType("HALF_AM", "오전 반차", new BigDecimal("0.5"), true, true, true, "#000", 2);

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService, accrualCalculator,
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher, new com.company.leave.mail.AccountMailProperties(
                        "noreply@company.com", "http://localhost:5173")));

        Employee employee = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(employee, "id", 10L);
        lenient().when(employeeService.getEntity(10L)).thenReturn(employee);
        lenient().when(leaveTypeService.getEntity(ANNUAL_ID)).thenReturn(연차);
        lenient().when(leaveTypeService.getEntity(HALF_ID)).thenReturn(오전반차);
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(0);
            LocalDate to = inv.getArgument(1);
            return List.of(new Holiday(HOLIDAY_MON, "대체공휴일(노동절)"), new Holiday(HOLIDAY_WED, "어린이날"))
                    .stream().filter(h -> !h.getDate().isBefore(from) && !h.getDate().isAfter(to)).toList();
        });
        // 사용 정책은 모두 통과(잔액 부족 허용, 제한 없음)
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(policy.isAllowNegative()).thenReturn(true);
        lenient().when(policy.allows(any())).thenReturn(true);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt())).thenReturn(new LeaveBalance(10L, 2027));
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
    }

    // --- 거부 ---

    @Test
    void 토요일_반차는_신청할_수_없다() {
        시작일_오류(HALF_ID, SAT, SAT);
    }

    @Test
    void 공휴일_반차는_신청할_수_없다() {
        시작일_오류(HALF_ID, HOLIDAY_WED, HOLIDAY_WED);
    }

    @Test
    void 뒤에_평일이_있어도_토요일에_시작하면_신청할_수_없다() {
        시작일_오류(ANNUAL_ID, SAT, TUE);
    }

    @Test
    void 뒤에_평일이_있어도_일요일에_시작하면_신청할_수_없다() {
        시작일_오류(ANNUAL_ID, SUN, TUE);
    }

    @Test
    void 뒤에_평일이_있어도_공휴일에_시작하면_신청할_수_없다() {
        시작일_오류(ANNUAL_ID, HOLIDAY_MON, TUE);
    }

    // --- 허용 ---

    @Test
    void 평일에_시작하면_중간의_주말과_공휴일을_끼고_한_번에_신청할_수_있다() {
        LeaveRequest saved = 신청(ANNUAL_ID, FRI, TUE);

        // 금·화만 근무일(토·일·월 대체공휴일 제외)
        assertThat(saved.getDays()).isEqualByComparingTo("2");
        assertThat(saved.getDeductedDays()).isEqualByComparingTo("2");
    }

    @Test
    void 평일에_시작하면_주말이나_공휴일로_끝나도_신청할_수_있다() {
        assertThat(신청(ANNUAL_ID, FRI, SUN).getDays()).isEqualByComparingTo("1");
        assertThat(신청(ANNUAL_ID, TUE, HOLIDAY_WED).getDays()).isEqualByComparingTo("1");
    }

    @Test
    void 평일_반차는_0_5일로_신청된다() {
        LeaveRequest saved = 신청(HALF_ID, TUE, TUE);

        assertThat(saved.getDays()).isEqualByComparingTo("0.5");
        assertThat(saved.getDeductedDays()).isEqualByComparingTo("0.5");
    }

    // --- helpers ---

    private void 시작일_오류(long typeId, LocalDate start, LocalDate end) {
        assertThatThrownBy(() -> service.create(10L, new LeaveRequestDtos.Create(typeId, start, end, "사유")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INVALID_PERIOD);
                    assertThat(ex.getMessage()).contains("시작일");
                });
        verify(requestRepository, never()).save(any());
    }

    private LeaveRequest 신청(long typeId, LocalDate start, LocalDate end) {
        service.create(10L, new LeaveRequestDtos.Create(typeId, start, end, "사유"));
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(requestRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
