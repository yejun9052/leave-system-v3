package com.company.leave.dashboard;

import com.company.leave.dashboard.dto.DashboardDtos;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveBalanceService balanceService;

    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveRequestRepository requestRepository,
                            LeaveBalanceService balanceService) {
        this.employeeRepository = employeeRepository;
        this.requestRepository = requestRepository;
        this.balanceService = balanceService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.AdminDashboard admin() {
        int year = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();

        long totalEmployees = employeeRepository.countByStatus(EmployeeStatus.ACTIVE);
        int onLeaveToday = requestRepository.findApprovedBetween(today, today).size();
        long pending = requestRepository.countByStatusIn(java.util.EnumSet.of(
                LeaveRequestStatus.PENDING, LeaveRequestStatus.CANCEL_REQUESTED));

        // 직원마다 지금 쓰고 있는 연차 기간(입사일 기준이면 기간이 서로 다르다)
        List<LeaveBalance> balances = balanceService.balancesAsOf(today, true, true).stream()
                .map(LeaveBalanceService.PeriodBalance::balance).toList();
        BigDecimal totalGranted = balances.stream()
                .map(LeaveBalance::getGranted).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUsed = balances.stream()
                .map(LeaveBalance::getUsed).reduce(BigDecimal.ZERO, BigDecimal::add);
        double usageRate = totalGranted.signum() > 0
                ? totalUsed.divide(totalGranted, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

        List<LeaveRequest> approvedThisYear = requestRepository.findApprovedBetween(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        List<DashboardDtos.MonthlyUsage> monthly = monthlyUsage(approvedThisYear);
        List<DashboardDtos.DepartmentUsage> byDept = departmentUsage(approvedThisYear);

        return new DashboardDtos.AdminDashboard(totalEmployees, onLeaveToday, pending,
                totalGranted, totalUsed, round1(usageRate), monthly, byDept);
    }

    @Transactional
    public DashboardDtos.PersonalDashboard personal(Long employeeId) {
        var balance = balanceService.getResponse(employeeId, balanceService.currentYear(employeeId));
        long pendingCount = requestRepository
                .findByEmployeeIdAndStatusOrderByStartDateDesc(employeeId, LeaveRequestStatus.PENDING)
                .size();

        LocalDate today = LocalDate.now();
        List<LeaveRequestDtos.Response> upcoming = requestRepository
                .findByEmployeeIdAndStatusOrderByStartDateDesc(employeeId, LeaveRequestStatus.APPROVED)
                .stream()
                .filter(r -> !r.getEndDate().isBefore(today))
                .sorted((a, b) -> a.getStartDate().compareTo(b.getStartDate()))
                .limit(5)
                .map(LeaveRequestDtos.Response::from)
                .toList();

        Employee me = employeeRepository.findById(employeeId).orElseThrow();
        int teamOnLeave = 0;
        if (me.getDepartmentId() != null) {
            teamOnLeave = (int) requestRepository.findApprovedBetween(today, today).stream()
                    .filter(r -> me.getDepartmentId().equals(r.getEmployee().getDepartmentId()))
                    .filter(r -> !r.getEmployee().getId().equals(employeeId)) // 본인 제외
                    .map(r -> r.getEmployee().getId())
                    .distinct()
                    .count();
        }

        return new DashboardDtos.PersonalDashboard(balance, pendingCount, upcoming, teamOnLeave);
    }

    private List<DashboardDtos.MonthlyUsage> monthlyUsage(List<LeaveRequest> approved) {
        Map<Integer, BigDecimal> map = new TreeMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, BigDecimal.ZERO);
        }
        for (LeaveRequest r : approved) {
            int m = r.getStartDate().getMonthValue();
            map.merge(m, r.getDays(), BigDecimal::add);
        }
        List<DashboardDtos.MonthlyUsage> list = new ArrayList<>();
        map.forEach((m, d) -> list.add(new DashboardDtos.MonthlyUsage(m, d)));
        return list;
    }

    private List<DashboardDtos.DepartmentUsage> departmentUsage(List<LeaveRequest> approved) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (LeaveRequest r : approved) {
            String dept = r.getEmployee().getDepartment() != null
                    ? r.getEmployee().getDepartment().getName() : "미배정";
            map.merge(dept, r.getDays(), BigDecimal::add);
        }
        List<DashboardDtos.DepartmentUsage> list = new ArrayList<>();
        map.forEach((name, d) -> list.add(new DashboardDtos.DepartmentUsage(name, d)));
        list.sort((a, b) -> b.days().compareTo(a.days()));
        return list;
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
