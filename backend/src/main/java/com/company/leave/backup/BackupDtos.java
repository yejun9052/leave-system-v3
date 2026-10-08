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

    /**
     * 백업 종류. 파일 이름 끝에 code 가 붙는다. IMPORTED 는 서버 관리자가 import/ 폴더에 직접 넣은 외부 파일
     * (이름 규칙이 따로 있고 화면에서 지울 수 없다).
     */
    public enum Kind {
        MANUAL("manual"),
        AUTO("auto"),
        /** 복원하기 직전 자동으로 만든 백업 */
        PRE_RESTORE("pre-restore"),
        IMPORTED("import");

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
     * @param createdAt 파일 이름에 적힌 만든 시각(가져온 파일은 파일 수정 시각)
     * @param size      바이트
     * @param dbVersion 백업 정보 파일(.json)에 적힌 DB 버전. 정보 파일이 없으면 null(복원할 때 백업 안에서 확인)
     */
    public record BackupFile(String fileName, LocalDateTime createdAt, long size, Kind kind, String dbVersion) {

        public BackupFile(String fileName, LocalDateTime createdAt, long size, Kind kind) {
            this(fileName, createdAt, size, kind, null);
        }
    }

    /**
     * 백업 탭 상단 정보와 목록.
     *
     * @param dir         백업 폴더(서버 기준 경로)
     * @param usableBytes 그 폴더가 있는 디스크의 남은 공간
     * @param running     지금 백업이 진행 중인지
     * @param files       최신순
     * @param imports     import/ 폴더의 가져온 파일(최신순)
     * @param dbVersion   지금 앱 DB 버전
     */
    public record Overview(String dir, long usableBytes, boolean running, List<BackupFile> files,
                           List<BackupFile> imports, String dbVersion) {
    }

    /**
     * 자동 백업 설정.
     *
     * @param scheduleLabel 실행 시각 설명(예: "매일 02:00")
     * @param nextRunAt     다음 실행 시각(꺼져 있으면 null)
     * @param warnings      다른 자동 작업과 같은 시각이면 그 안내(막지는 않음)
     */
    public record Settings(boolean enabled, Frequency frequency, int dayOfWeek, LocalTime runTime, int intervalHours,
                           int keepMonths, String scheduleLabel,
                           LocalDateTime nextRunAt, List<String> warnings) {
    }

    /** 자동 백업 설정 변경. dayOfWeek: 1=월~7=일, runTime: "02:00", keepMonths: 보관 기간(개월) */
    public record SettingsRequest(
            @NotNull Boolean enabled,
            @NotNull Frequency frequency,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime runTime,
            @Min(1) @Max(24) int intervalHours,
            @Min(1) @Max(24) int keepMonths) {
    }
}
