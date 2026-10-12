package com.company.leave.leave.accrual;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 근속 가산 연차(정책: N년마다 +M일, 최대 일수)와 장기근속 포상(근속 N년이 되는 기간에 한 번).
 * 연차 기간은 입사일 기준이고, 근속 연수는 기간 시작일(입사 기념일)에 센다. 2월 29일 입사자는 평년 2월 28일이 기념일.
 */
@DisplayName("근속 가산·장기근속 포상")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SeniorityAndServiceAwardTest {

    private final LeaveAccrualCalculator accrual = new LeaveAccrualCalculator();

    /** 기본 15일, step 년마다 +increment 일, 최대 max 일(입사일 기준). */
    private static LeavePolicy 정책(int step, String increment, String max) {
        LeavePolicy p = LeavePolicy.createDefault();
        p.apply(new LeavePolicy.Settings(
                GrantBasis.HIRE_DATE, 1, 1,
                BigDecimal.valueOf(15), step, new BigDecimal(increment), new BigDecimal(max),
                true, 11,
                false, true, false, 0, 0, 0,
                false, BigDecimal.ZERO, true));
        return p;
    }

    private LeavePeriodCalculator 기간(Map<Integer, String> 포상) {
        return new LeavePeriodCalculator(accrual, new WorkdayCalculator(),
                years -> new BigDecimal(포상.getOrDefault(years, "0")));
    }

    @Nested
    @DisplayName("근속 가산")
    class 근속_가산 {

        private final LocalDate 입사 = LocalDate.of(2010, 3, 2);
        private final LeavePeriodCalculator periods = 기간(Map.of());

        @Test
        void 세_해마다_2일씩_더한다() {
            LeavePolicy p = 정책(3, "2", "30");
            // 기간 시작일 근속: 1~3년 15일, 4~6년 17일, 7~9년 19일
            assertThat(periods.entitlement(입사, 2011, p)).isEqualByComparingTo("15"); // 1년
            assertThat(periods.entitlement(입사, 2013, p)).isEqualByComparingTo("15"); // 3년
            assertThat(periods.entitlement(입사, 2014, p)).isEqualByComparingTo("17"); // 4년
            assertThat(periods.entitlement(입사, 2016, p)).isEqualByComparingTo("17"); // 6년
            assertThat(periods.entitlement(입사, 2017, p)).isEqualByComparingTo("19"); // 7년
        }

        @Test
        void 최대_일수를_넘지_않는다() {
            LeavePolicy p = 정책(1, "1", "20");
            assertThat(periods.entitlement(입사, 2015, p)).isEqualByComparingTo("19"); // 5년: 15 + 4
            assertThat(periods.entitlement(입사, 2016, p)).isEqualByComparingTo("20"); // 6년: 15 + 5
            assertThat(periods.entitlement(입사, 2026, p)).isEqualByComparingTo("20"); // 16년: 상한
        }

        @Test
        void 기념일_전날까지는_앞_기간의_근속으로_센다() {
            LeavePolicy p = 정책(2, "1", "25");
            // 2026-03-01 은 2025 기간(근속 15년, 22일), 2026-03-02 부터 2026 기간(근속 16년, 22일 → 17년부터 23일)
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 3, 1), p)).isEqualTo(2025);
            assertThat(periods.yearOf(입사, LocalDate.of(2026, 3, 2), p)).isEqualTo(2026);
            assertThat(periods.entitlement(입사, 2026, p)).isEqualByComparingTo("22");
            assertThat(periods.entitlement(입사, 2027, p)).isEqualByComparingTo("23");
        }

        @Test
        void 소수_가산도_그대로_더한다() {
            LeavePolicy p = 정책(2, "0.5", "25");
            assertThat(periods.entitlement(입사, 2015, p)).isEqualByComparingTo("16"); // 5년: 15 + 0.5*2
            assertThat(periods.entitlement(입사, 2016, p)).isEqualByComparingTo("16"); // 6년: (6-1)/2 = 2단계
            assertThat(periods.entitlement(입사, 2017, p)).isEqualByComparingTo("16.5"); // 7년: 3단계
        }
    }

    @Nested
    @DisplayName("2월 29일 입사자")
    class 윤일_입사 {

        private final LocalDate 입사 = LocalDate.of(2016, 2, 29);
        private final LeavePolicy p = 정책(2, "1", "25");

        @Test
        void 평년_2월_28일에_근속이_한_해_늘어_가산과_포상이_그날부터_적용된다() {
            LeavePeriodCalculator periods = 기간(Map.of(10, "5"));
            // 2026-02-28(평년)에 근속 10년: 15 + (10-1)/2 = 19, 포상 5 → 24
            assertThat(periods.startOf(입사, 2026, p)).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(LeaveAccrualCalculator.completedYears(입사, LocalDate.of(2026, 2, 28))).isEqualTo(10);
            assertThat(periods.serviceAward(입사, 2026, p)).isEqualByComparingTo("5");
            assertThat(periods.entitlement(입사, 2026, p)).isEqualByComparingTo("24");
            assertThat(periods.entitlement(입사, 2025, p)).isEqualByComparingTo("19"); // 9년, 포상 없음
        }
    }

    @Nested
    @DisplayName("장기근속 포상")
    class 장기근속_포상 {

        private final LocalDate 입사 = LocalDate.of(2016, 7, 1);
        private final LeavePolicy p = 정책(2, "1", "25");
        private final LeavePeriodCalculator periods = 기간(Map.of(5, "3", 10, "5"));

        @Test
        void 근속_N년이_되는_기간에만_한_번_더한다() {
            assertThat(periods.serviceAward(입사, 2020, p)).isZero();               // 4년
            assertThat(periods.serviceAward(입사, 2021, p)).isEqualByComparingTo("3"); // 5년
            assertThat(periods.serviceAward(입사, 2022, p)).isZero();               // 6년
            assertThat(periods.serviceAward(입사, 2026, p)).isEqualByComparingTo("5"); // 10년
            // 부여 일수 = 근속 연차 + 포상: 5년 17 + 3, 10년 19 + 5
            assertThat(periods.entitlement(입사, 2021, p)).isEqualByComparingTo("20");
            assertThat(periods.entitlement(입사, 2026, p)).isEqualByComparingTo("24");
        }

        @Test
        void 포상은_최대_일수와_별개로_더한다() {
            LeavePolicy 상한 = 정책(1, "1", "16");
            assertThat(periods.entitlement(입사, 2026, 상한)).isEqualByComparingTo("21"); // 상한 16 + 포상 5
        }

        @Test
        void 입사_첫_기간에는_포상이_없다() {
            LeavePeriodCalculator 첫해포상 = 기간(Map.of(0, "9", 1, "2"));
            assertThat(첫해포상.serviceAward(입사, 2016, p)).isZero();
            assertThat(첫해포상.serviceAward(입사, 2017, p)).isEqualByComparingTo("2"); // 근속 1년 규칙은 적용
        }

        @Test
        void 규칙이_없으면_포상은_0이다() {
            LeavePeriodCalculator 없음 = new LeavePeriodCalculator(accrual, new WorkdayCalculator());
            assertThat(없음.serviceAward(입사, 2021, p)).isZero();
            assertThat(없음.entitlement(입사, 2021, p)).isEqualByComparingTo("17");
        }
    }
}
