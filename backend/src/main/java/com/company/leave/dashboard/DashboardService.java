package com.company.leave.dashboard;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.dashboard.dto.DashboardDtos;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveBalanceService balanceService;
    /** 없으면(단위 테스트) 공휴일 없이 주말만 빼고 나눈다 */
    private final HolidayRepository holidayRepository;
    /** 팀 일정에서 동료의 병가·경조사를 가릴지 판단({@link LeaveRequestService#leaveTypeVisibility}) */
    private final LeaveRequestService leaveRequestService;

    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveRequestRepository requestRepository,
                            LeaveBalanceService balanceService,
                            LeaveRequestService leaveRequestService) {
        this(employeeRepository, requestRepository, balanceService, null, leaveRequestService);
    }

    @Autowired
    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveRequestRepository requestRepository,
                            LeaveBalanceService balanceService,
                            HolidayRepository holidayRepository,
                            LeaveRequestService leaveRequestService) {
        this.employeeRepository = employeeRepository;
        this.requestRepository = requestRepository;
        this.balanceService = balanceService;
        this.holidayRepository = holidayRepository;
        this.leaveRequestService = leaveRequestService;
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
        BigDecimal totalCarried = balances.stream()
                .map(LeaveBalance::getCarriedOver).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 쓸 수 있던 연차 = 부여 + 이월. 이월로 쓴 날도 사용에 들어가므로 분모에도 넣어야 100%를 넘지 않는다
        BigDecimal usable = totalGranted.add(totalCarried);
        double usageRate = usable.signum() > 0
                ? totalUsed.divide(usable, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<DayShare> shares = dayShares(requestRepository.findApprovedBetween(yearStart, yearEnd), yearStart, yearEnd);

        List<DashboardDtos.MonthlyUsage> monthly = monthlyUsage(shares);
        List<DashboardDtos.DepartmentUsage> byDept = departmentUsage(shares);

        return new DashboardDtos.AdminDashboard(totalEmployees, onLeaveToday, pending,
                totalGranted, totalUsed, totalCarried, round1(usageRate), monthly, byDept);
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

        // 같은 부서 동료(본인 제외)의 이번 주(월~일) 승인된 휴가. 오늘 팀 부재는 그중 오늘 쉬는 사람 수
        Employee me = employeeRepository.findById(employeeId).orElseThrow();
        List<LeaveRequest> teamThisWeek = List.of();
        if (me.getDepartmentId() != null) {
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            teamThisWeek = requestRepository.findApprovedBetween(weekStart, weekStart.plusDays(6)).stream()
                    .filter(r -> me.getDepartmentId().equals(r.getEmployee().getDepartmentId()))
                    .filter(r -> !r.getEmployee().getId().equals(employeeId))
                    .sorted(Comparator.comparing(LeaveRequest::getStartDate)
                            .thenComparing(r -> r.getEmployee().getName()))
                    .toList();
        }
        int teamOnLeave = (int) teamThisWeek.stream()
                .filter(r -> !r.getStartDate().isAfter(today) && !r.getEndDate().isBefore(today))
                .map(r -> r.getEmployee().getId())
                .distinct()
                .count();
        // 동료의 병가·경조사는 "휴가"로만(팀장이면 맡은 팀원 것은 그대로)
        Predicate<LeaveRequest> typeVisible = teamThisWeek.isEmpty()
                ? r -> true : leaveRequestService.leaveTypeVisibility(employeeId);
        List<DashboardDtos.TeamLeave> teamLeaves = teamThisWeek.stream()
                .map(r -> new DashboardDtos.TeamLeave(r.getEmployee().getId(), r.getEmployee().getName(),
                        typeVisible.test(r) ? r.getLeaveType().getName() : LeaveRequestDtos.HIDDEN_TYPE_NAME,
                        r.getPortion(), r.getStartDate(), r.getEndDate()))
                .toList();

        return new DashboardDtos.PersonalDashboard(balance, pendingCount, upcoming, teamOnLeave, teamLeaves);
    }

    /** 휴가 한 건 중 하루치(쉰 날짜와 그날 몫, 부서 이름). */
    private record DayShare(LocalDate date, BigDecimal days, String department) {
    }

    /**
     * 승인된 휴가를 쉰 날짜마다 나눈다. 올해 날짜만 남긴다(작년 12/30~올해 1/2 휴가는 1/2 만 올해 1월).
     * 여러 날 휴가는 주말·공휴일을 뺀 근무일마다 같은 몫(일수 ÷ 근무일 수), 하루짜리(반차·시간차 포함)는 그날에 전부.
     */
    private List<DayShare> dayShares(List<LeaveRequest> approved, LocalDate from, LocalDate to) {
        if (approved.isEmpty()) {
            return List.of();
        }
        LocalDate min = approved.stream().map(LeaveRequest::getStartDate).min(Comparator.naturalOrder()).orElse(from);
        LocalDate max = approved.stream().map(LeaveRequest::getEndDate).max(Comparator.naturalOrder()).orElse(to);
        Set<LocalDate> holidays = holidayRepository == null ? Set.of()
                : holidayRepository.findByDateBetweenOrderByDateAsc(min, max).stream()
                        .map(Holiday::getDate).collect(Collectors.toSet());
        List<DayShare> shares = new ArrayList<>();
        for (LeaveRequest r : approved) {
            String dept = r.getEmployee().getDepartment() != null
                    ? r.getEmployee().getDepartment().getName() : "미배정";
            if (r.getStartDate().equals(r.getEndDate())) {
                if (!r.getStartDate().isBefore(from) && !r.getStartDate().isAfter(to)) {
                    shares.add(new DayShare(r.getStartDate(), r.getDays(), dept));
                }
                continue;
            }
            List<LocalDate> workdays = new ArrayList<>();
            for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
                if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !holidays.contains(d)) {
                    workdays.add(d);
                }
            }
            if (workdays.isEmpty()) {
                continue;
            }
            BigDecimal each = r.getDays().divide(BigDecimal.valueOf(workdays.size()), 3, RoundingMode.HALF_UP);
            workdays.stream()
                    .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                    .forEach(d -> shares.add(new DayShare(d, each, dept)));
        }
        return shares;
    }

    private List<DashboardDtos.MonthlyUsage> monthlyUsage(List<DayShare> shares) {
        Map<Integer, BigDecimal> map = new TreeMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, BigDecimal.ZERO);
        }
        for (DayShare s : shares) {
            map.merge(s.date().getMonthValue(), s.days(), BigDecimal::add);
        }
        List<DashboardDtos.MonthlyUsage> list = new ArrayList<>();
        map.forEach((m, d) -> list.add(new DashboardDtos.MonthlyUsage(m, d)));
        return list;
    }

    private List<DashboardDtos.DepartmentUsage> departmentUsage(List<DayShare> shares) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (DayShare s : shares) {
            map.merge(s.department(), s.days(), BigDecimal::add);
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
