package com.company.leave.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.holiday.HolidaySyncService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
@DisplayName("공휴일 동기화 스케줄러")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidaySyncSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private HolidaySyncService syncService;

    private HolidaySyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HolidaySyncScheduler(syncService);
    }

    private static Scheduled dailySyncSchedule() throws NoSuchMethodException {
        return HolidaySyncScheduler.class.getMethod("dailySync").getAnnotation(Scheduled.class);
    }

    private static ZonedDateTime seoul(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, SEOUL);
    }

    @Test
    void 동기화는_서울_시간대_기준으로_실행된다() throws Exception {
        assertThat(dailySyncSchedule().zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 동기화는_매일_0시_10분에_실행된다() throws Exception {
        CronExpression cron = CronExpression.parse(dailySyncSchedule().cron());

        assertThat(cron.next(seoul(2026, 3, 5, 0, 9))).isEqualTo(seoul(2026, 3, 5, 0, 10));
        assertThat(cron.next(seoul(2026, 3, 5, 0, 10))).isEqualTo(seoul(2026, 3, 6, 0, 10));
        assertThat(cron.next(seoul(2026, 12, 31, 0, 10))).isEqualTo(seoul(2027, 1, 1, 0, 10));
    }

    @Test
    void 키가_설정되지_않으면_동기화를_호출하지_않는다() {
        when(syncService.isConfigured()).thenReturn(false);

        scheduler.dailySync();

        verify(syncService, never()).syncCurrentAndNextYear();
    }

    @Test
    void 키가_설정돼_있으면_올해와_내년_동기화를_한_번_호출한다() {
        when(syncService.isConfigured()).thenReturn(true);

        scheduler.dailySync();

        verify(syncService, times(1)).syncCurrentAndNextYear();
    }

    @Test
    void 동기화가_예외를_던지면_삼키지_않고_그대로_전파한다() {
        IllegalStateException failure = new IllegalStateException("API 장애");
        when(syncService.isConfigured()).thenReturn(true);
        when(syncService.syncCurrentAndNextYear()).thenThrow(failure);

        assertThatThrownBy(() -> scheduler.dailySync()).isSameAs(failure);
    }
}
