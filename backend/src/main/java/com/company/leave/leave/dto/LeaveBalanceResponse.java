package com.company.leave.leave.dto;

import com.company.leave.leave.domain.LeaveBalance;
import java.math.BigDecimal;

public record LeaveBalanceResponse(
        int year,
        BigDecimal granted,
        BigDecimal used,
        BigDecimal pending,
        BigDecimal carriedOver,
        BigDecimal expired,
        BigDecimal remaining) {

    public static LeaveBalanceResponse of(LeaveBalance b, BigDecimal pending) {
        return new LeaveBalanceResponse(
                b.getYear(), b.getGranted(), b.getUsed(), pending,
                b.getCarriedOver(), b.getExpired(), b.remaining());
    }
}
