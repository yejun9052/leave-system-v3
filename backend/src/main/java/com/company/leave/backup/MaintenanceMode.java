package com.company.leave.backup;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 점검 모드(복원 중). 켜져 있으면 API 요청은 503({@link MaintenanceFilter}), 자동 작업은 건너뛴다
 * ({@link MaintenanceSchedulingAspect}, 자동 백업). 처리 중인 API 요청 수를 세어 복원 전에 끝나기를 기다린다.
 */
@Component
public class MaintenanceMode {

    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();

    public boolean isActive() {
        return active.get();
    }

    /** 점검 시작. 이미 점검 중이면 false. */
    public boolean begin() {
        return active.compareAndSet(false, true);
    }

    public void end() {
        active.set(false);
    }

    void requestStarted() {
        inFlight.incrementAndGet();
    }

    void requestFinished() {
        inFlight.decrementAndGet();
    }

    /**
     * 처리 중인 API 요청이 others 개 이하가 될 때까지 기다린다(복원 요청 자신은 1개로 센다).
     *
     * @return 제한 시간 안에 끝났으면 true
     */
    public boolean awaitIdle(int others, Duration timeout) throws InterruptedException {
        long end = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > others) {
            if (System.nanoTime() > end) {
                return false;
            }
            Thread.sleep(100);
        }
        return true;
    }
}
