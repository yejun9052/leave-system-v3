package com.company.leave.batch;

import com.company.leave.leave.LeaveGrantService;
import com.company.leave.leave.LeavePromotionService;
import com.company.leave.policy.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 연차 자동 부여/이월 스케줄러.
 *
 * <ul>
 *   <li>매일 새벽: 당해 연도 연차 재계산 (입사 1년 미만자의 월 단위 적치 반영)</li>
 *   <li>매년 1월 1일: 새해 연차 부여 + 전년 잔여 이월/소멸 처리</li>
 * </ul>
 */
@Component
public class LeaveAccrualScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualScheduler.class);

    private final LeaveGrantService leaveGrantService;
    private final LeavePromotionService promotionService;
    private final PolicyService policyService;

    public LeaveAccrualScheduler(LeaveGrantService leaveGrantService,
                                 LeavePromotionService promotionService,
                                 PolicyService policyService) {
        this.leaveGrantService = leaveGrantService;
        this.promotionService = promotionService;
        this.policyService = policyService;
    }

    /**
     * 매일 01:00 - 직원마다 지금 연차 기간 부여·재계산. 입사 기념일(회계연도 기준이면 회계연도 시작일)이 지나
     * 새 기간이 시작된 직원은 이때 지난 기간 잔여가 이월·소멸된다. 그래서 1월 1일 일괄 부여는 따로 없다.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void dailyRecompute() {
        log.info("[스케줄러] 직원별 지금 연차 기간 부여·재계산 시작");
        leaveGrantService.grantCurrentPeriods();
    }

    /**
     * 연차 촉진 정기 알림 7/1·11/1 - 정책이 켜져 있을 때만. 지금 연차 기간에 사용 계획이 없는 연차가 있는 직원에게 앱 알림만.
     * (메일 촉진은 관리자가 대상을 골라 보낸다. 직원별 사용 기한에 맞춘 자동 발송은 정책으로 따로 붙일 예정)
     */
    @Scheduled(cron = "0 0 9 1 7,11 *", zone = "Asia/Seoul")
    public void promotion() {
        if (!policyService.getActivePolicy().isPromotionEnabled()) {
            return;
        }
        log.info("[스케줄러] 연차 촉진 정기 알림 발송 시작");
        promotionService.notifyRemaining();
    }
}
