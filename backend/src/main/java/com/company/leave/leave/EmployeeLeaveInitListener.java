package com.company.leave.leave;

import com.company.leave.employee.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신규 사용자 생성 시 당해 연도 연차를 초기 부여한다.
 * <p>사용자 생성 트랜잭션이 <b>커밋된 후</b> 별도 트랜잭션으로 실행한다. 초기 부여가 실패해도
 * 사용자 생성 자체는 롤백되지 않으며(부여는 부차적 작업), 실패는 로그로 남긴다.
 */
@Component
public class EmployeeLeaveInitListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLeaveInitListener.class);

    private final LeaveGrantService leaveGrantService;

    public EmployeeLeaveInitListener(LeaveGrantService leaveGrantService) {
        this.leaveGrantService = leaveGrantService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmployeeCreated(EmployeeService.EmployeeCreatedEvent event) {
        try {
            leaveGrantService.grantCurrentPeriod(event.employeeId());
        } catch (Exception e) {
            log.warn("신규 사용자 초기 연차 부여 실패 (employeeId={}): {}", event.employeeId(), e.getMessage());
        }
    }
}
