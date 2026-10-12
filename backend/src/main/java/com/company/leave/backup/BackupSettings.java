package com.company.leave.backup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/** 자동 백업 설정(한 줄, id = 1). 주기·시각과 보관 기간(개월). */
@Entity
@Table(name = "backup_settings")
@Getter
public class BackupSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private boolean enabled;

    /** 실행 시각: cron 6칸(초 분 시 일 월 요일), 예 "0 10 0 * * *" = 매일 00:10. {@link BackupSchedule} */
    @Column(nullable = false, length = 100)
    private String cron;

    /** 최근 N개월 동안의 자동 백업은 모두 보관(1~24). */
    @Column(name = "keep_months", nullable = false)
    private int keepMonths;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BackupSettings() {
    }

    /** 기본값(마이그레이션과 같음): 켜짐, 매일 02:00, 6개월 보관. */
    public static BackupSettings defaults() {
        BackupSettings s = new BackupSettings();
        s.id = SINGLETON_ID;
        s.update(true, BackupSchedule.DEFAULT_CRON, 6);
        return s;
    }

    /** cron 은 {@link BackupSchedule#problem} 으로 미리 확인한 값이어야 한다. */
    public void update(boolean enabled, String cron, int keepMonths) {
        this.enabled = enabled;
        this.cron = new BackupSchedule(cron).cron();
        this.keepMonths = keepMonths;
        this.updatedAt = Instant.now();
    }

    /** 실행 시각 규칙. */
    public BackupSchedule schedule() {
        return new BackupSchedule(cron);
    }
}
