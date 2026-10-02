package com.company.leave.batch;

import com.company.leave.leave.LeaveGrantService;
import com.company.leave.leave.LeavePromotionService;
import com.company.leave.policy.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 연차 자동 작업 스케줄러. 실행 결과는 {@link JobRunRecorder} 로 남겨 자동화 탭에서 보여 준다.
 *
 * <ul>
 *   <li>매일 01:00: 직원별 지금 연차 기간 부여·재계산, 입사 기념일이 지나면 지난 기간 이월·소멸</li>
 *   <li>매일 09:00: 연차 촉진 자동 발송(정책에서 켰을 때)</li>
 * </ul>
 */
@Component
public class LeaveAccrualScheduler {

    static final String GRANT_CRON = "0 0 1 * * *";
    static final String PROMOTION_CRON = "0 0 9 * * *";

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualScheduler.class);

    private final LeaveGrantService leaveGrantService;
    private final LeavePromotionService promotionService;
    private final PolicyService policyService;
    private final JobRunRecorder recorder;

    public LeaveAccrualScheduler(LeaveGrantService leaveGrantService,
                                 LeavePromotionService promotionService,
                                 PolicyService policyService,
                                 JobRunRecorder recorder) {
        this.leaveGrantService = leaveGrantService;
        this.promotionService = promotionService;
        this.policyService = policyService;
        this.recorder = recorder;
    }

    /**
     * 매일 01:00 - 직원마다 지금 연차 기간 부여·재계산. 입사 기념일(회계연도 기준이면 회계연도 시작일)이 지나
     * 새 기간이 시작된 직원은 이때 지난 기간 잔여가 이월·소멸된다. 그래서 1월 1일 일괄 부여는 따로 없다.
     */
    @Scheduled(cron = GRANT_CRON, zone = "Asia/Seoul")
    public void dailyRecompute() {
        log.info("[스케줄러] 직원별 지금 연차 기간 부여·재계산 시작");
        recorder.run(JobRunRecorder.LEAVE_GRANT,
                () -> "재직자 " + leaveGrantService.grantCurrentPeriods() + "명 부여·재계산");
    }

    /**
     * 매일 09:00 - 연차 촉진 자동 발송(정책에서 켰을 때만). 발송 시기(사용 기한 N개월 전)에 들어온 직원에게
     * 알림·메일을 보낸다. 같은 시기에 이미 보낸 직원(수동 포함)은 건너뛴다({@link LeavePromotionService#autoSend}).
     */
    @Scheduled(cron = PROMOTION_CRON, zone = "Asia/Seoul")
    public void promotion() {
        if (!policyService.getActivePolicy().isPromotionEnabled()) {
            recorder.skipped(JobRunRecorder.PROMOTION_AUTO, "자동 발송이 꺼져 있어 보내지 않음");
            return;
        }
        log.info("[스케줄러] 연차 촉진 자동 발송 시작");
        recorder.run(JobRunRecorder.PROMOTION_AUTO, () -> {
            LeavePromotionService.AutoResult r = promotionService.autoSend();
            return r.sent() + "명 발송(메일 " + r.mailed() + "명)"
                    + (r.alreadySent() > 0 ? ", 같은 시기에 이미 보내 건너뜀 " + r.alreadySent() + "명" : "");
        });
    }
}
