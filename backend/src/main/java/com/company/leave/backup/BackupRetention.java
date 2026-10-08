package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 자동 백업 보관 규칙(일간·주간·월간). 자동 백업 파일만 대상이고 수동·복원 전 백업은 건드리지 않는다.
 * <ul>
 *   <li>일간: 날짜별 마지막 백업을, 백업이 있는 최근 N일치</li>
 *   <li>주간: 주(월~일)별 마지막 백업을, 백업이 있는 최근 N주치</li>
 *   <li>월간: 월별 마지막 백업을, 백업이 있는 최근 N개월치</li>
 * </ul>
 * 셋 중 하나라도 해당하면 남기고, 어디에도 해당하지 않는 것만 지운다. 가장 최근 자동 백업(방금 만든 것)은 항상 남긴다.
 * "최근 N일"은 달력이 아니라 백업이 있는 날 기준이라, 서버가 며칠 꺼져 있었어도 보관분이 한꺼번에 사라지지 않는다.
 */
final class BackupRetention {

    private BackupRetention() {
    }

    /** 지울 자동 백업(오래된 것부터). */
    static List<BackupFile> toDelete(Collection<BackupFile> files, int keepDaily, int keepWeekly, int keepMonthly) {
        List<BackupFile> autos = files.stream()
                .filter(f -> f.kind() == Kind.AUTO)
                .sorted(Comparator.comparing(BackupFile::createdAt).reversed())
                .toList();
        if (autos.isEmpty()) {
            return List.of();
        }
        Set<BackupFile> keep = new HashSet<>();
        keep.add(autos.get(0));
        keep.addAll(latestPerPeriod(autos, LocalDateTime::toLocalDate, keepDaily));
        keep.addAll(latestPerPeriod(autos, t -> weekStart(t.toLocalDate()), keepWeekly));
        keep.addAll(latestPerPeriod(autos, YearMonth::from, keepMonthly));
        return autos.reversed().stream().filter(f -> !keep.contains(f)).toList();
    }

    /** 최신순 목록에서 기간마다 처음 나오는 것(그 기간의 마지막 백업)을 최근 n개 기간만큼. */
    private static List<BackupFile> latestPerPeriod(List<BackupFile> newestFirst,
                                                    Function<LocalDateTime, ?> period, int n) {
        Map<Object, BackupFile> latest = new LinkedHashMap<>();
        for (BackupFile f : newestFirst) {
            if (latest.size() >= n && !latest.containsKey(period.apply(f.createdAt()))) {
                break;
            }
            latest.putIfAbsent(period.apply(f.createdAt()), f);
        }
        return List.copyOf(latest.values());
    }

    /** 그 날이 속한 주(월~일)의 월요일. */
    static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
