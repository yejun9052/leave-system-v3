package com.company.leave.leave.accrual;

import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 근로기준법 제60조 기반 연차 부여 일수 계산기 (순수 계산 로직).
 * 기본일수/가산주기/가산량/상한/월차는 {@link LeavePolicy} 값에 따른다.
 *
 * <ul>
 *   <li>입사 1년 미만: 1개월 개근 시 1일 (정책 상한, 기본 11일)</li>
 *   <li>입사 1년 이상: 기본 연차(기본 15일)</li>
 *   <li>근속 가산: 가산 주기(기본 2년)마다 가산량(기본 1일), 상한(기본 25일)</li>
 * </ul>
 */
@Component
public class LeaveAccrualCalculator {

    /**
     * 주어진 기준일(asOf) 시점에 사용자가 보유해야 할 연간 연차 부여 일수.
     */
    public BigDecimal annualEntitlement(LocalDate hireDate, LocalDate asOf, LeavePolicy policy) {
        if (hireDate == null || asOf == null || asOf.isBefore(hireDate)) {
            return BigDecimal.ZERO;
        }
        if (policy.getGrantBasis() == GrantBasis.FISCAL_YEAR) {
            return fiscalYearEntitlement(hireDate, asOf, policy);
        }
        return hireDateEntitlement(hireDate, asOf, policy);
    }

    /** 입사일 기준. */
    private BigDecimal hireDateEntitlement(LocalDate hireDate, LocalDate asOf, LeavePolicy policy) {
        int completedYears = Period.between(hireDate, asOf).getYears();
        if (completedYears < 1) {
            if (!policy.isMonthlyAccrualEnabled()) {
                return BigDecimal.ZERO;
            }
            int months = monthsCompleted(hireDate, asOf);
            return BigDecimal.valueOf(Math.min(policy.getMonthlyAccrualMax(), months));
        }
        return serviceEntitlement(completedYears, policy);
    }

    /** 회계연도 기준. */
    private BigDecimal fiscalYearEntitlement(LocalDate hireDate, LocalDate asOf, LeavePolicy policy) {
        LocalDate fiscalStart = currentFiscalStart(asOf, policy);
        LocalDate nextFiscalStart = fiscalStart.plusYears(1);

        if (!hireDate.isBefore(fiscalStart)) {
            // 회계연도 도중 입사 → 잔여 기간 비례 부여 (기본 연차 기준)
            long totalDays = ChronoUnit.DAYS.between(fiscalStart, nextFiscalStart);
            long workedDays = ChronoUnit.DAYS.between(hireDate, nextFiscalStart);
            if (totalDays <= 0) {
                return BigDecimal.ZERO;
            }
            return policy.getBaseAnnualDays()
                    .multiply(BigDecimal.valueOf(workedDays))
                    .divide(BigDecimal.valueOf(totalDays), 1, RoundingMode.HALF_UP);
        }

        int yearsAtFiscalStart = Period.between(hireDate, fiscalStart).getYears();
        return serviceEntitlement(Math.max(1, yearsAtFiscalStart), policy);
    }

    /** 근속연수(>=1)에 따른 연차 일수: 기본 + 가산량*(근속-1)/주기, 상한 적용. */
    private BigDecimal serviceEntitlement(int completedYears, LeavePolicy policy) {
        int step = Math.max(1, policy.getSeniorityStepYears());
        int steps = (completedYears - 1) / step;
        BigDecimal additional = policy.getSeniorityIncrementDays().multiply(BigDecimal.valueOf(steps));
        BigDecimal total = policy.getBaseAnnualDays().add(additional);
        return total.min(policy.getMaxAnnualDays());
    }

    private int monthsCompleted(LocalDate from, LocalDate to) {
        Period p = Period.between(from, to);
        return p.getYears() * 12 + p.getMonths();
    }

    /** asOf 가 속한 회계연도의 시작일. */
    public LocalDate currentFiscalStart(LocalDate asOf, LeavePolicy policy) {
        LocalDate candidate = LocalDate.of(asOf.getYear(),
                policy.getFiscalStartMonth(), policy.getFiscalStartDay());
        if (asOf.isBefore(candidate)) {
            candidate = candidate.minusYears(1);
        }
        return candidate;
    }
}
