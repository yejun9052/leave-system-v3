package com.company.leave.batch;

import com.company.leave.calendar.holiday.HolidaySyncService;
import java.util.List;
import java.util.stream.Collectors;
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

    static final String SYNC_CRON = "0 10 0 * * *";

    private final HolidaySyncService syncService;
    private final JobRunRecorder recorder;

    public HolidaySyncScheduler(HolidaySyncService syncService, JobRunRecorder recorder) {
        this.syncService = syncService;
        this.recorder = recorder;
    }

    /** 매일 00:10 - 올해·내년 공휴일 동기화 */
    @Scheduled(cron = SYNC_CRON, zone = "Asia/Seoul")
    public void dailySync() {
        if (!syncService.isConfigured()) {
            log.warn("[스케줄러] 공휴일 API 키가 없어 동기화를 건너뜁니다(app.holiday-api.service-key).");
            recorder.skipped(JobRunRecorder.HOLIDAY_SYNC, "공휴일 API 키가 없어 건너뜀");
            return;
        }
        log.info("[스케줄러] 공휴일 동기화 시작");
        long startedAt = System.currentTimeMillis();
        try {
            recorder.run(JobRunRecorder.HOLIDAY_SYNC, () -> summary(syncService.syncCurrentAndNextYear()));
        } finally {
            log.info("[스케줄러] 공휴일 동기화 종료 ({}ms)", System.currentTimeMillis() - startedAt);
        }
    }

    /** "2026년 추가 0·변경 0 / 2027년 추가 3·변경 0, 휴가 조정 1건". 받지 못한 해가 있으면 실패로 남긴다. */
    static String summary(List<HolidaySyncService.SyncResult> results) {
        if (results.size() < 2) {
            throw new IllegalStateException("올해·내년 중 받지 못한 해가 있습니다("
                    + results.stream().map(r -> r.year() + "년").collect(Collectors.joining(", ")) + "만 성공)");
        }
        String years = results.stream()
                .map(r -> r.year() + "년 추가 " + r.added().size() + "·변경 " + r.renamed().size())
                .collect(Collectors.joining(" / "));
        int adjusted = results.stream().mapToInt(HolidaySyncService.SyncResult::adjustedRequests).sum();
        return years + (adjusted > 0 ? ", 휴가 조정 " + adjusted + "건" : "");
    }
}
