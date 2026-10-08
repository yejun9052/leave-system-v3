package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.Settings;
import com.company.leave.backup.BackupDtos.SettingsRequest;
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

    @Transactional
    public Settings update(SettingsRequest req) {
        BackupSettings settings = repository.findById(BackupSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(BackupSettings.defaults()));
        settings.update(req.enabled(), req.frequency(), req.dayOfWeek(), req.runTime(), req.intervalHours(),
                req.keepMonths());
        events.publishEvent(new Changed());
        return toResponse(settings);
    }

    private Settings toResponse(BackupSettings s) {
        BackupSchedule schedule = s.schedule();
        LocalDateTime next = s.isEnabled() ? schedule.next(LocalDateTime.now(clock)) : null;
        List<String> warnings = schedule.conflicts().stream()
                .map(job -> job + "과 같은 시각에 실행됩니다. 서로 겹치지 않는 시각을 권장합니다.")
                .toList();
        return new Settings(s.isEnabled(), s.getFrequency(), s.getDayOfWeek(), s.getRunTime(), s.getIntervalHours(),
                s.getKeepMonths(), schedule.label(), next, warnings);
    }
}
