package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.batch.JobRunRecorder;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자동 백업 스케줄. 다른 배치처럼 고정 cron(@Scheduled)이 아니라, 설정값으로 {@link TaskScheduler} 에 등록하고
 * 설정이 바뀌면 기존 예약을 취소하고 다시 등록한다(재시작 불필요). 실행 결과는 자동화 탭에 "자동 백업"으로 남고,
 * 실패하면 시스템 관리자·인사관리자에게 알림·메일을 보낸다.
 */
@Component
public class AutoBackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoBackupScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TaskScheduler taskScheduler;
    private final BackupSettingsService settingsService;
    private final BackupService backupService;
    private final JobRunRecorder recorder;
    private final BackupMessenger messenger;
    private final MaintenanceMode maintenance;

    private ScheduledFuture<?> future;
    private CronTrigger trigger;

    public AutoBackupScheduler(TaskScheduler taskScheduler, BackupSettingsService settingsService,
                               BackupService backupService, JobRunRecorder recorder, BackupMessenger messenger,
                               MaintenanceMode maintenance) {
        this.maintenance = maintenance;
        this.taskScheduler = taskScheduler;
        this.settingsService = settingsService;
        this.backupService = backupService;
        this.recorder = recorder;
        this.messenger = messenger;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reschedule();
    }

    /** 복원으로 설정 표도 되돌아갔으니 다시 읽어 등록. */
    @EventListener(RestoreService.Restored.class)
    public void onRestored() {
        reschedule();
    }

    /** 설정 저장이 커밋된 뒤 다시 등록. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSettingsChanged(BackupSettingsService.Changed event) {
        reschedule();
    }

    /** 지금 설정으로 다시 등록한다. 진행 중인 백업은 끝까지 둔다. 꺼져 있으면 등록하지 않는다. */
    public synchronized void reschedule() {
        if (future != null) {
            future.cancel(false);
            future = null;
            trigger = null;
        }
        BackupSettings settings = settingsService.current();
        if (!settings.isEnabled()) {
            log.info("[자동 백업] 꺼져 있어 예약하지 않음");
            return;
        }
        BackupSchedule schedule = settings.schedule();
        trigger = new CronTrigger(schedule.cron(), SEOUL);
        future = taskScheduler.schedule(this::runAuto, trigger);
        log.info("[자동 백업] 예약: {} ({})", schedule.label(), schedule.cron());
    }

    /** 지금 등록된 실행 규칙(꺼져 있으면 null). */
    synchronized CronTrigger trigger() {
        return trigger;
    }

    void runAuto() {
        if (maintenance.isActive()) {
            recorder.skipped(JobRunRecorder.BACKUP_AUTO, "복원 중(점검 모드)이라 건너뜀");
            return;
        }
        log.info("[자동 백업] 시작");
        try {
            recorder.run(JobRunRecorder.BACKUP_AUTO, this::backupAndCleanup);
        } catch (RuntimeException ex) {
            log.warn("[자동 백업] 실패: {}", ex.getMessage());
            messenger.autoBackupFailed(ex.getMessage());
        }
    }

    /** 백업이 성공한 뒤에만 보관 정리. 백업이 실패하면 예외가 나서 아무것도 지우지 않는다. */
    private String backupAndCleanup() {
        BackupFile file = backupService.backup(Kind.AUTO);
        BackupSettings settings = settingsService.current();
        List<String> deleted = backupService.cleanupAuto(settings.getKeepMonths());
        return summary(file, deleted);
    }

    /** "annual_leave_..._auto.dump (98 KB) · 보관 정리 2개 삭제: a, b" */
    static String summary(BackupFile file, List<String> deleted) {
        String made = file.fileName() + " (" + file.size() / 1024 + " KB)";
        return deleted.isEmpty() ? made : made + " · 보관 정리 " + deleted.size() + "개 삭제: " + String.join(", ", deleted);
    }
}
