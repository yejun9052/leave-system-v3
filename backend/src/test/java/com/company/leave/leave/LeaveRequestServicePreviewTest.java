package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
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
 * 신청 미리보기(eligibility 에 종료일까지 준 경우): 신청과 같은 계산으로 근무일·차감·신청 후 잔여를 알려 주고,
 * 규칙 위반이면 allowed=false 와 사유를 준다. 저장하지 않는다.
 * 기준 주: 2027-06-10(목), 06-11(금), 06-12(토), 06-13(일), 06-14(월). 공휴일 없음. 일수 계산은 실제 WorkdayCalculator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 신청 미리보기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServicePreviewTest {

    private static final LocalDate THU = LocalDate.of(2027, 6, 10);
    private static final LocalDate FRI = LocalDate.of(2027, 6, 11);
    private static final LocalDate SAT = LocalDate.of(2027, 6, 12);
    private static final LocalDate MON = LocalDate.of(2027, 6, 14);
    private static final long EMP = 10L;
    private static final long ADMIN = 1L;

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
    private final LeaveBalance balance = new LeaveBalance(EMP, 2027);
    private final Map<Long, LeaveRequest> saved = new HashMap<>();
    private Employee employee;

    private final LeaveType 연차 = 종류(1L, "ANNUAL", "연차", "1.0", DayPortion.FULL, true, false);
    private final LeaveType 반차 = 종류(2L, "HALF_AM", "오전 반차", "0.5", DayPortion.HALF, true, false);
    private final LeaveType 병가 = 종류(6L, "SICK", "병가", "0.0", DayPortion.FULL, false, true);

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService, accrualCalculator,
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher);

        employee = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(employee, "id", EMP);
        Employee admin = Employee.builder().email("hr@company.com").passwordHash("h").name("인사")
                .roles(Set.of(Role.HR_ADMIN)).build();
        ReflectionTestUtils.setField(admin, "id", ADMIN);
        lenient().when(employeeService.getEntity(EMP)).thenReturn(employee);
        lenient().when(employeeService.getEntity(ADMIN)).thenReturn(admin);
        for (LeaveType t : List.of(연차, 반차, 병가)) {
            lenient().when(leaveTypeService.getEntity(t.getId())).thenReturn(t);
        }
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(policy.allows(any())).thenReturn(true);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt())).thenReturn(balance);
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(requestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 500L + saved.size());
            saved.put(r.getId(), r);
            return r;
        });
        lenient().when(requestRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<Long>getArgument(0))));
        잔여(15);
    }

    @Test
    void 주말이_낀_3일_기간의_근무일과_차감이_실제_신청_결과와_같다() {
        when(requestRepository.sumPendingDeductedDays(EMP, 2027)).thenReturn(BigDecimal.ONE);

        LeaveRequestDtos.Eligibility preview = 미리보기(연차, THU, SAT, null);

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.workdays()).isEqualTo(2);
        assertThat(preview.deduction()).isEqualByComparingTo("2");
        assertThat(preview.pendingDays()).isEqualByComparingTo("1");
        assertThat(preview.remainingAfter()).isEqualByComparingTo("12");
        verify(requestRepository, never()).save(any());

        LeaveRequest created = 신청(연차, THU, SAT, null);
        assertThat(created.getDays()).isEqualByComparingTo(BigDecimal.valueOf(preview.workdays()));
        assertThat(created.getDeductedDays()).isEqualByComparingTo(preview.deduction());
    }

    @Test
    void 시작일이_토요일이면_신청할_수_없다고_알려준다() {
        LeaveRequestDtos.Eligibility preview = 미리보기(연차, SAT, MON, null);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).contains("시작일");
        assertThat(preview.workdays()).isNull();
        verify(requestRepository, never()).save(any());
    }

    @Test
    void 반차에_종료일을_시작일과_다르게_주면_하루만_신청할_수_있다고_알려준다() {
        LeaveRequestDtos.Eligibility preview = 미리보기(반차, THU, FRI, null);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).contains("하루만");
    }

    @Test
    void 대기_중인_신청과_기간이_겹치면_신청할_수_없다고_알려준다() {
        LeaveRequest existing = new LeaveRequest(employee, 연차, FRI, FRI, BigDecimal.ONE, BigDecimal.ONE, 2027, "기존");
        when(requestRepository.findActiveOverlapping(EMP, THU, FRI)).thenReturn(List.of(existing));

        LeaveRequestDtos.Eligibility preview = 미리보기(연차, THU, FRI, null);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).contains("겹칩니다");
    }

    @Test
    void 잔여_연차보다_많이_신청하면_부족하다고_알려준다() {
        잔여(1);

        LeaveRequestDtos.Eligibility preview = 미리보기(연차, THU, FRI, null);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.reason()).contains("잔여 연차가 부족");
    }

    @Test
    void 병가_소멸_예정_일수가_실제_승인_때_소멸되는_일수와_같다() {
        잔여(0.5);

        LeaveRequestDtos.Eligibility preview = 미리보기(병가, THU, FRI, null);

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.forfeitDays()).isEqualByComparingTo("0.5");
        assertThat(preview.deduction()).isEqualByComparingTo("0");
        assertThat(preview.remainingAfter()).isEqualByComparingTo("0");

        LeaveRequest created = 신청(병가, THU, FRI, true);
        service.approve(created.getId(), ADMIN);
        assertThat(created.getForfeitedDays()).isEqualByComparingTo(preview.forfeitDays());
    }

    @Test
    void 종료일이_없으면_기존처럼_종류_조건만_보고_시작일은_검사하지_않는다() {
        LeaveRequestDtos.Eligibility result = service.eligibility(EMP, 연차.getId(), SAT);

        assertThat(result.allowed()).isTrue();
        assertThat(result.workdays()).isNull();
    }

    // --- helpers ---

    private static LeaveType 종류(long id, String code, String name, String deduct, DayPortion portion,
                                boolean deductFromAnnual, boolean requiresAnnualExhausted) {
        LeaveType type = new LeaveType(code, name, new BigDecimal(deduct), true, portion, deductFromAnnual,
                requiresAnnualExhausted, "#000", 1);
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    /** 승인 기준 잔여 연차를 맞춘다. */
    private void 잔여(double remaining) {
        balance.setGranted(BigDecimal.valueOf(remaining).add(balance.getUsed()).add(balance.getExpired()));
    }

    private LeaveRequestDtos.Eligibility 미리보기(LeaveType type, LocalDate start, LocalDate end, Integer hours) {
        return service.eligibility(EMP, type.getId(), start, end, hours, null);
    }

    private LeaveRequest 신청(LeaveType type, LocalDate start, LocalDate end, Boolean acknowledged) {
        LeaveRequestDtos.Response response = service.create(EMP,
                new LeaveRequestDtos.Create(type.getId(), start, end, "사유", null, acknowledged));
        return saved.get(response.id());
    }
}
