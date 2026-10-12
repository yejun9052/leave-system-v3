package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.Settings;
import com.company.leave.backup.BackupDtos.SchedulePreview;
import com.company.leave.backup.BackupDtos.SettingsRequest;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 백업 설정 조회·변경. 저장하면 {@link Changed} 를 알려 스케줄이 재시작 없이 바로 바뀐다
 * ({@link AutoBackupScheduler}).
 */
@Service
public class BackupSettingsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 화면에 보여 줄 앞으로의 실행 시각 개수 */
    private static final int NEXT_RUNS = 5;

    /** 설정이 바뀌었음(커밋 뒤 스케줄 다시 등록). */
    public record Changed() {
    }

    private final BackupSettingsRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public BackupSettingsService(BackupSettingsRepository repository, ApplicationEventPublisher events) {
        this(repository, events, Clock.system(SEOUL));
    }

    BackupSettingsService(BackupSettingsRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    /** 지금 설정(행이 없으면 기본값). */
    @Transactional(readOnly = true)
    public BackupSettings current() {
        return repository.findById(BackupSettings.SINGLETON_ID).orElseGet(BackupSettings::defaults);
    }

    @Transactional(readOnly = true)
    public Settings get() {
        return toResponse(current());
    }

    /** @throws BusinessException 실행 시각(cron)이 올바르지 않으면 INVALID_INPUT(이유 포함) */
    @Transactional
    public Settings update(SettingsRequest req) {
        BackupSchedule schedule = new BackupSchedule(req.cron());
        String problem = schedule.problem(LocalDateTime.now(clock));
        if (problem != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자동 백업 실행 시각: " + problem);
        }
        BackupSettings settings = repository.findById(BackupSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(BackupSettings.defaults()));
        settings.update(req.enabled(), schedule.cron(), req.keepMonths());
        events.publishEvent(new Changed());
        return toResponse(settings);
    }

    /** 저장하지 않고 cron 을 해석해 설명·다음 실행 시각·겹침 안내를 돌려준다(설정 화면 미리보기). */
    public SchedulePreview preview(String cron) {
        BackupSchedule schedule = new BackupSchedule(cron);
        String problem = schedule.problem(LocalDateTime.now(clock));
        if (problem != null) {
            return new SchedulePreview(schedule.cron(), problem, null, List.of(), List.of());
        }
        return new SchedulePreview(schedule.cron(), null, schedule.label(),
                schedule.nextRuns(LocalDateTime.now(clock), NEXT_RUNS), warnings(schedule));
    }

    private Settings toResponse(BackupSettings s) {
        BackupSchedule schedule = s.schedule();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime next = s.isEnabled() ? schedule.next(now) : null;
        return new Settings(s.isEnabled(), schedule.cron(), s.getKeepMonths(), schedule.label(), next,
                schedule.nextRuns(now, NEXT_RUNS), warnings(schedule));
    }

    private static List<String> warnings(BackupSchedule schedule) {
        return schedule.conflicts().stream()
                .map(job -> job + "과 같은 시각에 실행됩니다. 서로 겹치지 않는 시각을 권장합니다.")
                .toList();
    }
}
