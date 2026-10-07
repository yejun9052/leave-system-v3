package com.company.leave.leave;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 연차 기간별 잔액. year 는 "그 해에 시작한 연차 기간"({@link LeavePeriodCalculator}). */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final PolicyService policyService;
    private final LeavePeriodCalculator periods;

    public LeaveBalanceService(LeaveBalanceRepository balanceRepository,
                               LeaveRequestRepository requestRepository,
                               EmployeeRepository employeeRepository,
                               PolicyService policyService,
                               LeavePeriodCalculator periods) {
        this.balanceRepository = balanceRepository;
        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
        this.policyService = policyService;
        this.periods = periods;
    }

    /** 특정 기간 잔액(대기중 신청 포함) 응답. 없으면 0 기반 응답. */
    @Transactional
    public LeaveBalanceResponse getResponse(Long employeeId, int year) {
        LocalDate hireDate = hireDateOf(employeeId);
        LeavePolicy policy = policyService.getActivePolicy();
        return response(employeeId, getOrCreate(employeeId, year), hireDate, policy,
                periods.currentYear(hireDate, policy));
    }

    /** 지난·지금 기간 잔액(최신순). 다음 기간 예약분은 지금 기간의 nextPeriodReserved 로 보여 준다. */
    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listResponses(Long employeeId) {
        LocalDate hireDate = hireDateOf(employeeId);
        LeavePolicy policy = policyService.getActivePolicy();
        int current = periods.currentYear(hireDate, policy);
        return balanceRepository.findByEmployeeIdOrderByYearDesc(employeeId).stream()
                .filter(b -> b.getYear() <= current)
                .map(b -> response(employeeId, b, hireDate, policy, current))
                .toList();
    }

    private LeaveBalanceResponse response(Long employeeId, LeaveBalance balance, LocalDate hireDate,
                                          LeavePolicy policy, int currentYear) {
        int year = balance.getYear();
        LeavePeriodCalculator.Period period = periods.period(hireDate, year, policy);
        BigDecimal pending = requestRepository.sumPendingDeductedDays(employeeId, year);
        BigDecimal nextReserved = year == currentYear ? nextPeriodReserved(employeeId, year + 1) : BigDecimal.ZERO;
        return LeaveBalanceResponse.of(balance, period.start(), period.end(), pending, nextReserved);
    }

    /** 다음 기간에서 뺄 예약분: 승인된 몫(그 기간 잔액의 사용) + 결재 대기 몫. */
    private BigDecimal nextPeriodReserved(Long employeeId, int nextYear) {
        BigDecimal approved = find(employeeId, nextYear).map(LeaveBalance::getUsed).orElse(BigDecimal.ZERO);
        return approved.add(requestRepository.sumPendingDeductedDays(employeeId, nextYear));
    }

    /** 직원 한 명의 어느 날 기준 연차 기간과 그 잔액. */
    public record PeriodBalance(Employee employee, LeavePeriodCalculator.Period period, LeaveBalance balance) {
    }

    /**
     * date 기준으로 각 직원이 쓰고 있던 연차 기간의 잔액(잔액이 없는 직원 제외).
     * 입사일 기준이면 직원마다 기간이 달라 연도 하나로 모을 수 없다. 대시보드·보고서·연차 촉진 집계용.
     *
     * @param activeOnly           재직 중인 직원만
     * @param includeSystemAccount 관리 전용 계정(admin)도 넣을지. 리포트(엑셀)만 빼고 나머지는 넣는다
     */
    @Transactional(readOnly = true)
    public List<PeriodBalance> balancesAsOf(LocalDate date, boolean activeOnly, boolean includeSystemAccount) {
        LeavePolicy policy = policyService.getActivePolicy();
        List<Employee> employees = (activeOnly
                ? employeeRepository.findByStatus(EmployeeStatus.ACTIVE)
                : employeeRepository.findAll()).stream()
                .filter(e -> includeSystemAccount || !e.isSystemAccount())
                .toList();
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> yearOf = employees.stream().collect(Collectors.toMap(Employee::getId,
                e -> periods.yearOf(e.getHireDate(), date, policy)));
        Map<String, LeaveBalance> balances = balanceRepository.findByYearIn(Set.copyOf(yearOf.values())).stream()
                .collect(Collectors.toMap(b -> b.getEmployeeId() + ":" + b.getYear(), b -> b));
        return employees.stream()
                .map(e -> {
                    int year = yearOf.get(e.getId());
                    LeaveBalance b = balances.get(e.getId() + ":" + year);
                    return b == null ? null : new PeriodBalance(e, periods.period(e.getHireDate(), year, policy), b);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** 오늘이 속한 그 직원의 연차 기간. */
    @Transactional(readOnly = true)
    public int currentYear(Long employeeId) {
        return periods.currentYear(hireDateOf(employeeId), policyService.getActivePolicy());
    }

    private LocalDate hireDateOf(Long employeeId) {
        return employeeRepository.findById(employeeId).map(Employee::getHireDate).orElse(null);
    }

    @Transactional
    public LeaveBalance getOrCreate(Long employeeId, int year) {
        return balanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> balanceRepository.save(new LeaveBalance(employeeId, year)));
    }

    /** 잔액 행을 만들지 않고 찾는다(아직 시작 전인 기간의 예약 한도 계산 등). */
    @Transactional(readOnly = true)
    public Optional<LeaveBalance> find(Long employeeId, int year) {
        return balanceRepository.findByEmployeeIdAndYear(employeeId, year);
    }
}
