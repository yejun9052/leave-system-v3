package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 연차 기간별 잔액 조회: 기간 시작·끝, 다음 기간 예약분(지금 기간에만), 이력에서 다음 기간 숨김,
 * 직원별 지금 기간 잔액 모으기(대시보드·보고서·촉진).
 * 직원은 3년 10일 전에 입사해 지금 기간이 열흘 전에 시작됐다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 기간별 잔액 조회")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveBalanceServiceTest {

    private static final LocalDate 입사 = LocalDate.now().minusYears(3).minusDays(10);
    private static final LeavePeriodCalculator PERIODS =
            new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator());

    @Mock private LeaveBalanceRepository balanceRepository;
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PolicyService policyService;

    private LeaveBalanceService service;
    private final LeavePolicy policy = LeavePolicy.createDefault();
    private final Map<Integer, LeaveBalance> balances = new TreeMap<>();
    private Employee 직원;
    private int 지금_기간;

    @BeforeEach
    void setUp() {
        service = new LeaveBalanceService(balanceRepository, requestRepository, employeeRepository, policyService,
                PERIODS);
        직원 = 직원(10L, "홍길동", 입사);
        지금_기간 = PERIODS.currentYear(입사, policy);

        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(직원));
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(balanceRepository.findByEmployeeIdAndYear(anyLong(), anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(balances.get(inv.<Integer>getArgument(1))));
        lenient().when(balanceRepository.save(org.mockito.ArgumentMatchers.any(LeaveBalance.class)))
                .thenAnswer(inv -> {
                    LeaveBalance b = inv.getArgument(0);
                    balances.put(b.getYear(), b);
                    return b;
                });
        lenient().when(balanceRepository.findByEmployeeIdOrderByYearDesc(10L))
                .thenAnswer(inv -> balances.values().stream()
                        .sorted(Comparator.comparingInt(LeaveBalance::getYear).reversed()).toList());
    }

    @Test
    void 지금_기간_잔액에_기간_시작_끝과_다음_기간_예약분을_담는다() {
        잔액(지금_기간, "16", "3");
        잔액(지금_기간 + 1, "0", "2");
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간)).thenReturn(new BigDecimal("1"));
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간 + 1)).thenReturn(new BigDecimal("0.5"));

        LeaveBalanceResponse r = service.getResponse(10L, 지금_기간);

        LeavePeriodCalculator.Period period = PERIODS.period(입사, 지금_기간, policy);
        assertThat(r.periodStart()).isEqualTo(period.start());
        assertThat(r.periodEnd()).isEqualTo(period.end());
        assertThat(r.remaining()).isEqualByComparingTo("13");
        assertThat(r.pending()).isEqualByComparingTo("1");
        assertThat(r.nextPeriodReserved()).isEqualByComparingTo("2.5");
    }

    @Test
    void 지난_기간_잔액에는_다음_기간_예약분을_채우지_않는다() {
        잔액(지금_기간 - 1, "15", "15");
        잔액(지금_기간, "16", "3");

        LeaveBalanceResponse r = service.getResponse(10L, 지금_기간 - 1);

        assertThat(r.nextPeriodReserved()).isZero();
    }

    @Test
    void 잔액이_없으면_0으로_만들어_돌려준다() {
        LeaveBalanceResponse r = service.getResponse(10L, 지금_기간);

        assertThat(r.granted()).isZero();
        assertThat(balances).containsKey(지금_기간);
    }

    @Test
    void 이력은_지난_기간과_지금_기간만_최신순으로_보여주고_다음_기간은_숨긴다() {
        잔액(지금_기간 - 1, "15", "15");
        잔액(지금_기간, "16", "3");
        잔액(지금_기간 + 1, "0", "2");

        List<LeaveBalanceResponse> list = service.listResponses(10L);

        assertThat(list).extracting(LeaveBalanceResponse::year).containsExactly(지금_기간, 지금_기간 - 1);
        assertThat(list.get(0).nextPeriodReserved()).isEqualByComparingTo("2");
        assertThat(list.get(1).nextPeriodReserved()).isZero();
    }

    @Test
    void 지금_기간은_직원의_입사_기념일로_정한다() {
        assertThat(service.currentYear(10L)).isEqualTo(입사.getYear() + 3);
    }

    @Test
    void 직원을_찾을_수_없으면_달력_연도로_본다() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.currentYear(99L)).isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void 기준일에_직원마다_쓰고_있던_기간의_잔액을_모은다() {
        LocalDate today = LocalDate.now();
        Employee 다른_직원 = 직원(20L, "김철수", today.minusYears(2).plusDays(10)); // 기념일이 열흘 뒤
        Employee 잔액_없음 = 직원(30L, "이영희", today.minusYears(5));
        when(employeeRepository.findByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE))
                .thenReturn(List.of(직원, 다른_직원, 잔액_없음));
        int 다른_기간 = PERIODS.yearOf(다른_직원.getHireDate(), today, policy);
        LeaveBalance 내_잔액 = new LeaveBalance(10L, 지금_기간);
        LeaveBalance 다른_잔액 = new LeaveBalance(20L, 다른_기간);
        LeaveBalance 다른_직원의_다른_해 = new LeaveBalance(20L, 지금_기간 == 다른_기간 ? 다른_기간 + 1 : 지금_기간);
        when(balanceRepository.findByYearIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(내_잔액, 다른_잔액, 다른_직원의_다른_해));

        List<LeaveBalanceService.PeriodBalance> result = service.balancesAsOf(today, true);

        assertThat(result).extracting(pb -> pb.employee().getName()).containsExactly("홍길동", "김철수");
        assertThat(result.get(0).balance()).isSameAs(내_잔액);
        assertThat(result.get(1).balance()).isSameAs(다른_잔액);
        assertThat(result.get(1).period()).isEqualTo(PERIODS.period(다른_직원.getHireDate(), 다른_기간, policy));
    }

    @Test
    void 퇴사자를_포함하면_관리_전용_계정만_빼고_모든_직원을_본다() {
        Employee 관리자 = 직원(1L, "관리자", null);
        ReflectionTestUtils.setField(관리자, "systemAccount", true);
        when(employeeRepository.findAll()).thenReturn(List.of(직원, 관리자));
        when(balanceRepository.findByYearIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(new LeaveBalance(10L, 지금_기간), new LeaveBalance(1L, LocalDate.now().getYear())));

        List<LeaveBalanceService.PeriodBalance> result = service.balancesAsOf(LocalDate.now(), false);

        assertThat(result).extracting(pb -> pb.employee().getName()).containsExactly("홍길동");
        verify(employeeRepository, never()).findByStatusAndSystemAccountFalse(eq(EmployeeStatus.ACTIVE));
    }

    @Test
    void 직원이_없으면_잔액을_조회하지_않고_빈_목록이다() {
        when(employeeRepository.findByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.balancesAsOf(LocalDate.now(), true)).isEmpty();
        verify(balanceRepository, never()).findByYearIn(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void 잔액을_찾기만_하고_없으면_만들지_않는다() {
        assertThat(service.find(10L, 지금_기간 + 1)).isEmpty();
        verify(balanceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // --- helpers ---

    private static Employee 직원(Long id, String name, LocalDate hireDate) {
        Employee e = Employee.builder().email("e" + id + "@company.com").passwordHash("h").name(name)
                .hireDate(hireDate).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private LeaveBalance 잔액(int year, String granted, String used) {
        LeaveBalance b = new LeaveBalance(10L, year);
        b.setGranted(new BigDecimal(granted));
        b.addUsed(new BigDecimal(used));
        balances.put(year, b);
        return b;
    }
}
