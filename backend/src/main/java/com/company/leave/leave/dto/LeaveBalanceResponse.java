package com.company.leave.leave.dto;

import com.company.leave.leave.domain.LeaveBalance;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 연차 기간 하나의 잔액.
 *
 * @param year               그 해에 시작한 연차 기간
 * @param periodStart        기간 첫날(입사일 기준이면 입사 기념일)
 * @param periodEnd          기간 마지막 날(사용 기한)
 * @param nextPeriodReserved 다음 기간에서 뺄 예약분(승인 + 결재 대기). 지금 기간에만 채운다
 */
public record LeaveBalanceResponse(
        int year,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal granted,
        BigDecimal used,
        BigDecimal pending,
        BigDecimal carriedOver,
        BigDecimal expired,
        BigDecimal remaining,
        BigDecimal nextPeriodReserved) {

    public static LeaveBalanceResponse of(LeaveBalance b, LocalDate periodStart, LocalDate periodEnd,
                                          BigDecimal pending, BigDecimal nextPeriodReserved) {
        return new LeaveBalanceResponse(
                b.getYear(), periodStart, periodEnd, b.getGranted(), b.getUsed(), pending,
                b.getCarriedOver(), b.getExpired(), b.remaining(), nextPeriodReserved);
    }
}
