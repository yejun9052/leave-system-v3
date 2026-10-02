package com.company.leave.calendar.holiday;

import com.company.leave.calendar.repository.HolidayRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기동 시 1회: 공휴일 API 키가 있고 올해 공휴일이 한 건도 없으면 올해·내년을 동기화한다.
 * 실패해도 기동은 계속한다(매일 00:10 스케줄이 다시 시도).
 * 기본 데이터 생성(LeaveDataInitializer, @Order(2)) 이후에 실행.
 */
@Order(3)
@Component
public class HolidayStartupSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HolidayStartupSync.class);

    private final HolidaySyncService syncService;
    private final HolidayRepository holidayRepository;

    public HolidayStartupSync(HolidaySyncService syncService, HolidayRepository holidayRepository) {
        this.syncService = syncService;
        this.holidayRepository = holidayRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncService.isConfigured()) {
            log.info("공휴일 API 키 없음 → 기동 시 공휴일 동기화 생략");
            return;
        }
        int year = LocalDate.now().getYear();
        if (!holidayRepository.findByDateBetweenOrderByDateAsc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).isEmpty()) {
            return;
        }
        log.info("올해({}) 공휴일이 없어 기동 시 공휴일 API 동기화를 실행합니다.", year);
        syncService.syncCurrentAndNextYear();
    }
}
