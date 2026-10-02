package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
 * 휴가 한 건의 차감·환원을 연차 기간별로 나눠 반영하는지(시작일 기간 몫 + 다음 기간 몫).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 차감·환원의 기간별 반영")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveChargesTest {

    @Mock private LeaveBalanceService balanceService;

    private final Map<Integer, LeaveBalance> balances = new HashMap<>();
    private Employee 직원;

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 경조사 = new LeaveType("FAMILY", "경조사", new BigDecimal("1.0"), true, false, false, "#000", 2);

    @BeforeEach
    void setUp() {
        직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt()))
                .thenAnswer(inv -> balances.computeIfAbsent(inv.<Integer>getArgument(1), y -> new LeaveBalance(10L, y)));
    }

    @Test
    void 기산일을_걸친_휴가는_시작일_기간과_다음_기간에서_나눠_뺀다() {
        LeaveRequest r = 휴가(연차, "3", 2026, "2");

        LeaveCharges.charge(balanceService, r);

        assertThat(balances.get(2026).getUsed()).isEqualByComparingTo("1");
        assertThat(balances.get(2027).getUsed()).isEqualByComparingTo("2");
    }

    @Test
    void 다음_기간_몫이_없으면_다음_기간_잔액은_건드리지_않는다() {
        LeaveRequest r = 휴가(연차, "2", 2026, "0");

        LeaveCharges.charge(balanceService, r);

        assertThat(balances.get(2026).getUsed()).isEqualByComparingTo("2");
        assertThat(balances).doesNotContainKey(2027);
        verify(balanceService, never()).getOrCreate(10L, 2027);
    }

    @Test
    void 연차를_차감하지_않는_종류는_차감도_환원도_하지_않는다() {
        LeaveRequest r = 휴가(경조사, "0", 2026, "0");

        LeaveCharges.charge(balanceService, r);
        LeaveCharges.restore(balanceService, r);

        verifyNoInteractions(balanceService);
    }

    @Test
    void 취소하면_나눠_뺀_만큼_각_기간에_모두_돌려준다() {
        LeaveRequest r = 휴가(연차, "3", 2026, "2");
        LeaveCharges.charge(balanceService, r);

        LeaveCharges.restore(balanceService, r);

        assertThat(balances.get(2026).getUsed()).isZero();
        assertThat(balances.get(2027).getUsed()).isZero();
    }

    @Test
    void 일부_환원은_주어진_기간별_양만큼만_돌려준다() {
        LeaveRequest r = 휴가(연차, "3", 2026, "2");
        LeaveCharges.charge(balanceService, r);

        LeaveCharges.restore(balanceService, r, BigDecimal.ZERO, BigDecimal.ONE);

        assertThat(balances.get(2026).getUsed()).isEqualByComparingTo("1");
        assertThat(balances.get(2027).getUsed()).isEqualByComparingTo("1");
    }

    @Test
    void 환원할_양이_0이면_그_기간_잔액은_조회하지_않는다() {
        LeaveRequest r = 휴가(연차, "3", 2026, "2");

        LeaveCharges.restore(balanceService, r, BigDecimal.ONE, BigDecimal.ZERO);

        verify(balanceService).getOrCreate(10L, 2026);
        verify(balanceService, never()).getOrCreate(10L, 2027);
    }

    private LeaveRequest 휴가(LeaveType type, String deducted, int year, String nextPart) {
        LeaveRequest r = new LeaveRequest(직원, type, LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 16),
                new BigDecimal(deducted), new BigDecimal(deducted), year, "사유");
        r.assignPeriods(year, new BigDecimal(nextPart));
        return r;
    }
}
