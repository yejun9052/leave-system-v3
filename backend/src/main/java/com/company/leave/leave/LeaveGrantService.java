package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final LeaveRequestRepository requestRepository;
    /** 매일 배치: 직원마다 따로 커밋해 한 명의 실패가 전 직원 부여를 되돌리지 않게 */
    private final TransactionTemplate perEmployee;

    public LeaveGrantService(EmployeeRepository employeeRepository,
                             LeaveBalanceRepository balanceRepository,
                             LeaveBalanceService balanceService,
                             LeavePeriodCalculator periods,
                             PolicyService policyService,
                             LeaveRequestRepository requestRepository,
                             PlatformTransactionManager transactionManager) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.balanceService = balanceService;
        this.periods = periods;
        this.policyService = policyService;
        this.requestRepository = requestRepository;
        this.perEmployee = new TransactionTemplate(transactionManager);
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
     * 직원마다 따로 커밋한다: 한 명이 실패해도(같은 시각 승인과 잔액 충돌 등) 나머지는 부여되고,
     * 실패한 직원이 있으면 끝에 예외로 알려 자동화 탭에 실패로 남는다(다음 날 배치가 다시 맞춘다).
     */
    public int grantCurrentPeriods() {
        LeavePolicy policy = policyService.getActivePolicy();
        int count = 0;
        List<String> failed = new ArrayList<>();
        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            try {
                perEmployee.executeWithoutResult(status -> grant(employee, currentYear(employee, policy), policy));
                count++;
            } catch (RuntimeException ex) {
                log.error("연차 부여 실패: 직원 {}({})", employee.getName(), employee.getId(), ex);
                failed.add(employee.getName());
            }
        }
        log.info("연차 부여 배치 완료: 직원별 지금 기간, 대상 {}명, 실패 {}명", count, failed.size());
        if (!failed.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "연차 부여: " + count + "명 부여, " + failed.size() + "명 실패("
                    + String.join(", ", failed.subList(0, Math.min(5, failed.size())))
                    + (failed.size() > 5 ? " 외" : "") + ")");
        }
        return count;
    }

    private int currentYear(Employee employee, LeavePolicy policy) {
        // 입사 예정자는 첫 기간(입사 연도)을 미리 부여한다
        LocalDate hireDate = employee.getHireDate();
        return periods.yearOf(hireDate, LocalDate.now(), policy);
    }

    private LeaveBalance grant(Employee employee, int year, LeavePolicy policy) {
        // 장기근속 포상(근속 N년이 되는 기간에 한 번)도 포함된 부여 일수
        BigDecimal entitlement = periods.entitlement(employee.getHireDate(), year, policy);

        LeaveBalance balance = balanceService.getOrCreate(employee.getId(), year);
        balance.setGranted(entitlement);
        applyCarryOver(employee.getId(), year, policy, balance);
        return balance;
    }

    /**
     * 지난 기간 잔여를 정책에 따라 이월/소멸 처리한다. 바로 앞 기간은 이월 규칙을 따르고,
     * 그보다 오래된 기간에 남은 잔여(서버가 오래 꺼져 있었거나 지난 휴가가 취소돼 돌아온 연차)는 모두 소멸한다.
     * 이월한 몫은 지난 기간 잔여에 그대로 남는다(이번 기간 carriedOver 와 같은 값). 그래서 매일 다시 계산해 덮어써도
     * 두 번 이월되지 않고, 지난 기간 휴가가 취소돼 돌아온 연차는 한도까지 이월에 더해지고 나머지는 소멸한다.
     * 지난 기간 날짜로 결재 대기 중인 신청의 몫은 이월·소멸하지 않고 남겨 둔다: 기산일 뒤에 승인돼도 잔여가 있어야 한다.
     * 반려·취소되면 그 몫이 다음 배치에서 이월·소멸된다.
     *
     * <p>당겨쓴 연차(마이너스 잔여)는 이번 기간 carriedOver 에 음수로 넣어 갚는다. 이월 허용·한도, 마이너스 허용 정책과
     * 상관없이 항상(docs/decisions/2026-10-13-negative-leave-repayment.md). 지난 기간 기록은 그대로 음수로 남아(양수 이월과
     * 같은 방식) 매일 다시 계산해도 두 번 빠지지 않는다. 판단은 승인된 것만 본다(결재 대기는 승인된 뒤 다음 배치에서).
     * carriedOver 는 경우마다 덮어쓴다: 지난 기간 휴가가 취소돼 갚을 것이 없어지면 0 또는 양수 이월로 돌아간다.
     */
    private void applyCarryOver(Long employeeId, int year, LeavePolicy policy, LeaveBalance current) {
        balanceRepository.findByEmployeeIdAndYear(employeeId, year - 1).ifPresent(prev -> {
            if (prev.remaining().signum() < 0) {
                current.setCarriedOver(prev.remaining());
                return;
            }
            BigDecimal pending = requestRepository.sumPendingDeductedDays(employeeId, year - 1).max(BigDecimal.ZERO);
            BigDecimal settle = prev.remaining().subtract(pending);
            if (settle.signum() <= 0) {
                current.setCarriedOver(BigDecimal.ZERO);
                return;
            }
            if (policy.isCarryOverEnabled()) {
                BigDecimal carry = settle.min(policy.getMaxCarryOverDays());
                current.setCarriedOver(carry);
                prev.expire(settle.subtract(carry));
            } else {
                // 이월 미허용: 전 기간 잔여(결재 대기 몫 제외) 전부 소멸
                prev.expire(settle);
            }
        });
        balanceRepository.findByEmployeeIdOrderByYearDesc(employeeId).stream()
                .filter(b -> b.getYear() < year - 1 && b.remaining().signum() > 0)
                .forEach(LeaveBalance::expireRemaining);
    }
}
