package com.company.leave.backup;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 점검 모드(복원 중)에는 @Scheduled 자동 작업(공휴일 동기화·연차 부여·촉진 발송)을 실행하지 않는다.
 * 복원이 끝나면 다음 예정 시각에 평소대로 실행된다. 자동 백업은 {@link AutoBackupScheduler} 가 따로 확인한다.
 */
@Aspect
@Component
public class MaintenanceSchedulingAspect {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSchedulingAspect.class);

    private final MaintenanceMode maintenance;

    public MaintenanceSchedulingAspect(MaintenanceMode maintenance) {
        this.maintenance = maintenance;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object skipDuringMaintenance(ProceedingJoinPoint pjp) throws Throwable {
        if (maintenance.isActive()) {
            log.warn("[점검 중] 복원 중이라 자동 작업을 건너뜁니다: {}", pjp.getSignature().toShortString());
            return null;
        }
        return pjp.proceed();
    }
}
