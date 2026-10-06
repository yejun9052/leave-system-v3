package com.company.leave.leave;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 기간 기준 전환(입사일 기준 기간, 2026-10) 뒤 한 번만: 기존 휴가와 잔액을 새 기준(입사일 기준이면 입사 기념일부터 1년)으로 다시 계산한다.
 * <ol>
 *   <li>휴가마다 시작일이 속한 기간과 기산일을 걸친 다음 기간 몫을 다시 정한다</li>
 *   <li>잔액의 사용·이월·소멸을 0으로 돌리고, 승인된 휴가(취소 요청 중 포함)의 차감과 병가·공가 소멸을 다시 쌓는다</li>
 *   <li>직원마다 가장 오래된 기간부터 지금 기간까지 차례로 다시 부여해 부여 일수와 지난 기간 이월·소멸을 맞춘다</li>
 * </ol>
 * one_time_tasks 의 'leave-period-rebuild' 행을 먼저 완료로 바꾸고 같은 트랜잭션에서 실행한다.
 * 실패하면 전체가 롤백되어 다음 시작 때 다시 시도하고, 서버가 여러 대여도 한 대만 실행한다.
 * 기본 데이터·공휴일 준비(@Order 1~3) 뒤에 실행한다.
 */
@Order(4)
@Component
public class LeavePeriodRebuildRunner implements ApplicationRunner {

    static final String TASK = "leave-period-rebuild";
    private static final Logger log = LoggerFactory.getLogger(LeavePeriodRebuildRunner.class);
    private static final Set<LeaveRequestStatus> CHARGED = EnumSet.of(
            LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED);

    @PersistenceContext
    private EntityManager em;

    private final LeaveRequestRepository requestRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceService balanceService;
    private final LeaveGrantService grantService;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final PolicyService policyService;
    private final LeavePeriodCalculator periods;

    public LeavePeriodRebuildRunner(LeaveRequestRepository requestRepository,
                                    LeaveBalanceRepository balanceRepository,
                                    LeaveBalanceService balanceService,
                                    LeaveGrantService grantService,
                                    EmployeeRepository employeeRepository,
                                    HolidayRepository holidayRepository,
                                    PolicyService policyService,
                                    LeavePeriodCalculator periods) {
        this.requestRepository = requestRepository;
        this.balanceRepository = balanceRepository;
        this.balanceService = balanceService;
        this.grantService = grantService;
        this.employeeRepository = employeeRepository;
        this.holidayRepository = holidayRepository;
        this.policyService = policyService;
        this.periods = periods;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int claimed = em.createNativeQuery(
                        "update one_time_tasks set done_at = now() where name = :name and done_at is null")
                .setParameter("name", TASK)
                .executeUpdate();
        if (claimed == 0) {
            return;
        }
        log.info("[연차 기간 전환] 기존 휴가·잔액을 새 연차 기간 기준으로 다시 계산합니다.");
        LeavePolicy policy = policyService.getActivePolicy();
        Set<LocalDate> holidays = holidayRepository.findAll().stream()
                .map(Holiday::getDate).collect(Collectors.toSet());

        int moved = 0;
        int split = 0;
        var requests = requestRepository.findAll();
        for (LeaveRequest r : requests) {
            LocalDate hireDate = r.getEmployee().getHireDate();
            int year = periods.yearOf(hireDate, r.getStartDate(), policy);
            Integer hours = r.getLeaveType().getPortion() == DayPortion.HOURLY
                    ? WorkdayCalculator.hoursOf(r.getDays()) : null;
            BigDecimal next = periods.nextPeriodDeduction(hireDate, year, r.getLeaveType(), r.getStartDate(),
                    r.getEndDate(), holidays, hours, policy).min(r.getDeductedDays());
            if (year != r.getAppliedYear()) {
                moved++;
            }
            if (next.signum() > 0) {
                split++;
            }
            r.assignPeriods(year, next);
        }

        balanceRepository.findAll().forEach(LeaveBalance::resetUsage);
        for (LeaveRequest r : requests) {
            if (!CHARGED.contains(r.getStatus())) {
                continue;
            }
            LeaveCharges.charge(balanceService, r);
            if (r.getForfeitedDays().signum() > 0) {
                balanceService.getOrCreate(r.getEmployee().getId(), r.getAppliedYear()).forfeit(r.getForfeitedDays());
            }
        }

        int employees = 0;
        for (Employee e : employeeRepository.findAll()) {
            if (e.isSystemAccount()) {
                continue;
            }
            grantService.regrantAllPeriods(e);
            employees++;
        }
        log.info("[연차 기간 전환] 완료: 휴가 {}건 중 기간이 바뀐 {}건, 기산일을 걸쳐 나눈 {}건, 직원 {}명 재부여",
                requests.size(), moved, split, employees);
    }
}
