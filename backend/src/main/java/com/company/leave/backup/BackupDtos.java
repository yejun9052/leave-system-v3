package com.company.leave.backup;

import com.company.leave.backup.BackupSettings.Frequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class BackupDtos {

    private BackupDtos() {
    }

    /** 백업 종류. 파일 이름 끝에 code 가 붙는다. */
    public enum Kind {
        MANUAL("manual"),
        AUTO("auto");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        static Kind ofCode(String code) {
            for (Kind k : values()) {
                if (k.code.equals(code)) {
                    return k;
                }
            }
            throw new IllegalArgumentException(code);
        }
    }

    /**
     * @param createdAt 파일 이름에 적힌 만든 시각
     * @param size      바이트
     */
    public record BackupFile(String fileName, LocalDateTime createdAt, long size, Kind kind) {
    }

    /**
     * 백업 탭 상단 정보와 목록.
     *
     * @param dir         백업 폴더(서버 기준 경로)
     * @param usableBytes 그 폴더가 있는 디스크의 남은 공간
     * @param running     지금 백업이 진행 중인지
     * @param files       최신순
     */
    public record Overview(String dir, long usableBytes, boolean running, List<BackupFile> files) {
    }

    /**
     * 자동 백업 설정.
     *
     * @param scheduleLabel 실행 시각 설명(예: "매일 02:00")
     * @param nextRunAt     다음 실행 시각(꺼져 있으면 null)
     * @param warnings      다른 자동 작업과 같은 시각이면 그 안내(막지는 않음)
     */
    public record Settings(boolean enabled, Frequency frequency, int dayOfWeek, LocalTime runTime, int intervalHours,
                           int keepDaily, int keepWeekly, int keepMonthly, String scheduleLabel,
                           LocalDateTime nextRunAt, List<String> warnings) {
    }

    /** 자동 백업 설정 변경. dayOfWeek: 1=월~7=일, runTime: "02:00" */
    public record SettingsRequest(
            @NotNull Boolean enabled,
            @NotNull Frequency frequency,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime runTime,
            @Min(1) @Max(24) int intervalHours,
            @Min(0) @Max(60) int keepDaily,
            @Min(0) @Max(52) int keepWeekly,
            @Min(0) @Max(24) int keepMonthly) {
    }
}
