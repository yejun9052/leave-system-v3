package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
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
 * 연차 부여 배치와 입사일 기준 기간. 입사 기념일이 지나 새 기간이 시작되면 지난 기간 잔여가 이월·소멸된다.
 * 직원은 4년 10일 전에 입사해 지금 기간이 열흘 전에 시작됐다(근속 4년 → 16일).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 부여와 입사일 기준 기간")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveGrantServicePeriodTest {

    private static final LocalDate 입사 = LocalDate.now().minusYears(4).minusDays(10);
    private static final LeavePeriodCalculator PERIODS =
            new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator());

    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveBalanceRepository balanceRepository;
    @Mock private LeaveBalanceService balanceService;
    @Mock private PolicyService policyService;
    @Mock private ServiceAwardRuleRepository awardRuleRepository;

    private LeaveGrantService service;
    private final LeavePolicy policy = LeavePolicy.createDefault();
    private final Map<Integer, LeaveBalance> balances = new TreeMap<>();
    private Employee 직원;
    private int 지금_기간;

    @BeforeEach
    void setUp() {
        service = new LeaveGrantService(employeeRepository, balanceRepository, balanceService, PERIODS,
                policyService, awardRuleRepository);
        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").hireDate(입사).build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        지금_기간 = PERIODS.currentYear(입사, policy);

        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(직원));
        lenient().when(employeeRepository.findByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE))
                .thenReturn(List.of(직원));
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt()))
                .thenAnswer(inv -> 잔액(inv.<Integer>getArgument(1)));
        lenient().when(balanceRepository.findByEmployeeIdAndYear(anyLong(), anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(balances.get(inv.<Integer>getArgument(1))));
        lenient().when(balanceRepository.findByEmployeeIdOrderByYearDesc(anyLong()))
                .thenAnswer(inv -> balances.values().stream()
                        .sorted(Comparator.comparingInt(LeaveBalance::getYear).reversed()).toList());
    }

    @Test
    void 매일_배치는_지금_기간을_입사_기념일_기준_근속으로_부여한다() {
        service.grantCurrentPeriods();

        assertThat(지금_기간).isEqualTo(입사.getYear() + 4);
        assertThat(잔액(지금_기간).getGranted()).isEqualByComparingTo("16");
    }

    @Test
    void 기념일이_지나_새_기간이_시작되면_지난_기간_잔여가_소멸한다() {
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "10");

        service.grantCurrentPeriods();

        assertThat(지난.getExpired()).isEqualByComparingTo("6");
        assertThat(지난.remaining()).isZero();
        assertThat(잔액(지금_기간).getCarriedOver()).isZero();
    }

    @Test
    void 이월을_허용하면_한도만큼_새_기간으로_넘어가고_나머지는_소멸한다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("5"));
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "9");

        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("5");
        assertThat(지난.getExpired()).isEqualByComparingTo("2");
    }

    @Test
    void 배치를_다시_돌려도_이월과_소멸이_두_번_되지_않는다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("5"));
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "9");

        service.grantCurrentPeriods();
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("5");
        assertThat(지난.getExpired()).isEqualByComparingTo("2");
    }

    @Test
    void 그보다_오래된_기간에_남은_연차도_소멸한다() {
        LeaveBalance 오래된 = 사용(지금_기간 - 3, "15", "3");

        service.grantCurrentPeriods();

        assertThat(오래된.getExpired()).isEqualByComparingTo("12");
    }

    @Test
    void 지난_휴가가_취소돼_돌아온_연차는_다음_배치에서_다시_소멸한다() {
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "16");
        service.grantCurrentPeriods();

        지난.restoreUsed(new BigDecimal("2")); // 지난 기간 휴가 취소
        service.grantCurrentPeriods();

        assertThat(지난.remaining()).isZero();
        assertThat(지난.getExpired()).isEqualByComparingTo("2");
    }

    @Test
    void 아직_시작하지_않은_기간은_일괄_부여하지_않는다() {
        LeaveBalance 지금 = 사용(지금_기간, "16", "3");

        assertThat(service.grantAll(지금_기간 + 1)).isZero();

        assertThat(balances).doesNotContainKey(지금_기간 + 1);
        assertThat(지금.getExpired()).isZero();
    }

    @Test
    void 특정_직원도_아직_시작하지_않은_기간은_부여할_수_없다() {
        사용(지금_기간, "16", "3");

        assertThatThrownBy(() -> service.grantForEmployee(10L, 지금_기간 + 1))
                .isInstanceOf(BusinessException.class);

        assertThat(잔액(지금_기간).getExpired()).isZero();
    }

    @Test
    void 미리_예약된_사용분은_그_기간이_시작돼_부여돼도_그대로_남는다() {
        사용(지금_기간, "0", "2"); // 기산일 전에 미리 승인된 이번 기간 휴가

        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getGranted()).isEqualByComparingTo("16");
        assertThat(잔액(지금_기간).getUsed()).isEqualByComparingTo("2");
    }

    private LeaveBalance 잔액(int year) {
        return balances.computeIfAbsent(year, y -> new LeaveBalance(10L, y));
    }

    private LeaveBalance 사용(int year, String granted, String used) {
        LeaveBalance b = 잔액(year);
        b.setGranted(new BigDecimal(granted));
        b.addUsed(new BigDecimal(used));
        return b;
    }
}
