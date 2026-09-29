package com.company.leave.leave;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 부여 엔진. 정책과 근속에 따라 연도별 연차 잔액을 계산·부여한다.
 * 스케줄러와 신규 입사 시점에 호출된다.
 */
@Service
public class LeaveGrantService {

    private static final Logger log = LoggerFactory.getLogger(LeaveGrantService.class);

    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceService balanceService;
    private final LeaveAccrualCalculator calculator;
    private final PolicyService policyService;
    private final ServiceAwardRuleRepository awardRuleRepository;

    public LeaveGrantService(EmployeeRepository employeeRepository,
                             LeaveBalanceRepository balanceRepository,
                             LeaveBalanceService balanceService,
                             LeaveAccrualCalculator calculator,
                             PolicyService policyService,
                             ServiceAwardRuleRepository awardRuleRepository) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.balanceService = balanceService;
        this.calculator = calculator;
        this.policyService = policyService;
        this.awardRuleRepository = awardRuleRepository;
    }

    /** 특정 사용자의 특정 연도 연차를 (재)부여한다. 이월 규칙도 함께 반영. */
    @Transactional
    public LeaveBalance grantForEmployee(Long employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        LeavePolicy policy = policyService.getActivePolicy();
        return grant(employee, year, policy);
    }

    /** 재직 중인 전 직원에게 해당 연도 연차를 부여한다. */
    @Transactional
    public int grantAll(int year) {
        LeavePolicy policy = policyService.getActivePolicy();
        int count = 0;
        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            grant(employee, year, policy);
            count++;
        }
        log.info("연차 부여 배치 완료: {}년 대상 {}명", year, count);
        return count;
    }

    private LeaveBalance grant(Employee employee, int year, LeavePolicy policy) {
        LocalDate asOf = referenceDate(employee.getHireDate(), year, policy);
        BigDecimal entitlement = calculator.annualEntitlement(employee.getHireDate(), asOf, policy);

        // 장기근속 포상: 해당 연도에 근속 N년에 도달하면 보너스 연차 가산
        int completedYears = Period.between(employee.getHireDate(), asOf).getYears();
        BigDecimal bonus = awardRuleRepository.findByYears(completedYears)
                .map(r -> r.getBonusDays()).orElse(BigDecimal.ZERO);
        entitlement = entitlement.add(bonus);

        LeaveBalance balance = balanceService.getOrCreate(employee.getId(), year);
        balance.setGranted(entitlement);
        applyCarryOver(employee.getId(), year, policy, balance);
        return balance;
    }

    /** 전년도 잔여분을 정책에 따라 이월/소멸 처리한다. */
    private void applyCarryOver(Long employeeId, int year, LeavePolicy policy, LeaveBalance current) {
        balanceRepository.findByEmployeeIdAndYear(employeeId, year - 1).ifPresent(prev -> {
            BigDecimal prevRemaining = prev.remaining();
            if (prevRemaining.signum() <= 0) {
                return;
            }
            if (policy.isCarryOverEnabled()) {
                BigDecimal carry = prevRemaining.min(policy.getMaxCarryOverDays());
                current.setCarriedOver(carry);
                prev.setExpired(prev.getExpired().add(prevRemaining.subtract(carry)));
            } else {
                // 이월 미허용: 전년 잔여 전부 소멸
                prev.setExpired(prev.getExpired().add(prevRemaining));
            }
        });
    }

    /** 연차 산정 기준일. */
    private LocalDate referenceDate(LocalDate hireDate, int year, LeavePolicy policy) {
        if (policy.getGrantBasis() == GrantBasis.FISCAL_YEAR) {
            return LocalDate.of(year, policy.getFiscalStartMonth(), policy.getFiscalStartDay());
        }
        // 입사일 기준: 입사연도는 현재까지의 월별 적치, 이후 연도는 그 해 입사 기념일 기준
        if (year == hireDate.getYear()) {
            LocalDate today = LocalDate.now();
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            return today.isBefore(yearEnd) ? today : yearEnd;
        }
        return safeAnniversary(hireDate, year);
    }

    private LocalDate safeAnniversary(LocalDate hireDate, int year) {
        int day = hireDate.getDayOfMonth();
        LocalDate firstOfMonth = LocalDate.of(year, hireDate.getMonth(), 1);
        int lastDay = firstOfMonth.lengthOfMonth();
        return LocalDate.of(year, hireDate.getMonth(), Math.min(day, lastDay));
    }
}
