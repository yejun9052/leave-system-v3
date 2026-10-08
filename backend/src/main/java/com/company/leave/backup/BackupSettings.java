package com.company.leave.backup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;

/** 자동 백업 설정(한 줄, id = 1). 주기·시각과 일간·주간·월간 보관 개수. */
@Entity
@Table(name = "backup_settings")
@Getter
public class BackupSettings {

    public static final int ID = 1;

    /** 매일 / 매주(요일) / N시간마다(0시부터 정각). */
    public enum Frequency { DAILY, WEEKLY, HOURLY }

    @Id
    private Integer id;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    /** 1 = 월요일 … 7 = 일요일 (매주일 때). */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    /** 매일·매주일 때 실행 시각. */
    @Column(name = "run_time", nullable = false)
    private LocalTime runTime;

    /** N시간마다일 때 간격(1~24). */
    @Column(name = "interval_hours", nullable = false)
    private int intervalHours;

    @Column(name = "keep_daily", nullable = false)
    private int keepDaily;

    @Column(name = "keep_weekly", nullable = false)
    private int keepWeekly;

    @Column(name = "keep_monthly", nullable = false)
    private int keepMonthly;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BackupSettings() {
    }

    /** 기본값(마이그레이션과 같음): 켜짐, 매일 02:00, 보관 7일·4주·6개월. */
    public static BackupSettings defaults() {
        BackupSettings s = new BackupSettings();
        s.id = ID;
        s.update(true, Frequency.DAILY, 1, LocalTime.of(2, 0), 6, 7, 4, 6);
        return s;
    }

    public void update(boolean enabled, Frequency frequency, int dayOfWeek, LocalTime runTime, int intervalHours,
                       int keepDaily, int keepWeekly, int keepMonthly) {
        this.enabled = enabled;
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime.withSecond(0).withNano(0);
        this.intervalHours = intervalHours;
        this.keepDaily = keepDaily;
        this.keepWeekly = keepWeekly;
        this.keepMonthly = keepMonthly;
        this.updatedAt = Instant.now();
    }

    /** 실행 시각 규칙. */
    public BackupSchedule schedule() {
        return new BackupSchedule(frequency, dayOfWeek, runTime, intervalHours);
    }
}
