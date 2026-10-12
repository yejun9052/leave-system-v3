package com.company.leave.backup;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.support.CronExpression;

/**
 * 자동 백업 실행 시각: Spring cron 6칸(초 분 시 일 월 요일). 서버 시간대는 Asia/Seoul.
 * 예: "0 0 2 * * *" 매일 02:00, "0 30 3 * * MON,THU" 매주 월·목 03:30, "0 0 4 1 * *" 매월 1일 04:00, "0 0 0/6 * * *" 6시간마다.
 * 백업은 무거운 작업이라 1시간보다 자주 돌 수 없고, 초는 숫자 하나로 정한다.
 */
public record BackupSchedule(String cron) {

    /** 기본: 매일 02:00 */
    public static final String DEFAULT_CRON = "0 0 2 * * *";

    /** 다른 자동 작업 시각. 같은 시각이면 화면에 경고만 한다(막지 않음). */
    static final Map<LocalTime, String> OTHER_JOBS = Map.of(
            LocalTime.of(0, 10), "공휴일 동기화(매일 00:10)",
            LocalTime.of(1, 0), "연차 부여·소멸(매일 01:00)",
            LocalTime.of(9, 0), "연차 촉진 자동 발송(매일 09:00)");

    private static final Duration MIN_GAP = Duration.ofHours(1);
    private static final String[] DAY_NAMES = {"일", "월", "화", "수", "목", "금", "토", "일"};
    private static final List<String> DAY_CODES = List.of("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");

    public BackupSchedule {
        cron = cron == null ? "" : cron.trim().replaceAll("\\s+", " ");
    }

    /**
     * 저장할 수 있는 규칙인지 확인한다. 문제가 있으면 화면에 보여 줄 한국어 문장, 없으면 null.
     *
     * @param from 실행 간격을 살펴볼 기준 시각
     */
    public String problem(LocalDateTime from) {
        String[] f = cron.split(" ");
        if (f.length != 6) {
            return "6칸(초 분 시 일 월 요일)을 띄어 써서 입력해 주세요. 예: 0 0 2 * * *";
        }
        CronExpression expression;
        try {
            expression = CronExpression.parse(cron);
        } catch (IllegalArgumentException ex) {
            return "형식이 올바르지 않습니다. " + koreanReason(ex.getMessage());
        }
        if (!f[0].matches("\\d{1,2}")) {
            return "초는 0처럼 숫자 하나로 정해 주세요(백업은 1시간보다 자주 실행할 수 없습니다).";
        }
        LocalDateTime prev = expression.next(from);
        if (prev == null) {
            return "실행될 날짜가 없습니다(예: 2월 30일).";
        }
        for (int i = 0; i < 200; i++) {
            LocalDateTime t = expression.next(prev);
            if (t == null) {
                break;
            }
            if (Duration.between(prev, t).compareTo(MIN_GAP) < 0) {
                return "백업은 1시간보다 자주 실행할 수 없습니다.";
            }
            prev = t;
        }
        return null;
    }

    /** after 이후 첫 실행 시각(없으면 null). */
    public LocalDateTime next(LocalDateTime after) {
        return CronExpression.parse(cron).next(after);
    }

    /** after 이후 실행 시각 count 개. */
    public List<LocalDateTime> nextRuns(LocalDateTime after, int count) {
        CronExpression expression = CronExpression.parse(cron);
        List<LocalDateTime> runs = new ArrayList<>();
        LocalDateTime t = after;
        while (runs.size() < count && (t = expression.next(t)) != null) {
            runs.add(t);
        }
        return runs;
    }

    /** 화면 표시: "매일 02:00", "매주 월·목 03:30", "매월 1일 04:00", "6시간마다(0시부터, 매시 00분)" 등. 못 읽으면 cron 그대로. */
    public String label() {
        String[] f = cron.split(" ");
        if (f.length != 6) {
            return cron;
        }
        String min = f[1];
        String hour = f[2];
        String dom = any(f[3]) ? "*" : f[3];
        String month = f[4];
        String dow = any(f[5]) ? "*" : f[5];

        String time = time(hour, min);
        if (time == null) {
            return "cron " + cron;
        }
        String months = "*".equals(month) ? "" : month.replace(",", "·") + "월 ";
        if ("*".equals(dom) && "*".equals(dow)) {
            return months.isEmpty() ? (time.startsWith("매") || time.contains("마다") ? time : "매일 " + time)
                    : months + "매일 " + time;
        }
        if ("*".equals(dom)) {
            String days = days(dow);
            return days == null ? "cron " + cron : months + "매주 " + days + " " + time;
        }
        if ("*".equals(dow)) {
            String day = "L".equals(dom) ? "말일" : dom.replace(",", "·") + "일";
            return (months.isEmpty() ? "매월 " : months) + day + " " + time;
        }
        return "cron " + cron;
    }

    /** 다른 자동 작업과 같은 시각에 실행되면 그 작업 이름들(1년 동안 실행 시각 기준). */
    public List<String> conflicts() {
        CronExpression expression = CronExpression.parse(cron);
        TreeSet<LocalTime> times = new TreeSet<>();
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0).minusSeconds(1);
        LocalDateTime end = t.plusYears(1);
        for (int i = 0; i < 5000 && (t = expression.next(t)) != null && t.isBefore(end); i++) {
            times.add(t.toLocalTime());
        }
        List<String> jobs = new ArrayList<>();
        OTHER_JOBS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> times.contains(e.getKey()))
                .forEach(e -> jobs.add(e.getValue()));
        return jobs;
    }

    private static final Map<String, String> FIELD_NAMES = Map.of(
            "SecondOfMinute", "초", "MinuteOfHour", "분", "HourOfDay", "시",
            "DayOfMonth", "일", "MonthOfYear", "월", "DayOfWeek", "요일");
    private static final Pattern RANGE_ERROR = Pattern.compile(
            "Invalid value for (\\w+) \\(valid values? (\\d+) - (\\d+)\\): (\\S+)");

    /** Spring 의 영어 오류를 화면용 한국어로: "시 칸에는 0~23만 쓸 수 있습니다(입력: 25)." 못 읽으면 일반 안내. */
    static String koreanReason(String message) {
        Matcher m = RANGE_ERROR.matcher(message == null ? "" : message);
        if (m.find() && FIELD_NAMES.containsKey(m.group(1))) {
            return FIELD_NAMES.get(m.group(1)) + " 칸에는 " + m.group(2) + "~" + m.group(3) + "만 쓸 수 있습니다(입력: "
                    + m.group(4).replaceAll("[,:]$", "") + ").";
        }
        return "칸마다 쓸 수 있는 값을 확인해 주세요(예: 시 0~23, 요일 MON~SUN).";
    }

    private static boolean any(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    /** 시·분 → "02:00", "02:00·14:00", "6시간마다(0시부터, 매시 00분)", "매시간 30분". 못 읽으면 null. */
    private static String time(String hour, String min) {
        if (!min.matches("\\d{1,2}")) {
            return null;
        }
        String mm = "%02d".formatted(Integer.parseInt(min));
        if (hour.matches("\\d{1,2}(,\\d{1,2})*")) {
            List<String> times = new ArrayList<>();
            for (String h : hour.split(",")) {
                times.add("%02d:%s".formatted(Integer.parseInt(h), mm));
            }
            return String.join("·", times);
        }
        if ("*".equals(hour)) {
            return "매시간 " + mm + "분";
        }
        if (hour.matches("(\\*|\\d{1,2})/\\d{1,2}")) {
            String[] p = hour.split("/");
            String from = "*".equals(p[0]) ? "0" : String.valueOf(Integer.parseInt(p[0]));
            return Integer.parseInt(p[1]) + "시간마다(" + from + "시부터, 매번 " + mm + "분)";
        }
        return null;
    }

    /** 요일 칸 → "월·목", "월~금". 못 읽으면 null. */
    private static String days(String dow) {
        List<String> parts = new ArrayList<>();
        for (String part : dow.toUpperCase().split(",")) {
            String[] range = part.split("-");
            if (range.length == 2) {
                String a = day(range[0]);
                String b = day(range[1]);
                if (a == null || b == null) {
                    return null;
                }
                parts.add(a + "~" + b);
            } else {
                String d = day(part);
                if (d == null) {
                    return null;
                }
                parts.add(d);
            }
        }
        return String.join("·", parts) + "요일";
    }

    private static String day(String token) {
        if (token.matches("[0-7]")) {
            return DAY_NAMES[Integer.parseInt(token)];
        }
        int i = DAY_CODES.indexOf(token);
        return i < 0 ? null : DAY_NAMES[i];
    }
}
