package com.company.leave.leave.accrual;

import com.company.leave.leave.domain.LeaveType;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 연차 사용 기간(회차) 계산기 (순수 계산 로직).
 *
 * <p>연차 잔액({@code leave_balances.year})과 휴가의 적용 연도({@code leave_requests.applied_year})는
 * "그 해에 시작한 연차 기간"을 가리킨다.
 * <ul>
 *   <li>입사일 기준: 입사 기념일 ~ 다음 기념일 전날. 2월 29일 입사자는 평년에 2월 28일이 기념일</li>
 *   <li>회계연도 기준: 회계연도 시작일 ~ 다음 시작일 전날</li>
 * </ul>
 * 입사일이 없으면(데이터 누락) 달력 연도로 본다.
 */
@Component
public class LeavePeriodCalculator {

    private final LeaveAccrualCalculator accrualCalculator;
    private final WorkdayCalculator workdayCalculator;

    public LeavePeriodCalculator(LeaveAccrualCalculator accrualCalculator, WorkdayCalculator workdayCalculator) {
        this.accrualCalculator = accrualCalculator;
        this.workdayCalculator = workdayCalculator;
    }

    /** 연차 기간 [start, end] (양 끝 포함). */
    public record Period(int year, LocalDate start, LocalDate end) {
    }

    /** year 에 시작하는 연차 기간의 첫날. */
    public LocalDate startOf(LocalDate hireDate, int year, LeavePolicy policy) {
        if (policy.getGrantBasis() == GrantBasis.FISCAL_YEAR) {
            LocalDate first = LocalDate.of(year, policy.getFiscalStartMonth(), 1);
            return first.withDayOfMonth(Math.min(policy.getFiscalStartDay(), first.lengthOfMonth()));
        }
        if (hireDate == null) {
            return LocalDate.of(year, 1, 1);
        }
        return LeaveAccrualCalculator.anniversary(hireDate, year);
    }

    public Period period(LocalDate hireDate, int year, LeavePolicy policy) {
        return new Period(year, startOf(hireDate, year, policy), startOf(hireDate, year + 1, policy).minusDays(1));
    }

    /** date 가 속한 연차 기간의 연도. 입사 전 날짜는 첫 기간(입사 연도)으로 본다. */
    public int yearOf(LocalDate hireDate, LocalDate date, LeavePolicy policy) {
        if (policy.getGrantBasis() == GrantBasis.HIRE_DATE && hireDate != null && date.isBefore(hireDate)) {
            return hireDate.getYear();
        }
        int year = date.getYear();
        return date.isBefore(startOf(hireDate, year, policy)) ? year - 1 : year;
    }

    /** 오늘이 속한 연차 기간의 연도. */
    public int currentYear(LocalDate hireDate, LeavePolicy policy) {
        return yearOf(hireDate, LocalDate.now(), policy);
    }

    /**
     * year 기간에 부여할(또는 부여될 것으로 예상하는) 연차 일수. 이월분은 넣지 않는다.
     * 입사 첫 기간(1년 미만)은 오늘까지 쌓인 월차, 그 뒤 기간은 기간 시작일 기준 근속 연차.
     */
    public BigDecimal entitlement(LocalDate hireDate, int year, LeavePolicy policy) {
        if (hireDate == null) {
            return BigDecimal.ZERO;
        }
        return accrualCalculator.annualEntitlement(hireDate, referenceDate(hireDate, year, policy), policy);
    }

    /** 연차 산정 기준일. */
    private LocalDate referenceDate(LocalDate hireDate, int year, LeavePolicy policy) {
        Period period = period(hireDate, year, policy);
        if (policy.getGrantBasis() == GrantBasis.HIRE_DATE && year == hireDate.getYear()) {
            // 입사 첫 기간: 오늘까지 쌓인 월차(기간이 끝났으면 기간 끝까지)
            LocalDate today = LocalDate.now();
            return today.isBefore(period.end()) ? today : period.end();
        }
        return period.start();
    }

    /**
     * 휴가 중 다음 연차 기간(기산일 이후)에 속한 날짜의 차감액. v2 "미래 차감 분리"와 같은 규칙:
     * 기산일 전 날짜는 시작일이 속한 기간에서, 기산일부터의 날짜는 다음 기간에서 뺀다.
     * 연차를 차감하지 않는 종류나 기간을 넘지 않는 휴가는 0. 부분 휴가(반차·시간차)는 하루짜리라 넘지 않는다.
     *
     * @param appliedYear 시작일이 속한 기간({@link #yearOf})
     */
    public BigDecimal nextPeriodDeduction(LocalDate hireDate, int appliedYear, LeaveType type,
                                          LocalDate start, LocalDate end, Set<LocalDate> holidays,
                                          Integer hours, LeavePolicy policy) {
        if (!type.isDeductFromAnnual() || type.isPartialDay()) {
            return BigDecimal.ZERO;
        }
        LocalDate nextStart = startOf(hireDate, appliedYear + 1, policy);
        if (end.isBefore(nextStart)) {
            return BigDecimal.ZERO;
        }
        LocalDate from = start.isAfter(nextStart) ? start : nextStart;
        BigDecimal days = workdayCalculator.computeLeaveDays(from, end, type, holidays, hours);
        return workdayCalculator.deductionFor(type, days);
    }
}
