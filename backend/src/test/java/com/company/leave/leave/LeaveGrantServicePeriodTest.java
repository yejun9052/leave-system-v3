package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
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
import org.springframework.transaction.PlatformTransactionManager;

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
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private LeaveGrantService service;
    private final LeavePolicy policy = LeavePolicy.createDefault();
    private final Map<Integer, LeaveBalance> balances = new TreeMap<>();
    private Employee 직원;
    private int 지금_기간;

    @BeforeEach
    void setUp() {
        service = new LeaveGrantService(employeeRepository, balanceRepository, balanceService, PERIODS,
                policyService, requestRepository, transactionManager);
        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").hireDate(입사).build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        지금_기간 = PERIODS.currentYear(입사, policy);

        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(직원));
        lenient().when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE))
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
    void 기념일_전_날짜로_결재_대기_중인_신청의_몫은_소멸하지_않고_남겨_둔다() {
        // 3/13 연차를 신청해 결재 대기인 채로 3/14 새 기간이 시작됨
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "13");
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간 - 1)).thenReturn(new BigDecimal("1"));

        service.grantCurrentPeriods();

        assertThat(지난.getExpired()).isEqualByComparingTo("2");
        assertThat(지난.remaining()).isEqualByComparingTo("1"); // 기산일 뒤에 승인돼도 잔여가 있다
    }

    @Test
    void 결재_대기가_반려되면_남겨_둔_몫은_다음_배치에서_소멸한다() {
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "13");
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간 - 1)).thenReturn(new BigDecimal("1"));
        service.grantCurrentPeriods();

        when(requestRepository.sumPendingDeductedDays(eq(10L), eq(지금_기간 - 1))).thenReturn(BigDecimal.ZERO);
        service.grantCurrentPeriods();

        assertThat(지난.remaining()).isZero();
        assertThat(지난.getExpired()).isEqualByComparingTo("3");
    }

    @Test
    void 이월을_허용해도_결재_대기_몫은_이월하지_않고_남겼다가_승인되면_이월은_그대로다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("5"));
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "9");
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간 - 1)).thenReturn(new BigDecimal("1"));

        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("5");
        assertThat(지난.getExpired()).isEqualByComparingTo("1"); // 7 중 결재 대기 1 제외 6 → 5 이월, 1 소멸
        assertThat(지난.remaining()).isEqualByComparingTo("6"); // 이월 5 + 결재 대기 1

        지난.addUsed(new BigDecimal("1")); // 기산일 뒤에 승인
        when(requestRepository.sumPendingDeductedDays(eq(10L), eq(지금_기간 - 1))).thenReturn(BigDecimal.ZERO);
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("5");
        assertThat(지난.getExpired()).isEqualByComparingTo("1");
    }

    @Test
    void 이월_뒤_지난_기간에_돌아온_연차는_한도까지_이월되고_나머지는_소멸한다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("5"));
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "13");
        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("3");

        지난.restoreUsed(new BigDecimal("4")); // 기념일 전 휴가가 기산일 뒤에 취소돼 4일이 돌아옴
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("5");
        assertThat(지난.getExpired()).isEqualByComparingTo("2");
    }

    // --- 당겨쓴 연차(마이너스 잔여) 갚기 ---

    @Test
    void 기념일에_지난_기간에_당겨쓴_연차는_새_기간_이월에_음수로_넘어가_잔여가_줄어든다() {
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "18"); // -2

        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-2");
        assertThat(잔액(지금_기간).remaining()).isEqualByComparingTo("14");
        assertThat(지난.remaining()).as("지난 기간 기록은 그대로").isEqualByComparingTo("-2");
        assertThat(지난.getExpired()).isZero();
    }

    @Test
    void 배치를_여러_번_돌려도_당겨쓴_연차는_한_번만_빠진다() {
        사용(지금_기간 - 1, "16", "18");

        service.grantCurrentPeriods();
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-2");
        assertThat(잔액(지금_기간).remaining()).isEqualByComparingTo("14");
    }

    @Test
    void 이월을_끄거나_이월_한도가_작아도_당겨쓴_연차는_그대로_갚는다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("1"));
        사용(지금_기간 - 1, "16", "19"); // -3

        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-3");

        ReflectionTestUtils.setField(policy, "carryOverEnabled", false);
        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-3");
    }

    @Test
    void 마이너스_연차_허용을_꺼도_이미_당겨쓴_연차는_갚는다() {
        ReflectionTestUtils.setField(policy, "allowNegative", false);
        사용(지금_기간 - 1, "16", "18");

        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-2");
    }

    @Test
    void 기산일_뒤_지난_기간_휴가가_취소돼_잔여가_양수가_되면_갚을_것이_없어지고_양수_이월로_돌아간다() {
        ReflectionTestUtils.setField(policy, "carryOverEnabled", true);
        ReflectionTestUtils.setField(policy, "maxCarryOverDays", new BigDecimal("5"));
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "18"); // -2
        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-2");

        지난.restoreUsed(new BigDecimal("3")); // 3일 휴가 취소 → 지난 기간 잔여 +1
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("1");
        assertThat(잔액(지금_기간).remaining()).isEqualByComparingTo("17");
    }

    @Test
    void 지난_기간_휴가가_취소돼_잔여가_0이_되면_이월도_0으로_돌아간다() {
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "18");
        service.grantCurrentPeriods();

        지난.restoreUsed(new BigDecimal("2"));
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isZero();
        assertThat(잔액(지금_기간).remaining()).isEqualByComparingTo("16");
    }

    @Test
    void 결재_대기는_갚을_몫에_넣지_않고_승인된_뒤_다음_배치에서_갚는다() {
        // 잔여 1 + 결재 대기 3: 아직 갚을 것이 없다(대기 몫은 남겨 둔다)
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "15");
        when(requestRepository.sumPendingDeductedDays(10L, 지금_기간 - 1)).thenReturn(new BigDecimal("3"));
        service.grantCurrentPeriods();
        assertThat(잔액(지금_기간).getCarriedOver()).isZero();
        assertThat(지난.remaining()).isEqualByComparingTo("1");

        지난.addUsed(new BigDecimal("3")); // 기산일 뒤에 승인 → 잔여 -2(음수면 결재 대기는 보지 않는다)
        service.grantCurrentPeriods();

        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-2");
    }

    @Test
    void 이월받은_연차를_다_쓰고_더_당겨쓰면_그만큼만_다음_기간에서_갚는다() {
        // 지난 기간도 그 전에 당겨쓴 2일을 갚은 상태(이월 -2)에서 다시 1일을 당겨씀 → 지금 기간 이월 -1
        LeaveBalance 지난 = 사용(지금_기간 - 1, "16", "15");
        지난.setCarriedOver(new BigDecimal("-2"));

        service.grantCurrentPeriods();

        assertThat(지난.remaining()).isEqualByComparingTo("-1");
        assertThat(잔액(지금_기간).getCarriedOver()).isEqualByComparingTo("-1");
    }

    @Test
    void 회계연도_기준에서도_회계_시작일_경계에서_당겨쓴_연차가_넘어간다() {
        ReflectionTestUtils.setField(policy, "grantBasis", com.company.leave.policy.domain.GrantBasis.FISCAL_YEAR);
        int 회계_지금 = PERIODS.currentYear(입사, policy);
        사용(회계_지금 - 1, "15", "17");

        service.grantCurrentPeriods();

        assertThat(잔액(회계_지금).getCarriedOver()).isEqualByComparingTo("-2");
    }

    @Test
    void 한_명이_실패해도_다른_직원은_부여되고_실패를_알린다() {
        Employee 다른 = Employee.builder().email("other@company.com").passwordHash("h").name("김실패").hireDate(입사).build();
        ReflectionTestUtils.setField(다른, "id", 11L);
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(다른, 직원));
        when(balanceService.getOrCreate(eq(11L), anyInt())).thenThrow(new IllegalStateException("잔액 충돌"));

        assertThatThrownBy(() -> service.grantCurrentPeriods())
                .isInstanceOf(BusinessException.class)
                .hasMessage("연차 부여: 1명 부여, 1명 실패(김실패)");

        assertThat(잔액(지금_기간).getGranted()).isEqualByComparingTo("16");
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
