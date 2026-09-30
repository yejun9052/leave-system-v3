package com.company.leave.leave;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.notification.NotificationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 촉진(사용 독려) 및 미사용 연차 현황.
 */
@Service
public class LeavePromotionService {

    private static final Logger log = LoggerFactory.getLogger(LeavePromotionService.class);

    private final LeaveBalanceRepository balanceRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public LeavePromotionService(LeaveBalanceRepository balanceRepository,
                                 EmployeeRepository employeeRepository,
                                 NotificationService notificationService) {
        this.balanceRepository = balanceRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    /** 해당 연도 잔여 연차가 threshold 초과인 대상자 목록. */
    @Transactional(readOnly = true)
    public List<Target> targets(int year, BigDecimal threshold) {
        List<LeaveBalance> balances = balanceRepository.findByYearExcludingSystemAccounts(year).stream()
                .filter(b -> b.remaining().compareTo(threshold) > 0)
                .toList();
        Map<Long, Employee> employees = employeeRepository
                .findAllById(balances.stream().map(LeaveBalance::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, e -> e));

        return balances.stream().map(b -> {
            Employee e = employees.get(b.getEmployeeId());
            return new Target(
                    b.getEmployeeId(),
                    e != null ? e.getName() : "-",
                    e != null && e.getDepartment() != null ? e.getDepartment().getName() : null,
                    b.getGranted(),
                    b.getUsed(),
                    b.remaining());
        }).sorted((a, x) -> x.remaining().compareTo(a.remaining())).toList();
    }

    /** 대상자에게 촉진 알림을 발송하고 발송 건수를 반환. */
    @Transactional
    public int runPromotion(int year, BigDecimal threshold) {
        List<Target> targets = targets(year, threshold);
        for (Target t : targets) {
            notificationService.notify(t.employeeId(), "LEAVE_PROMOTION",
                    "연차 사용 촉진 안내",
                    year + "년 잔여 연차 " + t.remaining() + "일이 남아 있습니다. 사용 계획을 등록해 주세요.",
                    "/my-leaves");
        }
        log.info("연차 촉진 알림 발송: {}년 대상 {}명", year, targets.size());
        return targets.size();
    }

    public record Target(
            Long employeeId,
            String name,
            String department,
            BigDecimal granted,
            BigDecimal used,
            BigDecimal remaining) {
    }
}
