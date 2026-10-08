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
            double usageRate,
            List<MonthlyUsage> monthlyUsage,
            List<DepartmentUsage> departmentUsage) {
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
