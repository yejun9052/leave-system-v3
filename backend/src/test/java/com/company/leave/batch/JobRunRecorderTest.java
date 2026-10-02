package com.company.leave.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 자동 작업 실행 기록(scheduled_job_runs): 시작·성공·실패·건너뜀 기록, 기록 실패가 작업을 막지 않음, 긴 문구 자르기,
 * 마지막 실행 결과 읽기. JdbcTemplate 은 가짜(mock)로 두고 보낸 값을 확인한다.
 * update 인자: (sql, job, started_at, finished_at, success, message).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("자동 작업 실행 기록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JobRunRecorderTest {

    @Mock private JdbcTemplate jdbc;

    private JobRunRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new JobRunRecorder(jdbc);
    }

    @Test
    void 실행하면_시작을_남기고_끝나면_성공과_결과_요약을_남긴다() {
        recorder.run("leave-grant", () -> "재직자 20명 부여·재계산");

        List<Object[]> saves = 저장_인자(2);
        assertThat(saves.get(0)[0]).isEqualTo("leave-grant");
        assertThat(saves.get(0)[2]).isNull(); // 아직 끝나지 않음
        assertThat(saves.get(0)[3]).isNull();
        assertThat(saves.get(0)[4]).isEqualTo("실행 중");
        assertThat(saves.get(1)[2]).isInstanceOf(Timestamp.class);
        assertThat(saves.get(1)[3]).isEqualTo(true);
        assertThat(saves.get(1)[4]).isEqualTo("재직자 20명 부여·재계산");
        assertThat(saves.get(1)[1]).isEqualTo(saves.get(0)[1]); // 같은 시작 시각
    }

    @Test
    void 작업이_실패하면_실패로_남기고_예외는_그대로_던진다() {
        IllegalStateException failure = new IllegalStateException("API 장애");

        assertThatThrownBy(() -> recorder.run("holiday-sync", () -> {
            throw failure;
        })).isSameAs(failure);

        List<Object[]> saves = 저장_인자(2);
        assertThat(saves.get(1)[3]).isEqualTo(false);
        assertThat(saves.get(1)[4]).isEqualTo("실패: API 장애");
    }

    @Test
    void 건너뛰면_성공_여부를_비우고_사유를_남긴다() {
        recorder.skipped("promotion-auto", "자동 발송이 꺼져 있어 보내지 않음");

        Object[] save = 저장_인자(1).get(0);
        assertThat(save[0]).isEqualTo("promotion-auto");
        assertThat(save[2]).isEqualTo(save[1]); // 시작과 끝이 같은 시각
        assertThat(save[3]).isNull();
        assertThat(save[4]).isEqualTo("자동 발송이 꺼져 있어 보내지 않음");
    }

    @Test
    void 기록_저장에_실패해도_작업은_끝까지_실행된다() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("DB 연결 끊김"));
        AtomicBoolean ran = new AtomicBoolean();

        recorder.run("leave-grant", () -> {
            ran.set(true);
            return "완료";
        });

        assertThat(ran).isTrue();
    }

    @Test
    void 긴_결과_문구는_500자로_자른다() {
        recorder.skipped("holiday-sync", "가".repeat(600));

        assertThat((String) 저장_인자(1).get(0)[4]).hasSize(500);
    }

    @Test
    void 마지막_실행_결과를_작업별로_읽는다() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        Instant start = Instant.parse("2026-10-02T00:10:00Z");
        when(rs.getString("job")).thenReturn("holiday-sync");
        when(rs.getTimestamp("started_at")).thenReturn(Timestamp.from(start));
        when(rs.getTimestamp("finished_at")).thenReturn(null);
        when(rs.getObject("success")).thenReturn(null);
        when(rs.getString("message")).thenReturn("실행 중");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        Map<String, JobRunRecorder.Run> runs = recorder.lastRuns();

        assertThat(runs).containsOnlyKeys("holiday-sync");
        JobRunRecorder.Run run = runs.get("holiday-sync");
        assertThat(run.startedAt()).isEqualTo(start);
        assertThat(run.finishedAt()).isNull();
        assertThat(run.success()).isNull();
        assertThat(run.message()).isEqualTo("실행 중");
    }

    @Test
    void 실행_기록을_읽지_못하면_빈_결과를_돌려준다() {
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("테이블 없음"));

        assertThat(recorder.lastRuns()).isEmpty();
    }

    /** jdbc.update 에 넘긴 값들(sql 제외)을 호출 순서대로. */
    private List<Object[]> 저장_인자(int times) {
        ArgumentCaptor<Object> job = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> started = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> finished = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> success = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(times)).update(anyString(), job.capture(), started.capture(), finished.capture(),
                success.capture(), message.capture());
        List<Object[]> result = new java.util.ArrayList<>();
        for (int i = 0; i < times; i++) {
            result.add(new Object[] {job.getAllValues().get(i), started.getAllValues().get(i),
                    finished.getAllValues().get(i), success.getAllValues().get(i), message.getAllValues().get(i)});
        }
        return result;
    }
}
