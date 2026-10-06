package com.company.leave.batch;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 자동 작업(스케줄러)의 마지막 실행 결과를 scheduled_job_runs 에 남긴다. 자동화 탭에서 보여 준다.
 * 기록에 실패해도 작업 자체는 막지 않는다(로그만 남김).
 */
@Component
public class JobRunRecorder {

    public static final String HOLIDAY_SYNC = "holiday-sync";
    public static final String LEAVE_GRANT = "leave-grant";
    public static final String PROMOTION_AUTO = "promotion-auto";

    private static final Logger log = LoggerFactory.getLogger(JobRunRecorder.class);
    private static final int MAX_MESSAGE = 500;

    private final JdbcTemplate jdbc;

    public JobRunRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 마지막 실행 결과.
     *
     * @param success 성공 true, 실패 false, 건너뜀(꺼짐·설정 없음) null
     */
    public record Run(String job, Instant startedAt, Instant finishedAt, Boolean success, String message) {
    }

    /**
     * 작업을 실행하고 결과를 남긴다. 실패하면 실패로 남기고 예외는 그대로 던진다.
     *
     * @param body 작업. 결과 요약 문구를 돌려준다(예: "20명 부여·재계산")
     */
    public void run(String job, Supplier<String> body) {
        Instant startedAt = Instant.now();
        save(job, startedAt, null, null, "실행 중");
        try {
            String summary = body.get();
            save(job, startedAt, Instant.now(), true, summary);
        } catch (RuntimeException ex) {
            save(job, startedAt, Instant.now(), false, "실패: " + ex.getMessage());
            throw ex;
        }
    }

    /** 실행하지 않고 건너뛴 사유를 남긴다(꺼져 있음, API 키 없음 등). */
    public void skipped(String job, String reason) {
        Instant now = Instant.now();
        save(job, now, now, null, reason);
    }

    public Map<String, Run> lastRuns() {
        try {
            return jdbc.query("select job, started_at, finished_at, success, message from scheduled_job_runs",
                            (rs, i) -> new Run(rs.getString("job"),
                                    toInstant(rs.getTimestamp("started_at")), toInstant(rs.getTimestamp("finished_at")),
                                    (Boolean) rs.getObject("success"), rs.getString("message")))
                    .stream().collect(Collectors.toMap(Run::job, r -> r));
        } catch (DataAccessException ex) {
            log.warn("자동 작업 실행 기록 조회 실패: {}", ex.getMessage());
            return Map.of();
        }
    }

    private void save(String job, Instant startedAt, Instant finishedAt, Boolean success, String message) {
        String text = message != null && message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
        try {
            jdbc.update("""
                    insert into scheduled_job_runs (job, started_at, finished_at, success, message)
                    values (?, ?, ?, ?, ?)
                    on conflict (job) do update set started_at = excluded.started_at,
                        finished_at = excluded.finished_at, success = excluded.success, message = excluded.message
                    """, job, Timestamp.from(startedAt), finishedAt != null ? Timestamp.from(finishedAt) : null,
                    success, text);
        } catch (DataAccessException ex) {
            log.warn("자동 작업 실행 기록 저장 실패 ({}): {}", job, ex.getMessage());
        }
    }

    private static Instant toInstant(Timestamp t) {
        return t != null ? t.toInstant() : null;
    }
}
