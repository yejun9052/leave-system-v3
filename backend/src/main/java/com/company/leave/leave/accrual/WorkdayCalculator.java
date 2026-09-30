package com.company.leave.leave.accrual;

import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 휴가 기간의 실제 차감 일수 계산 (주말/공휴일 제외).
 */
@Component
public class WorkdayCalculator {

    /** 종일·반차·반반차용. 시간차는 시간 수가 필요하므로 {@link #computeLeaveDays(LocalDate, LocalDate, LeaveType, Set, Integer)}. */
    public BigDecimal computeLeaveDays(LocalDate start, LocalDate end, LeaveType type,
                                       Set<LocalDate> holidays) {
        return computeLeaveDays(start, end, type, holidays, null);
    }

    /**
     * @param start    시작일
     * @param end      종료일 (포함)
     * @param type     휴가 종류
     * @param holidays 제외할 공휴일 집합
     * @param hours    시간차의 시간 수(1~{@value DayPortion#MAX_HOURLY_HOURS}). 다른 단위는 무시
     * @return 기록 일수. 종일=근무일 수, 반차 0.5, 반반차 0.25, 시간차 시간×0.125
     */
    public BigDecimal computeLeaveDays(LocalDate start, LocalDate end, LeaveType type,
                                       Set<LocalDate> holidays, Integer hours) {
        // 부분 휴가는 날짜와 관계없이 단위 일수(근무일 여부는 countWorkdays/isWorkday 로 따로 판단).
        // 연차 잔액 차감 여부는 LeaveType.deductFromAnnual 로 별도 판단하므로,
        // 경조사/병가/공가처럼 잔액을 차감하지 않는 휴가도 실제 일수로 기록된다.
        DayPortion portion = type.getPortion();
        return switch (portion) {
            case FULL -> BigDecimal.valueOf(countWorkdays(start, end, holidays));
            case HALF, QUARTER -> portion.unitDays();
            case HOURLY -> {
                if (hours == null || hours < 1) {
                    throw new IllegalArgumentException("시간차는 시간 수가 필요합니다.");
                }
                yield portion.unitDays().multiply(BigDecimal.valueOf(hours));
            }
        };
    }

    /** 기간 내 실제 근무일 수 (반차 여부와 무관, 겹침/경고 판단용). */
    public int countWorkdays(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        int workdays = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWorkday(d, holidays)) {
                workdays++;
            }
        }
        return workdays;
    }

    /**
     * 실제 잔액 차감액: 종일=근무일수×deductDays, 반차·반반차=deductDays, 시간차=시간×deductDays,
     * 비차감 유형=0. 휴가 신청(LeaveRequestService)과 공휴일 반영 재계산(HolidayImpactService)이 같은 규칙을 쓰도록 여기에 둔다.
     *
     * @param durationDays computeLeaveDays 결과(기록 일수)
     */
    public BigDecimal deductionFor(LeaveType type, BigDecimal durationDays) {
        if (!type.isDeductFromAnnual()) {
            return BigDecimal.ZERO;
        }
        return switch (type.getPortion()) {
            case FULL -> durationDays.multiply(type.getDeductDays());
            case HALF, QUARTER -> type.getDeductDays();
            case HOURLY -> BigDecimal.valueOf(hoursOf(durationDays)).multiply(type.getDeductDays());
        };
    }

    /** 시간차 기록 일수 → 시간 수(0.375일 → 3시간). */
    public static int hoursOf(BigDecimal hourlyDays) {
        return hourlyDays.multiply(BigDecimal.valueOf(DayPortion.HOURS_PER_DAY)).intValue();
    }

    public boolean isWorkday(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}
