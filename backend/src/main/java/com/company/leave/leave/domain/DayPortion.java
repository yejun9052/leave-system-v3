package com.company.leave.leave.domain;

import java.math.BigDecimal;

/**
 * 휴가 단위. 하루 8시간 기준.
 * <ul>
 *   <li>FULL: 종일(기간의 근무일 수)</li>
 *   <li>HALF: 반차 0.5일(4시간)</li>
 *   <li>QUARTER: 반반차 0.25일(2시간). 시간차로 대체되어 신청 불가. 과거 신청 기록 표시·계산용으로만 남긴다</li>
 *   <li>HOURLY: 시간차 1시간 = 0.125일, 신청 때 시간 수 선택</li>
 * </ul>
 * FULL 외에는 하루만 신청하며, 같은 날 부분 휴가 합계는 1일을 넘을 수 없다.
 */
public enum DayPortion {
    FULL(null),
    HALF(new BigDecimal("0.5")),
    QUARTER(new BigDecimal("0.25")),
    HOURLY(new BigDecimal("0.125"));

    /** 하루 근무 시간. */
    public static final int HOURS_PER_DAY = 8;
    /** 시간차 한 건의 최대 시간(4시간부터는 반차). */
    public static final int MAX_HOURLY_HOURS = 3;

    /** 부분 휴가 1단위의 일수(HOURLY 는 1시간당). FULL 은 null. */
    private final BigDecimal unitDays;

    DayPortion(BigDecimal unitDays) {
        this.unitDays = unitDays;
    }

    public boolean isPartial() {
        return this != FULL;
    }

    public BigDecimal unitDays() {
        return unitDays;
    }
}
