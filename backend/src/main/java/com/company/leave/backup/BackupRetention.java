package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 자동 백업 보관 규칙: 최근 N개월 동안의 자동 백업은 모두 남기고, 그보다 오래된 자동 백업만 지운다.
 * <ul>
 *   <li>예: 6개월, 오늘 10-08 14:00 → 04-08 14:00 이후 자동 백업은 모두 보관, 그 전 것은 삭제</li>
 *   <li>가장 최근 자동 백업은 항상 남긴다(서버를 오래 꺼 뒀다 켜도 자동 백업이 0개가 되지 않게)</li>
 *   <li>수동·복원 전 백업은 대상이 아니다(관리자가 목록에서 지운다)</li>
 * </ul>
 */
final class BackupRetention {

    private BackupRetention() {
    }

    /** 지울 자동 백업(오래된 것부터). */
    static List<BackupFile> toDelete(Collection<BackupFile> files, int keepMonths, LocalDateTime now) {
        List<BackupFile> autos = files.stream()
                .filter(f -> f.kind() == Kind.AUTO)
                .sorted(Comparator.comparing(BackupFile::createdAt).reversed())
                .toList();
        if (autos.isEmpty()) {
            return List.of();
        }
        LocalDateTime cutoff = now.minusMonths(keepMonths);
        return autos.subList(1, autos.size()).reversed().stream()
                .filter(f -> f.createdAt().isBefore(cutoff))
                .toList();
    }
}
