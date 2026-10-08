package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** 자동 백업 보관 규칙: 일간·주간·월간 중 하나라도 해당하면 남기고, 수동 백업은 건드리지 않는다. */
@DisplayName("자동 백업 보관 정리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupRetentionTest {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static BackupFile file(LocalDateTime at, Kind kind) {
        return new BackupFile("annual_leave_" + at.format(STAMP) + "_" + kind.code() + ".dump", at, 1, kind);
    }

    /** 2026-06-01 ~ 2026-10-08 매일 02:00 자동 백업 + 10-08 08:00·14:00 추가 자동 백업 + 오래된 수동·복원 전 백업. */
    private static List<BackupFile> files() {
        List<BackupFile> files = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2026, 6, 1); !d.isAfter(LocalDate.of(2026, 10, 8)); d = d.plusDays(1)) {
            files.add(file(d.atTime(2, 0), Kind.AUTO));
        }
        files.add(file(LocalDateTime.of(2026, 10, 8, 8, 0), Kind.AUTO));
        files.add(file(LocalDateTime.of(2026, 10, 8, 14, 0), Kind.AUTO));
        files.add(file(LocalDateTime.of(2026, 1, 1, 9, 0), Kind.MANUAL));
        files.add(file(LocalDateTime.of(2026, 2, 1, 9, 0), Kind.PRE_RESTORE));
        return files;
    }

    private static Set<LocalDateTime> kept(List<BackupFile> all, List<BackupFile> deleted) {
        return all.stream().filter(f -> f.kind() == Kind.AUTO && !deleted.contains(f))
                .map(BackupFile::createdAt).collect(Collectors.toSet());
    }

    @Test
    void 일간_주간_월간_중_하나라도_해당하는_자동_백업만_남긴다() {
        List<BackupFile> all = files();

        List<BackupFile> deleted = BackupRetention.toDelete(all, 7, 4, 6);

        assertThat(kept(all, deleted)).containsExactlyInAnyOrder(
                // 일간 7일: 날짜별 마지막(10-08 은 14:00)
                LocalDateTime.of(2026, 10, 8, 14, 0), LocalDateTime.of(2026, 10, 7, 2, 0),
                LocalDateTime.of(2026, 10, 6, 2, 0), LocalDateTime.of(2026, 10, 5, 2, 0),
                LocalDateTime.of(2026, 10, 4, 2, 0), LocalDateTime.of(2026, 10, 3, 2, 0),
                LocalDateTime.of(2026, 10, 2, 2, 0),
                // 주간 4주: 주(월~일)별 마지막 — 10-05주는 10-08, 9-28주는 10-04(일), 9-21주는 9-27, 9-14주는 9-20
                LocalDateTime.of(2026, 9, 27, 2, 0), LocalDateTime.of(2026, 9, 20, 2, 0),
                // 월간 6개월(백업이 있는 달은 6~10월 5개뿐): 월별 마지막
                LocalDateTime.of(2026, 9, 30, 2, 0), LocalDateTime.of(2026, 8, 31, 2, 0),
                LocalDateTime.of(2026, 7, 31, 2, 0), LocalDateTime.of(2026, 6, 30, 2, 0));
        assertThat(deleted).extracting(BackupFile::createdAt)
                .contains(LocalDateTime.of(2026, 10, 8, 2, 0), LocalDateTime.of(2026, 10, 8, 8, 0),
                        LocalDateTime.of(2026, 6, 1, 2, 0));
    }

    @Test
    void 수동_백업과_복원_전_백업은_아무리_오래돼도_지우지_않는다() {
        List<BackupFile> deleted = BackupRetention.toDelete(files(), 1, 0, 0);

        assertThat(deleted).noneMatch(f -> f.kind() == Kind.MANUAL || f.kind() == Kind.PRE_RESTORE);
        assertThat(deleted).hasSize(files().size() - 3); // 수동·복원 전 각 1개, 최신 자동 1개만 남음
    }

    @Test
    void 보관을_모두_0으로_해도_방금_만든_자동_백업은_남긴다() {
        List<BackupFile> all = files();

        List<BackupFile> deleted = BackupRetention.toDelete(all, 0, 0, 0);

        assertThat(kept(all, deleted)).containsExactly(LocalDateTime.of(2026, 10, 8, 14, 0));
    }

    @Test
    void 백업이_며칠_빠져도_백업이_있는_날_기준으로_N일치를_남긴다() {
        List<BackupFile> all = List.of(
                file(LocalDateTime.of(2026, 10, 8, 2, 0), Kind.AUTO),
                file(LocalDateTime.of(2026, 9, 1, 2, 0), Kind.AUTO),   // 서버가 한 달 꺼져 있었음
                file(LocalDateTime.of(2026, 8, 31, 2, 0), Kind.AUTO),
                file(LocalDateTime.of(2026, 8, 30, 2, 0), Kind.AUTO));

        assertThat(BackupRetention.toDelete(all, 3, 0, 0)).extracting(BackupFile::createdAt)
                .containsExactly(LocalDateTime.of(2026, 8, 30, 2, 0));
    }

    @Test
    void 지울_파일은_오래된_것부터_돌려준다() {
        List<BackupFile> deleted = BackupRetention.toDelete(files(), 7, 4, 6);

        assertThat(deleted).extracting(BackupFile::createdAt).isSorted();
        assertThat(BackupRetention.weekStart(LocalDate.of(2026, 10, 4))).isEqualTo(LocalDate.of(2026, 9, 28));
    }
}
