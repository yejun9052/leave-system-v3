package com.company.leave.leave.accrual;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LeaveAccrualCalculatorTest {

    private final LeaveAccrualCalculator calc = new LeaveAccrualCalculator();

    private LeavePolicy hireDatePolicy() {
        return LeavePolicy.createDefault(); // 기본이 HIRE_DATE
    }

    private LeavePolicy fiscalPolicy() {
        LeavePolicy p = LeavePolicy.createDefault();
        p.apply(new LeavePolicy.Settings(
                GrantBasis.FISCAL_YEAR, 1, 1,
                BigDecimal.valueOf(15), 2, BigDecimal.ONE, BigDecimal.valueOf(25),
                true, 11,
                false, true, false, true, 0, 0, 0,
                true, false, BigDecimal.ZERO));
        return p;
    }

    @Nested
    @DisplayName("입사일 기준")
    class HireDateBasis {

        @Test
        @DisplayName("입사 1년 미만은 개근 개월수만큼(최대 11일) 부여")
        void underOneYear() {
            LocalDate hire = LocalDate.of(2025, 1, 1);
            assertThat(calc.annualEntitlement(hire, LocalDate.of(2025, 7, 1), hireDatePolicy()))
                    .isEqualByComparingTo("6");
            assertThat(calc.annualEntitlement(hire, LocalDate.of(2025, 12, 1), hireDatePolicy()))
                    .isEqualByComparingTo("11");
        }

        @Test
        @DisplayName("입사 1년 시점은 15일")
        void oneYear() {
            LocalDate hire = LocalDate.of(2025, 1, 1);
            assertThat(calc.annualEntitlement(hire, LocalDate.of(2026, 1, 1), hireDatePolicy()))
                    .isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("3년차 16일, 5년차 17일 (2년마다 1일 가산)")
        void seniorityBonus() {
            assertThat(calc.annualEntitlement(
                    LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), hireDatePolicy()))
                    .isEqualByComparingTo("16");
            assertThat(calc.annualEntitlement(
                    LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1), hireDatePolicy()))
                    .isEqualByComparingTo("17");
        }

        @Test
        @DisplayName("장기근속은 최대 25일로 제한")
        void cappedAt25() {
            assertThat(calc.annualEntitlement(
                    LocalDate.of(2000, 1, 1), LocalDate.of(2026, 1, 1), hireDatePolicy()))
                    .isEqualByComparingTo("25");
        }
    }

    @Nested
    @DisplayName("회계연도 기준")
    class FiscalBasis {

        @Test
        @DisplayName("연도 중 입사자는 잔여 기간 비례 부여")
        void proportionalFirstYear() {
            LocalDate hire = LocalDate.of(2026, 7, 1);
            BigDecimal result = calc.annualEntitlement(hire, LocalDate.of(2026, 7, 6), fiscalPolicy());
            // 15 * 184/365 ≈ 7.6
            assertThat(result).isEqualByComparingTo("7.6");
        }

        @Test
        @DisplayName("회계연도 시작 이전 입사자는 근속 기준 부여")
        void fullYearAfterFirst() {
            LocalDate hire = LocalDate.of(2024, 3, 1);
            BigDecimal result = calc.annualEntitlement(hire, LocalDate.of(2026, 6, 1), fiscalPolicy());
            assertThat(result).isEqualByComparingTo("15");
        }
    }
}
