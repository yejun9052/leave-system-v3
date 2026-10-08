package com.company.leave.backup;

import com.company.leave.backup.BackupSettings.Frequency;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.scheduling.support.CronExpression;

/**
 * 자동 백업 실행 시각 규칙(설정값 → cron). 서버 시간대는 Asia/Seoul.
 * <ul>
 *   <li>매일: 매일 runTime</li>
 *   <li>매주: dayOfWeek(1=월~7=일)의 runTime</li>
 *   <li>N시간마다: 0시부터 N시간 간격 정각(예: 5 → 0·5·10·15·20시)</li>
 * </ul>
 */
public record BackupSchedule(Frequency frequency, int dayOfWeek, LocalTime runTime, int intervalHours) {

    /** 다른 자동 작업 시각. 같은 시각이면 화면에 경고만 한다(막지 않음). */
    static final Map<LocalTime, String> OTHER_JOBS = Map.of(
            LocalTime.of(0, 10), "공휴일 동기화(매일 00:10)",
            LocalTime.of(1, 0), "연차 부여·소멸(매일 01:00)",
            LocalTime.of(9, 0), "연차 촉진 자동 발송(매일 09:00)");

    /** Spring cron(초 분 시 일 월 요일). */
    public String cron() {
        return switch (frequency) {
            case DAILY -> "0 %d %d * * *".formatted(runTime.getMinute(), runTime.getHour());
            case WEEKLY -> "0 %d %d * * %s".formatted(runTime.getMinute(), runTime.getHour(),
                    DayOfWeek.of(dayOfWeek).name().substring(0, 3));
            case HOURLY -> intervalHours >= 24 ? "0 0 0 * * *" : "0 0 0/%d * * *".formatted(intervalHours);
        };
    }

    /** after 이후 첫 실행 시각. */
    public LocalDateTime next(LocalDateTime after) {
        return CronExpression.parse(cron()).next(after);
    }

    /** 화면 표시: "매일 02:00", "매주 월요일 02:00", "6시간마다(0시부터 정각)". */
    public String label() {
        return switch (frequency) {
            case DAILY -> "매일 " + runTime;
            case WEEKLY -> "매주 " + DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.KOREAN) + " " + runTime;
            case HOURLY -> intervalHours >= 24 ? "매일 00:00" : intervalHours + "시간마다(0시부터 정각)";
        };
    }

    /** 다른 자동 작업과 같은 시각에 실행되면 그 작업 이름들. */
    public List<String> conflicts() {
        CronExpression cron = CronExpression.parse(cron());
        TreeSet<LocalTime> times = new TreeSet<>();
        LocalDateTime t = LocalDateTime.of(2026, 1, 5, 0, 0).minusSeconds(1); // 월요일 0시부터 일주일
        LocalDateTime end = t.plusWeeks(1);
        while ((t = cron.next(t)) != null && t.isBefore(end)) {
            times.add(t.toLocalTime());
        }
        List<String> jobs = new ArrayList<>();
        OTHER_JOBS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> times.contains(e.getKey()))
                .forEach(e -> jobs.add(e.getValue()));
        return jobs;
    }
}
