package com.company.leave.dashboard.dto;

import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.dto.LeaveRequestDtos;
import java.math.BigDecimal;
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
            int teamOnLeaveToday) {
    }
}
