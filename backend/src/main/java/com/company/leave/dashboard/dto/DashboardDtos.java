package com.company.leave.dashboard.dto;

import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.domain.DayPortion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record MonthlyUsage(int month, BigDecimal days) {
    }

    public record DepartmentUsage(String departmentName, BigDecimal days) {
    }

    public record AdminDashboard(
            long totalEmployees,
            int onLeaveToday,
            long pendingApprovals,
            BigDecimal totalGranted,
            BigDecimal totalUsed,
            /** 지난 기간에서 넘어온 연차 합계. 사용률 분모 = 부여 + 이월 */
            BigDecimal totalCarriedOver,
            double usageRate,
            List<MonthlyUsage> monthlyUsage,
            List<DepartmentUsage> departmentUsage) {
    }

    /**
     * 리포트 › 회사 현황의 직원별 사용: 직원마다 지금 쓰고 있는 연차 기간의 잔액(관리자 대시보드 사용률과 같은 기준).
     *
     * @param usageRate 사용 ÷ (부여 + 이월) × 100, 소수 첫째 자리. 쓸 수 있던 연차가 없으면 0
     */
    public record EmployeeUsage(
            Long employeeId,
            String name,
            String departmentName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal granted,
            BigDecimal carriedOver,
            BigDecimal used,
            BigDecimal remaining,
            double usageRate) {
    }

    public record PersonalDashboard(
            LeaveBalanceResponse balance,
            long pendingCount,
            List<LeaveRequestDtos.Response> upcoming,
            int teamOnLeaveToday,
            List<TeamLeave> teamLeaves) {
    }

    /**
     * 팀 일정 카드: 이번 주(월~일)와 겹치는 같은 부서 동료(본인 제외)의 승인된 휴가. 사유는 넣지 않는다.
     *
     * @param portion 종일·반차·시간차
     */
    public record TeamLeave(Long employeeId, String employeeName, String leaveTypeName, DayPortion portion,
                            LocalDate startDate, LocalDate endDate) {
    }
}
