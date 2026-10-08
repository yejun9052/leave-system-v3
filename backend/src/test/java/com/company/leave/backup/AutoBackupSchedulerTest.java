package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupDtos.SettingsRequest;
import com.company.leave.backup.BackupSettings.Frequency;
import com.company.leave.batch.JobRunRecorder;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/** 자동 백업 스케줄: 설정을 바꾸면 예약이 바로 바뀌고, 꺼지면 예약을 지우고, 실패하면 알린다. */
@DisplayName("자동 백업 스케줄")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AutoBackupSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2026, 10, 8, 12, 0).atZone(SEOUL).toInstant(), SEOUL);

    private final BackupSettings stored = BackupSettings.defaults();
    private BackupSettingsRepository repository;
    private TaskScheduler taskScheduler;
    private ScheduledFuture<?> future;
    private BackupService backupService;
    private JobRunRecorder recorder;
    private BackupMessenger messenger;
    private BackupSettingsService settingsService;
    private AutoBackupScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(BackupSettingsRepository.class);
        when(repository.findById(BackupSettings.ID)).thenReturn(Optional.of(stored));
        taskScheduler = mock(TaskScheduler.class);
        future = mock(ScheduledFuture.class);
        doAnswer(i -> future).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        backupService = mock(BackupService.class);
        recorder = mock(JobRunRecorder.class);
        messenger = mock(BackupMessenger.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        settingsService = new BackupSettingsService(repository, events, CLOCK);
        // 저장이 커밋되면 스케줄러가 다시 등록한다(이벤트 리스너를 직접 연결)
        doAnswer(i -> {
            scheduler.onSettingsChanged((BackupSettingsService.Changed) i.getArgument(0));
            return null;
        }).when(events).publishEvent(any(Object.class));
        scheduler = new AutoBackupScheduler(taskScheduler, settingsService, backupService, recorder, messenger);
    }

    private static LocalDateTime nextRun(CronTrigger trigger) {
        Instant now = CLOCK.instant();
        SimpleTriggerContext ctx = new SimpleTriggerContext(Clock.fixed(now, SEOUL));
        return LocalDateTime.ofInstant(trigger.nextExecution(ctx), SEOUL);
    }

    private static SettingsRequest request(boolean enabled, Frequency frequency, LocalTime time, int hours) {
        return new SettingsRequest(enabled, frequency, 3, time, hours, 7, 4, 6);
    }

    @Test
    void 시작하면_기본_설정인_매일_02시로_예약한다() {
        scheduler.onReady();

        assertThat(nextRun(scheduler.trigger())).isEqualTo(LocalDateTime.of(2026, 10, 9, 2, 0));
    }

    @Test
    void 설정을_바꾸면_기존_예약을_취소하고_다음_실행_시각이_바로_바뀐다() {
        scheduler.onReady();

        BackupDtos.Settings saved = settingsService.update(request(true, Frequency.DAILY, LocalTime.of(12, 3), 6));

        verify(future).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
        assertThat(nextRun(scheduler.trigger())).isEqualTo(LocalDateTime.of(2026, 10, 8, 12, 3));
        assertThat(saved.nextRunAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 12, 3));
        assertThat(saved.scheduleLabel()).isEqualTo("매일 12:03");
    }

    @Test
    void N시간마다로_바꾸면_다음_정각_간격으로_바뀐다() {
        scheduler.onReady();

        settingsService.update(request(true, Frequency.HOURLY, LocalTime.of(2, 0), 5));

        assertThat(nextRun(scheduler.trigger())).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 0));
    }

    @Test
    void 끄면_예약을_취소하고_다시_등록하지_않는다() {
        scheduler.onReady();

        BackupDtos.Settings saved = settingsService.update(request(false, Frequency.DAILY, LocalTime.of(2, 0), 6));

        verify(future).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class));
        assertThat(scheduler.trigger()).isNull();
        assertThat(saved.nextRunAt()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 자동_백업은_auto_종류로_만들고_실행_기록을_남긴다() {
        when(backupService.backup(Kind.AUTO)).thenReturn(new BackupFile("annual_leave_20261009_020000_auto.dump",
                LocalDateTime.of(2026, 10, 9, 2, 0), 2048, Kind.AUTO));
        doAnswer(i -> ((Supplier<String>) i.getArgument(1)).get())
                .when(recorder).run(eq(JobRunRecorder.BACKUP_AUTO), any());

        when(backupService.cleanupAuto(7, 4, 6)).thenReturn(List.of("annual_leave_20260901_020000_auto.dump"));

        scheduler.runAuto();

        verify(backupService).backup(Kind.AUTO);
        verify(backupService).cleanupAuto(7, 4, 6);
        verify(messenger, never()).autoBackupFailed(anyString());
    }

    @Test
    void 실행_기록_요약에_만든_파일과_지운_파일을_남긴다() {
        BackupFile made = new BackupFile("annual_leave_20261009_020000_auto.dump", LocalDateTime.of(2026, 10, 9, 2, 0),
                100 * 1024, Kind.AUTO);

        assertThat(AutoBackupScheduler.summary(made, List.of())).isEqualTo("annual_leave_20261009_020000_auto.dump (100 KB)");
        assertThat(AutoBackupScheduler.summary(made, List.of("a.dump", "b.dump")))
                .isEqualTo("annual_leave_20261009_020000_auto.dump (100 KB) · 보관 정리 2개 삭제: a.dump, b.dump");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 자동_백업이_실패하면_아무것도_지우지_않고_관리자에게_알린다() {
        when(backupService.backup(Kind.AUTO))
                .thenThrow(new BusinessException(ErrorCode.BACKUP_FAILED, "백업에 실패했습니다. pg_dump 종료 코드 1"));
        doAnswer(i -> ((Supplier<String>) i.getArgument(1)).get())
                .when(recorder).run(eq(JobRunRecorder.BACKUP_AUTO), any());

        scheduler.runAuto();

        verify(messenger).autoBackupFailed("백업에 실패했습니다. pg_dump 종료 코드 1");
        verify(backupService, never()).cleanupAuto(anyInt(),
                anyInt(), anyInt());
    }

    @Test
    void 다른_작업과_같은_시각이면_경고를_돌려준다() {
        BackupDtos.Settings saved = settingsService.update(request(true, Frequency.DAILY, LocalTime.of(0, 10), 6));

        assertThat(saved.warnings()).singleElement().asString().contains("공휴일 동기화");
    }
}
