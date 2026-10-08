package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** 자동 백업 보관 규칙: 최근 N개월 동안의 자동 백업은 모두 남기고 그보다 오래된 자동 백업만 지운다. */
@DisplayName("자동 백업 보관 정리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupRetentionTest {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 14, 0);

    private static BackupFile file(LocalDateTime at, Kind kind) {
        return new BackupFile("annual_leave_" + at.format(STAMP) + "_" + kind.code() + ".dump", at, 1, kind);
    }

    /** 2026-01-01 ~ 2026-10-08 매일 02:00 자동 백업 + 오래된 수동·복원 전 백업. */
    private static List<BackupFile> files() {
        List<BackupFile> files = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2026, 1, 1); !d.isAfter(LocalDate.of(2026, 10, 8)); d = d.plusDays(1)) {
            files.add(file(d.atTime(2, 0), Kind.AUTO));
        }
        files.add(file(LocalDateTime.of(2026, 1, 1, 9, 0), Kind.MANUAL));
        files.add(file(LocalDateTime.of(2026, 2, 1, 9, 0), Kind.PRE_RESTORE));
        return files;
    }

    @Test
    void 보관_기간_안의_자동_백업은_하루치도_빠짐없이_남기고_그보다_오래된_것만_지운다() {
        List<BackupFile> all = files();

        List<BackupFile> deleted = BackupRetention.toDelete(all, 6, NOW);

        // 6개월 전 = 2026-04-08 14:00. 04-08 02:00 까지는 지우고 04-09 02:00 부터 남긴다
        assertThat(deleted).extracting(BackupFile::createdAt)
                .startsWith(LocalDateTime.of(2026, 1, 1, 2, 0))
                .endsWith(LocalDateTime.of(2026, 4, 8, 2, 0))
                .hasSize(98)  // 1/1 ~ 4/8
                .isSorted();
        List<LocalDateTime> kept = all.stream().filter(f -> f.kind() == Kind.AUTO && !deleted.contains(f))
                .map(BackupFile::createdAt).toList();
        assertThat(kept).hasSize(183)  // 4/9 ~ 10/8 매일
                .contains(LocalDateTime.of(2026, 4, 9, 2, 0), LocalDateTime.of(2026, 7, 15, 2, 0),
                        LocalDateTime.of(2026, 10, 8, 2, 0));
    }

    @Test
    void 수동_백업과_복원_전_백업은_아무리_오래돼도_지우지_않는다() {
        assertThat(BackupRetention.toDelete(files(), 1, NOW))
                .noneMatch(f -> f.kind() == Kind.MANUAL || f.kind() == Kind.PRE_RESTORE);
    }

    @Test
    void 서버를_오래_꺼_뒀어도_가장_최근_자동_백업은_남긴다() {
        List<BackupFile> old = List.of(
                file(LocalDateTime.of(2025, 12, 31, 2, 0), Kind.AUTO),
                file(LocalDateTime.of(2025, 12, 30, 2, 0), Kind.AUTO));

        assertThat(BackupRetention.toDelete(old, 6, NOW)).extracting(BackupFile::createdAt)
                .containsExactly(LocalDateTime.of(2025, 12, 30, 2, 0));
    }

    @Test
    void 보관_기간을_늘리면_더_오래된_백업까지_남는다() {
        assertThat(BackupRetention.toDelete(files(), 12, NOW)).isEmpty();
        assertThat(BackupRetention.toDelete(files(), 1, NOW)).extracting(BackupFile::createdAt)
                .endsWith(LocalDateTime.of(2026, 9, 8, 2, 0));
    }
}
