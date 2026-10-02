package com.company.leave.leave;

import com.company.leave.leave.domain.LeaveRequest;
import java.math.BigDecimal;

/**
 * 휴가 한 건의 연차 차감·환원을 연차 기간별로 나눠 잔액에 반영한다.
 * 시작일이 속한 기간(applied_year)에서 그 몫을, 기산일을 걸친 몫은 다음 기간(applied_year + 1)에서 뺀다.
 * 승인·강제 등록·취소·공휴일 재계산이 모두 이 규칙을 쓴다.
 */
final class LeaveCharges {

    private LeaveCharges() {
    }

    /** 승인(강제 등록) 때 차감. 연차를 차감하지 않는 종류는 하지 않는다. */
    static void charge(LeaveBalanceService balances, LeaveRequest request) {
        if (!request.getLeaveType().isDeductFromAnnual()) {
            return;
        }
        Long employeeId = request.getEmployee().getId();
        balances.getOrCreate(employeeId, request.getAppliedYear()).addUsed(request.getCurrentPeriodDeductedDays());
        if (request.getNextPeriodDeductedDays().signum() > 0) {
            balances.getOrCreate(employeeId, request.getAppliedYear() + 1).addUsed(request.getNextPeriodDeductedDays());
        }
    }

    /** 승인 때 뺀 것 전부 환원(취소). */
    static void restore(LeaveBalanceService balances, LeaveRequest request) {
        if (!request.getLeaveType().isDeductFromAnnual()) {
            return;
        }
        restore(balances, request, request.getCurrentPeriodDeductedDays(), request.getNextPeriodDeductedDays());
    }

    /** 기간별로 주어진 양만큼 환원(공휴일 지정으로 차감이 줄었을 때). */
    static void restore(LeaveBalanceService balances, LeaveRequest request, BigDecimal current, BigDecimal next) {
        Long employeeId = request.getEmployee().getId();
        if (current.signum() > 0) {
            balances.getOrCreate(employeeId, request.getAppliedYear()).restoreUsed(current);
        }
        if (next.signum() > 0) {
            balances.getOrCreate(employeeId, request.getAppliedYear() + 1).restoreUsed(next);
        }
    }
}
