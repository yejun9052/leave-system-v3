package com.company.leave.leave.accrual;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.leave.domain.LeaveType;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 연차 기간(회차) 계산. 입사일 기준이면 입사 기념일 ~ 다음 기념일 전날, 회계연도 기준이면 회계연도.
 */
@DisplayName("연차 기간 계산")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeavePeriodCalculatorTest {

    private final LeavePeriodCalculator periods =
            new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator());
    private final LeavePolicy hireDate = LeavePolicy.createDefault(); // 기본이 입사일 기준

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 오전반차 = new LeaveType("HALF_AM", "오전 반차", new BigDecimal("0.5"), true, true, true, "#000", 2);
    private final LeaveType 경조사 = new LeaveType("FAMILY", "경조사", new BigDecimal("1.0"), true, false, false, "#000", 3);

    @Nested
    @DisplayName("입사일 기준")
    class HireDateBasis {

        private final LocalDate 입사 = LocalDate.of(2021, 7, 5);

        @Test
        void 기간은_입사_기념일부터_다음_기념일_전날까지다() {
            LeavePeriodCalculator.Period p = periods.period(입사, 2026, hireDate);

            assertThat(p.start()).isEqualTo(LocalDate.of(2026, 7, 5));
            assertThat(p.end()).isEqualTo(LocalDate.of(2027, 7, 4));
        }

        @Test
        void 기념일_전날은_앞_기간이고_기념일_당일부터_새_기간이다() {
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 7, 4), hireDate)).isEqualTo(2025);
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 7, 5), hireDate)).isEqualTo(2026);
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 1, 2), hireDate)).isEqualTo(2025);
        }

        @Test
        void 입사_전_날짜는_첫_기간으로_본다() {
            assertThat(periods.yearOf(입사, LocalDate.of(2021, 1, 4), hireDate)).isEqualTo(2021);
        }

        @Test
        void 입사일이_없으면_달력_연도로_본다() {
            assertThat(periods.yearOf(null, LocalDate.of(2026, 3, 2), hireDate)).isEqualTo(2026);
            assertThat(periods.period(null, 2026, hireDate).end()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        void 첫_기간은_오늘까지_쌓인_월차_다음_기간은_기념일_기준_근속_연차다() {
            LocalDate 신입 = LocalDate.now().minusMonths(7);

            assertThat(periods.entitlement(신입, 신입.getYear(), hireDate)).isEqualByComparingTo("7");
            assertThat(periods.entitlement(신입, 신입.getYear() + 1, hireDate)).isEqualByComparingTo("15");
        }

        @Test
        void 근속_가산은_기간_시작일의_근속_연수로_정한다() {
            // 2026-07-05 시작 기간: 근속 5년 → 15 + (5-1)/2 = 17
            assertThat(periods.entitlement(입사, 2026, hireDate)).isEqualByComparingTo("17");
        }
    }

    @Nested
    @DisplayName("2월 29일 입사자")
    class LeapDay {

        private final LocalDate 입사 = LocalDate.of(2020, 2, 29);

        @Test
        void 평년에는_2월_28일부터_새_기간이다() {
            assertThat(periods.startOf(입사, 2026, hireDate)).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(periods.startOf(입사, 2028, hireDate)).isEqualTo(LocalDate.of(2028, 2, 29));
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 2, 27), hireDate)).isEqualTo(2025);
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 2, 28), hireDate)).isEqualTo(2026);
        }

        @Test
        void 평년_기념일에_근속_1년을_채운_것으로_본다() {
            // 2025-02-28 시작 기간: 근속 5년 → 17일 (Period.between 은 4년으로 계산해 16일이 되던 문제)
            assertThat(LeaveAccrualCalculator.completedYears(입사, LocalDate.of(2025, 2, 28))).isEqualTo(5);
            assertThat(periods.entitlement(입사, 2025, hireDate)).isEqualByComparingTo("17");
        }
    }

    @Nested
    @DisplayName("회계연도 기준")
    class FiscalYearBasis {

        @Test
        void 기간은_회계연도와_같다() {
            LeavePolicy fiscal = fiscalPolicy(4, 1);

            assertThat(periods.yearOf(LocalDate.of(2020, 9, 1), LocalDate.of(2027, 3, 31), fiscal)).isEqualTo(2026);
            assertThat(periods.yearOf(LocalDate.of(2020, 9, 1), LocalDate.of(2027, 4, 1), fiscal)).isEqualTo(2027);
            assertThat(periods.period(LocalDate.of(2020, 9, 1), 2026, fiscal).end())
                    .isEqualTo(LocalDate.of(2027, 3, 31));
        }
    }

    @Nested
    @DisplayName("기산일을 걸친 휴가")
    class CrossingAnniversary {

        // 기념일 2027-03-14(일). 3/11(목)~3/16(화) 근무일은 목·금·월·화 4일
        private final LocalDate 입사 = LocalDate.of(2022, 3, 14);
        private final LocalDate 목 = LocalDate.of(2027, 3, 11);
        private final LocalDate 화 = LocalDate.of(2027, 3, 16);

        @Test
        void 기산일부터의_근무일만_다음_기간_몫이다() {
            BigDecimal next = periods.nextPeriodDeduction(입사, 2026, 연차, 목, 화, Set.of(), null, hireDate);

            assertThat(next).isEqualByComparingTo("2"); // 월·화
        }

        @Test
        void 기산일_뒤_공휴일은_다음_기간_몫에서도_빠진다() {
            BigDecimal next = periods.nextPeriodDeduction(입사, 2026, 연차, 목, 화,
                    Set.of(LocalDate.of(2027, 3, 15)), null, hireDate);

            assertThat(next).isEqualByComparingTo("1"); // 화
        }

        @Test
        void 기산일_전에_끝나는_휴가는_다음_기간_몫이_없다() {
            assertThat(periods.nextPeriodDeduction(입사, 2026, 연차, 목, LocalDate.of(2027, 3, 12),
                    Set.of(), null, hireDate)).isZero();
        }

        @Test
        void 연차를_차감하지_않는_종류와_반차는_나누지_않는다() {
            assertThat(periods.nextPeriodDeduction(입사, 2026, 경조사, 목, 화, Set.of(), null, hireDate)).isZero();
            assertThat(periods.nextPeriodDeduction(입사, 2026, 오전반차, 화, 화, Set.of(), null, hireDate)).isZero();
        }
    }

    private static LeavePolicy fiscalPolicy(int month, int day) {
        LeavePolicy p = LeavePolicy.createDefault();
        p.apply(new LeavePolicy.Settings(
                GrantBasis.FISCAL_YEAR, month, day,
                BigDecimal.valueOf(15), 2, BigDecimal.ONE, BigDecimal.valueOf(25),
                true, 11,
                false, true, false, 0, 0, 0,
                true, false, BigDecimal.ZERO, true));
        return p;
    }
}
