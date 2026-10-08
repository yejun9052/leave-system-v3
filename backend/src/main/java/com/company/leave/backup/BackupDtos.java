package com.company.leave.backup;

import java.time.LocalDateTime;
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
}
