package com.company.leave.leave.accrual;

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

    /**
     * @param start    시작일
     * @param end      종료일 (포함)
     * @param type     휴가 종류
     * @param holidays 제외할 공휴일 집합
     * @return 차감 일수
     */
    public BigDecimal computeLeaveDays(LocalDate start, LocalDate end, LeaveType type,
                                       Set<LocalDate> holidays) {
        // 반차는 0.5일, 그 외에는 주말/공휴일을 제외한 실제 근무일 수.
        // (연차 잔액 차감 여부는 LeaveType.deductFromAnnual 로 별도 판단하므로,
        //  경조사/병가/공가처럼 잔액을 차감하지 않는 휴가도 실제 일수로 기록된다.)
        if (type.isHalfDay()) {
            return new BigDecimal("0.5");
        }
        int workdays = countWorkdays(start, end, holidays);
        return BigDecimal.valueOf(workdays);
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

    public boolean isWorkday(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}
