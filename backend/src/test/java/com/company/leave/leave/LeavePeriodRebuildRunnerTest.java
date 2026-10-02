package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 연차 기간 전환(V25) 뒤 한 번만 도는 재계산: 작업 선점, 휴가의 기간 배정·기산일 분할 재계산,
 * 잔액 초기화 뒤 승인된 휴가만 다시 차감, 병가·공가 소멸 재반영, 관리 전용 계정을 뺀 직원 재부여.
 * 직원은 2022-03-14 입사(기념일 3/14). 휴가 날짜는 2025~2026 년의 고정 날짜다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 기간 전환 재계산")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeavePeriodRebuildRunnerTest {

    @Mock private LeaveRequestRepository requestRepository;
    @Mock private LeaveBalanceRepository balanceRepository;
    @Mock private LeaveBalanceService balanceService;
    @Mock private LeaveGrantService grantService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private PolicyService policyService;
    @Mock private EntityManager em;
    @Mock private Query claim;

    private LeavePeriodRebuildRunner runner;
    private final Map<Integer, LeaveBalance> balances = new HashMap<>();
    private Employee 직원;
    private Employee 관리자;

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 병가 = new LeaveType("SICK", "병가", new BigDecimal("0.0"), false, false, false, "#000", 5);

    @BeforeEach
    void setUp() {
        runner = new LeavePeriodRebuildRunner(requestRepository, balanceRepository, balanceService, grantService,
                employeeRepository, holidayRepository, policyService,
                new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator()));
        ReflectionTestUtils.setField(runner, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(claim);
        lenient().when(claim.setParameter(anyString(), any())).thenReturn(claim);

        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동")
                .hireDate(LocalDate.of(2022, 3, 14)).build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        관리자 = Employee.builder().email("admin").passwordHash("h").name("관리자").systemAccount(true).build();
        ReflectionTestUtils.setField(관리자, "id", 1L);

        lenient().when(policyService.getActivePolicy()).thenReturn(LeavePolicy.createDefault());
        lenient().when(holidayRepository.findAll()).thenReturn(List.of());
        lenient().when(employeeRepository.findAll()).thenReturn(List.of(직원, 관리자));
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt()))
                .thenAnswer(inv -> balances.computeIfAbsent(inv.<Integer>getArgument(1), y -> new LeaveBalance(10L, y)));
    }

    @Test
    void 이미_완료된_작업이면_아무것도_하지_않는다() {
        when(claim.executeUpdate()).thenReturn(0);

        runner.run(null);

        verify(claim).setParameter("name", LeavePeriodRebuildRunner.TASK);
        verifyNoInteractions(requestRepository, balanceRepository, balanceService, grantService, employeeRepository);
    }

    @Test
    void 휴가의_기간을_입사_기념일_기준으로_다시_정하고_기산일을_걸치면_나눈다() {
        when(claim.executeUpdate()).thenReturn(1);
        // 달력 연도 기준으로 2026 에 들어가 있던 3월 초 휴가 → 2025 기간(2025-03-14 ~ 2026-03-13)
        LeaveRequest 기념일_전 = 휴가(연차, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), "2", 2026,
                LeaveRequestStatus.APPROVED);
        // 3/12(목)~3/16(월) 근무일 3일 중 기념일(3/14 토) 이후 월요일 1일은 2026 기간
        LeaveRequest 걸친_휴가 = 휴가(연차, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 16), "3", 2026,
                LeaveRequestStatus.APPROVED);
        when(requestRepository.findAll()).thenReturn(List.of(기념일_전, 걸친_휴가));
        when(balanceRepository.findAll()).thenReturn(List.of());

        runner.run(null);

        assertThat(기념일_전.getAppliedYear()).isEqualTo(2025);
        assertThat(기념일_전.getNextPeriodDeductedDays()).isZero();
        assertThat(걸친_휴가.getAppliedYear()).isEqualTo(2025);
        assertThat(걸친_휴가.getNextPeriodDeductedDays()).isEqualByComparingTo("1");
        assertThat(balances.get(2025).getUsed()).isEqualByComparingTo("4");
        assertThat(balances.get(2026).getUsed()).isEqualByComparingTo("1");
    }

    @Test
    void 잔액을_비운_뒤_승인과_취소_요청_중인_휴가만_다시_차감한다() {
        when(claim.executeUpdate()).thenReturn(1);
        LeaveBalance 예전_잔액 = new LeaveBalance(10L, 2025);
        예전_잔액.setGranted(new BigDecimal("15"));
        예전_잔액.addUsed(new BigDecimal("10"));
        예전_잔액.setExpired(new BigDecimal("5"));
        예전_잔액.setCarriedOver(new BigDecimal("2"));
        balances.put(2025, 예전_잔액);
        when(balanceRepository.findAll()).thenReturn(List.of(예전_잔액));
        LocalDate 화 = LocalDate.of(2025, 6, 3);
        when(requestRepository.findAll()).thenReturn(List.of(
                휴가(연차, 화, 화, "1", 2025, LeaveRequestStatus.APPROVED),
                휴가(연차, 화.plusDays(1), 화.plusDays(1), "1", 2025, LeaveRequestStatus.CANCEL_REQUESTED),
                휴가(연차, 화.plusDays(2), 화.plusDays(2), "1", 2025, LeaveRequestStatus.PENDING),
                휴가(연차, 화.plusDays(7), 화.plusDays(7), "1", 2025, LeaveRequestStatus.REJECTED),
                휴가(연차, 화.plusDays(8), 화.plusDays(8), "1", 2025, LeaveRequestStatus.CANCELLED)));

        runner.run(null);

        assertThat(예전_잔액.getUsed()).isEqualByComparingTo("2");
        assertThat(예전_잔액.getExpired()).isZero();
        assertThat(예전_잔액.getCarriedOver()).isZero();
        assertThat(예전_잔액.getGranted()).isEqualByComparingTo("15"); // 부여 일수는 재부여(grantService)가 맞춘다
    }

    @Test
    void 병가_공가로_소멸시킨_연차를_그_기간에_다시_소멸한다() {
        when(claim.executeUpdate()).thenReturn(1);
        LeaveRequest 승인된_병가 = 휴가(병가, LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 3), "0", 2025,
                LeaveRequestStatus.APPROVED);
        승인된_병가.recordForfeit(new BigDecimal("0.5"));
        LeaveRequest 취소된_병가 = 휴가(병가, LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 10), "0", 2025,
                LeaveRequestStatus.CANCELLED);
        취소된_병가.recordForfeit(new BigDecimal("0.25"));
        when(requestRepository.findAll()).thenReturn(List.of(승인된_병가, 취소된_병가));
        when(balanceRepository.findAll()).thenReturn(List.of());

        runner.run(null);

        assertThat(balances.get(2025).getExpired()).isEqualByComparingTo("0.5");
        assertThat(balances.get(2025).getUsed()).isZero();
    }

    @Test
    void 관리_전용_계정을_뺀_모든_직원의_기간을_다시_부여한다() {
        when(claim.executeUpdate()).thenReturn(1);
        when(requestRepository.findAll()).thenReturn(List.of());
        when(balanceRepository.findAll()).thenReturn(List.of());

        runner.run(null);

        verify(grantService).regrantAllPeriods(직원);
        verify(grantService, never()).regrantAllPeriods(관리자);
    }

    private LeaveRequest 휴가(LeaveType type, LocalDate start, LocalDate end, String deducted, int appliedYear,
                          LeaveRequestStatus status) {
        LeaveRequest r = new LeaveRequest(직원, type, start, end, new BigDecimal(deducted), new BigDecimal(deducted),
                appliedYear, "사유");
        switch (status) {
            case APPROVED -> r.approve(직원, Instant.now());
            case CANCEL_REQUESTED -> {
                r.approve(직원, Instant.now());
                r.requestCancel("취소");
            }
            case REJECTED -> r.reject(직원, "반려", Instant.now());
            case CANCELLED -> r.cancel();
            default -> {
            }
        }
        return r;
    }
}
