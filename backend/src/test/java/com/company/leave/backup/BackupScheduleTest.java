package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 자동 백업 실행 시각: cron 6칸(초 분 시 일 월 요일). 설명 문장·다음 실행·검사(1시간보다 자주 불가)·다른 작업과 겹침.
 */
@DisplayName("자동 백업 실행 시각(cron)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupScheduleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0); // 목요일

    private static BackupSchedule s(String cron) {
        return new BackupSchedule(cron);
    }

    @Test
    void 매일은_정한_시각에_실행한다() {
        assertThat(s("0 10 0 * * *").label()).isEqualTo("매일 00:10");
        assertThat(s("0 0 2 * * *").next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 9, 2, 0));
        assertThat(s("0 0 2,14 * * *").label()).isEqualTo("매일 02:00·14:00");
    }

    @Test
    void 요일은_이름이나_숫자로_여러_개_고를_수_있다() {
        assertThat(s("0 30 3 * * MON,THU").label()).isEqualTo("매주 월·목요일 03:30");
        assertThat(s("0 30 3 * * MON,THU").next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 12, 3, 30));
        assertThat(s("0 0 22 * * 1-5").label()).isEqualTo("매주 월~금요일 22:00");
        assertThat(s("0 0 23 * * SUN").next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 11, 23, 0));
    }

    @Test
    void 매월_날짜와_말일과_특정_달도_읽는다() {
        assertThat(s("0 0 4 1 * *").label()).isEqualTo("매월 1일 04:00");
        assertThat(s("0 0 4 1 * *").next(NOW)).isEqualTo(LocalDateTime.of(2026, 11, 1, 4, 0));
        assertThat(s("0 0 4 L * *").label()).isEqualTo("매월 말일 04:00");
        assertThat(s("0 0 4 L * *").next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 31, 4, 0));
        assertThat(s("0 0 4 1 1,7 *").label()).isEqualTo("1·7월 1일 04:00");
    }

    @Test
    void N시간마다는_시작_시각부터_N시간_간격으로_실행한다() {
        assertThat(s("0 0 0/5 * * *").label()).isEqualTo("5시간마다(0시부터, 매번 00분)");
        assertThat(s("0 0 0/5 * * *").next(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 0));
        assertThat(s("0 15 */6 * * *").label()).isEqualTo("6시간마다(0시부터, 매번 15분)");
        assertThat(s("0 30 * * * *").label()).isEqualTo("매시간 30분");
    }

    @Test
    void 앞으로의_실행_시각을_차례로_돌려준다() {
        assertThat(s("0 0 2 * * *").nextRuns(NOW, 3)).containsExactly(
                LocalDateTime.of(2026, 10, 9, 2, 0), LocalDateTime.of(2026, 10, 10, 2, 0),
                LocalDateTime.of(2026, 10, 11, 2, 0));
    }

    @Test
    void 칸_사이_공백은_하나로_정리한다() {
        assertThat(s("  0  0   2 * * * ").cron()).isEqualTo("0 0 2 * * *");
    }

    @Test
    void 저장할_수_있는_규칙이면_문제가_없다() {
        for (String ok : new String[] {"0 0 2 * * *", "0 30 3 * * MON,THU", "0 0 4 L * *", "0 0 * * * *",
                "0 0 0/6 * * *", "0 10 0 1 1 *"}) {
            assertThat(s(ok).problem(NOW)).as(ok).isNull();
        }
    }

    @Test
    void 칸_수가_틀리거나_형식이_틀리면_이유를_알려준다() {
        assertThat(s("0 2 * * *").problem(NOW)).contains("6칸");
        assertThat(s("0 0 25 * * *").problem(NOW)).isEqualTo("형식이 올바르지 않습니다. 시 칸에는 0~23만 쓸 수 있습니다(입력: 25).");
        assertThat(s("0 61 2 * * *").problem(NOW)).contains("분 칸에는 0~59만");
        assertThat(s("0 0 2 * * XYZ").problem(NOW)).isEqualTo(
                "형식이 올바르지 않습니다. 칸마다 쓸 수 있는 값을 확인해 주세요(예: 시 0~23, 요일 MON~SUN).");
        assertThat(s("").problem(NOW)).contains("6칸");
    }

    @Test
    void 한_시간보다_자주_실행하는_규칙은_막는다() {
        assertThat(s("* 0 2 * * *").problem(NOW)).contains("초는");
        assertThat(s("0/30 0 2 * * *").problem(NOW)).contains("초는");
        assertThat(s("0 */10 * * * *").problem(NOW)).contains("1시간보다 자주");
        assertThat(s("0 0,30 2 * * *").problem(NOW)).contains("1시간보다 자주");
    }

    @Test
    void 실행될_날이_없으면_막는다() {
        assertThat(s("0 0 2 30 2 *").problem(NOW)).contains("실행될 날짜가 없습니다");
    }

    @Test
    void 다른_자동_작업과_같은_시각이면_그_작업을_알려_준다() {
        assertThat(s("0 10 0 * * *").conflicts()).containsExactly("공휴일 동기화(매일 00:10)");
        assertThat(s("0 0 1 * * WED").conflicts()).containsExactly("연차 부여·소멸(매일 01:00)");
        assertThat(s("0 0 0/3 * * *").conflicts()).containsExactly("연차 촉진 자동 발송(매일 09:00)");
        assertThat(s("0 0 * * * *").conflicts())
                .containsExactly("연차 부여·소멸(매일 01:00)", "연차 촉진 자동 발송(매일 09:00)");
        assertThat(s("0 0 9 1 * *").conflicts()).containsExactly("연차 촉진 자동 발송(매일 09:00)"); // 매월 1일도 찾는다
        assertThat(s("0 0 2 * * *").conflicts()).isEmpty();
    }
}
