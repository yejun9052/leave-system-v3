package com.company.leave.leave.accrual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 휴가 일수·차감액 계산. 휴가 신청(LeaveRequestService)과 공휴일 반영 재계산(HolidayImpactService)이 함께 쓴다.
 * 기준 주: 2027-05-03(월) ~ 2027-05-09(일).
 */
@DisplayName("근무일·차감 계산")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WorkdayCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2027, 5, 3);
    private static final LocalDate WED = LocalDate.of(2027, 5, 5);
    private static final LocalDate SUN = LocalDate.of(2027, 5, 9);

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 오전반차 = new LeaveType("HALF_AM", "오전 반차", new BigDecimal("0.5"), true, true, true, "#000", 2);
    private final LeaveType 병가 = new LeaveType("SICK", "병가", new BigDecimal("0.0"), false, false, false, "#000", 5);

    private final WorkdayCalculator calculator = new WorkdayCalculator();

    @Test
    void 주말은_근무일이_아니다() {
        assertThat(calculator.countWorkdays(MON, SUN, Set.of())).isEqualTo(5);
    }

    @Test
    void 공휴일은_근무일에서_뺀다() {
        assertThat(calculator.countWorkdays(MON, SUN, Set.of(MON, WED))).isEqualTo(3);
        assertThat(calculator.isWorkday(WED, Set.of(WED))).isFalse();
        assertThat(calculator.isWorkday(WED, Set.of())).isTrue();
    }

    @Test
    void 종일_휴가_일수는_주말과_공휴일을_뺀_근무일_수다() {
        assertThat(calculator.computeLeaveDays(MON, SUN, 연차, Set.of(WED))).isEqualByComparingTo("4");
    }

    @Test
    void 반차_일수는_날짜와_관계없이_0_5일이다() {
        // 근무일 여부는 countWorkdays/isWorkday 로 따로 판단해야 한다
        assertThat(calculator.computeLeaveDays(WED, WED, 오전반차, Set.of(WED))).isEqualByComparingTo("0.5");
    }

    @Test
    void 종일_휴가_차감액은_일수_곱하기_유형_차감값이다() {
        assertThat(calculator.deductionFor(연차, new BigDecimal("3"))).isEqualByComparingTo("3");

        LeaveType 절반_차감 = new LeaveType("HALF_RATE", "절반 차감", new BigDecimal("0.5"), true, false, true, "#000", 9);
        assertThat(calculator.deductionFor(절반_차감, new BigDecimal("4"))).isEqualByComparingTo("2");
    }

    @Test
    void 반차_차감액은_일수와_관계없이_유형_차감값이다() {
        assertThat(calculator.deductionFor(오전반차, new BigDecimal("0.5"))).isEqualByComparingTo("0.5");
    }

    @Test
    void 시간차는_시간마다_0_125일이고_시간_수가_없으면_계산할_수_없다() {
        LeaveType 시간차 = new LeaveType("HOURLY", "시간차", new BigDecimal("0.125"), true, DayPortion.HOURLY,
                true, false, "#000", 3);

        BigDecimal days = calculator.computeLeaveDays(WED, WED, 시간차, Set.of(), 3);

        assertThat(days).isEqualByComparingTo("0.375");
        assertThat(calculator.deductionFor(시간차, days)).isEqualByComparingTo("0.375");
        assertThat(WorkdayCalculator.hoursOf(days)).isEqualTo(3);
        assertThatThrownBy(() -> calculator.computeLeaveDays(WED, WED, 시간차, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 연차를_차감하지_않는_유형은_차감액이_0이다() {
        assertThat(calculator.deductionFor(병가, new BigDecimal("3"))).isEqualByComparingTo("0");
    }
}
