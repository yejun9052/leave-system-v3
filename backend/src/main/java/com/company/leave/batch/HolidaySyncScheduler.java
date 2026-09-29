package com.company.leave.batch;

import com.company.leave.calendar.holiday.HolidaySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공휴일 자동 동기화. 임시공휴일은 연중 수시로 지정되므로 매일 00:10 에 올해·내년을 다시 받는다
 * (1회 24건 호출, 개발 계정 일 10,000건 제한과 무관).
 */
@Component
public class HolidaySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncScheduler.class);

    private final HolidaySyncService syncService;

    public HolidaySyncScheduler(HolidaySyncService syncService) {
        this.syncService = syncService;
    }

    /** 매일 00:10 - 올해·내년 공휴일 동기화 */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void dailySync() {
        if (!syncService.isConfigured()) {
            log.warn("[스케줄러] 공휴일 API 키가 없어 동기화를 건너뜁니다(app.holiday-api.service-key).");
            return;
        }
        log.info("[스케줄러] 공휴일 동기화 시작");
        syncService.syncCurrentAndNextYear();
    }
}
