package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.backup.BackupSettings.Frequency;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("자동 백업 실행 시각 규칙")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupScheduleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0); // 목요일

    @Test
    void 매일은_정한_시각에_실행한다() {
        BackupSchedule s = new BackupSchedule(Frequency.DAILY, 1, LocalTime.of(2, 0), 6);

        assertThat(s.cron()).isEqualTo("0 0 2 * * *");
        assertThat(s.label()).isEqualTo("매일 02:00");
        assertThat(s.next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 9, 2, 0));
    }

    @Test
    void 매주는_정한_요일의_시각에_실행한다() {
        BackupSchedule s = new BackupSchedule(Frequency.WEEKLY, 1, LocalTime.of(3, 30), 6);

        assertThat(s.label()).isEqualTo("매주 월요일 03:30");
        assertThat(s.next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 12, 3, 30));
        assertThat(new BackupSchedule(Frequency.WEEKLY, 7, LocalTime.of(23, 0), 6).next(NOW))
                .isEqualTo(LocalDateTime.of(2026, 10, 11, 23, 0));
    }

    @Test
    void N시간마다는_0시부터_N시간_간격_정각에_실행한다() {
        BackupSchedule five = new BackupSchedule(Frequency.HOURLY, 1, LocalTime.of(2, 0), 5);

        assertThat(five.label()).isEqualTo("5시간마다(0시부터 정각)");
        assertThat(five.next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 0));
        assertThat(five.next(LocalDateTime.of(2026, 10, 8, 20, 0))).isEqualTo(LocalDateTime.of(2026, 10, 9, 0, 0));
        assertThat(new BackupSchedule(Frequency.HOURLY, 1, LocalTime.of(2, 0), 24).next(NOW))
                .isEqualTo(LocalDateTime.of(2026, 10, 9, 0, 0));
    }

    @Test
    void 다른_자동_작업과_같은_시각이면_그_작업을_알려_준다() {
        assertThat(new BackupSchedule(Frequency.DAILY, 1, LocalTime.of(0, 10), 6).conflicts())
                .containsExactly("공휴일 동기화(매일 00:10)");
        assertThat(new BackupSchedule(Frequency.WEEKLY, 3, LocalTime.of(1, 0), 6).conflicts())
                .containsExactly("연차 부여·소멸(매일 01:00)");
        assertThat(new BackupSchedule(Frequency.HOURLY, 1, LocalTime.of(2, 0), 3).conflicts())
                .containsExactly("연차 촉진 자동 발송(매일 09:00)");
        assertThat(new BackupSchedule(Frequency.HOURLY, 1, LocalTime.of(2, 0), 1).conflicts())
                .containsExactly("연차 부여·소멸(매일 01:00)", "연차 촉진 자동 발송(매일 09:00)");
        assertThat(new BackupSchedule(Frequency.DAILY, 1, LocalTime.of(2, 0), 6).conflicts()).isEmpty();
    }
}
