package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 부여 엔진. 정책과 근속에 따라 연차 기간별 잔액을 계산·부여한다.
 * 연차 기간은 {@link LeavePeriodCalculator}(입사일 기준이면 입사 기념일부터 1년).
 * 매일 배치가 직원마다 지금 기간을 부여하고, 기념일이 지나 새 기간이 시작되면 지난 기간 잔여를 이월·소멸한다.
 */
@Service
public class LeaveGrantService {

    private static final Logger log = LoggerFactory.getLogger(LeaveGrantService.class);

    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceService balanceService;
    private final LeavePeriodCalculator periods;
    private final PolicyService policyService;
    private final ServiceAwardRuleRepository awardRuleRepository;

    public LeaveGrantService(EmployeeRepository employeeRepository,
                             LeaveBalanceRepository balanceRepository,
                             LeaveBalanceService balanceService,
                             LeavePeriodCalculator periods,
                             PolicyService policyService,
                             ServiceAwardRuleRepository awardRuleRepository) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.balanceService = balanceService;
        this.periods = periods;
        this.policyService = policyService;
        this.awardRuleRepository = awardRuleRepository;
    }

    /** 특정 사용자의 특정 연차 기간을 (재)부여한다. 이월 규칙도 함께 반영. */
    @Transactional
    public LeaveBalance grantForEmployee(Long employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        LeavePolicy policy = policyService.getActivePolicy();
        if (year > currentYear(employee, policy)) {
            // 미리 부여하면 지금 기간 잔여가 이월·소멸 처리돼 버린다
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아직 시작하지 않은 연차 기간은 부여할 수 없습니다.");
        }
        return grant(employee, year, policy);
    }

    /** 특정 사용자의 지금 연차 기간을 (재)부여한다(신규 입사 등). */
    @Transactional
    public LeaveBalance grantCurrentPeriod(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        LeavePolicy policy = policyService.getActivePolicy();
        return grant(employee, currentYear(employee, policy), policy);
    }

    /** 재직 중인 전 직원에게 year 에 시작한 연차 기간을 부여한다(관리 전용 계정 포함: 테스트용으로 휴가를 쓸 수 있다). */
    @Transactional
    public int grantAll(int year) {
        LeavePolicy policy = policyService.getActivePolicy();
        int count = 0;
        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            if (employee.getHireDate() != null && year < employee.getHireDate().getYear()) {
                continue; // 입사 전 기간은 없다
            }
            if (year > currentYear(employee, policy)) {
                continue; // 아직 시작 전인 기간: 부여하면 지금 기간 잔여가 미리 소멸된다
            }
            grant(employee, year, policy);
            count++;
        }
        log.info("연차 부여 배치 완료: {}년 시작 기간, 대상 {}명", year, count);
        return count;
    }

    /**
     * 재직 중인 전 직원에게 각자 지금 연차 기간을 부여한다(매일 배치). 입사 기념일이 지나 새 기간이 시작된 직원은
     * 이때 지난 기간 잔여가 이월·소멸된다.
     */
    @Transactional
    public int grantCurrentPeriods() {
        LeavePolicy policy = policyService.getActivePolicy();
        int count = 0;
        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            grant(employee, currentYear(employee, policy), policy);
            count++;
        }
        log.info("연차 부여 배치 완료: 직원별 지금 기간, 대상 {}명", count);
        return count;
    }

    private int currentYear(Employee employee, LeavePolicy policy) {
        // 입사 예정자는 첫 기간(입사 연도)을 미리 부여한다
        LocalDate hireDate = employee.getHireDate();
        return periods.yearOf(hireDate, LocalDate.now(), policy);
    }

    private LeaveBalance grant(Employee employee, int year, LeavePolicy policy) {
        BigDecimal entitlement = periods.entitlement(employee.getHireDate(), year, policy);

        // [일시 중지 2026-09-30] 장기근속 포상 자동 가산. 정책 정리 전까지 연차 부여에 포상을 더하지 않는다.
        // 다시 켤 때: 근속 연수는 LeaveAccrualCalculator.completedYears(2월 29일 입사자도 기념일 기준)를 쓸 것.
        // int completedYears = LeaveAccrualCalculator.completedYears(employee.getHireDate(), periodStart);
        // BigDecimal bonus = awardRuleRepository.findByYears(completedYears)
        //         .map(r -> r.getBonusDays()).orElse(BigDecimal.ZERO);
        // entitlement = entitlement.add(bonus);

        LeaveBalance balance = balanceService.getOrCreate(employee.getId(), year);
        balance.setGranted(entitlement);
        applyCarryOver(employee.getId(), year, policy, balance);
        return balance;
    }

    /**
     * 지난 기간 잔여를 정책에 따라 이월/소멸 처리한다. 바로 앞 기간은 이월 규칙을 따르고,
     * 그보다 오래된 기간에 남은 잔여(서버가 오래 꺼져 있었거나 지난 휴가가 취소돼 돌아온 연차)는 모두 소멸한다.
     */
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
                // 이월 미허용: 전 기간 잔여 전부 소멸
                prev.setExpired(prev.getExpired().add(prevRemaining));
            }
        });
        balanceRepository.findByEmployeeIdOrderByYearDesc(employeeId).stream()
                .filter(b -> b.getYear() < year - 1 && b.remaining().signum() > 0)
                .forEach(old -> old.setExpired(old.getExpired().add(old.remaining())));
    }
}
