package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인 연속 실패 차단: 접속한 PC(IP) 기준으로 10번 실패하면 15분 동안 그 IP의 로그인 시도를 막는다.
 * 성공하면 초기화, 시간이 지나면 풀린다. 계정은 잠그지 않는다.
 */
@DisplayName("로그인 연속 실패 차단(IP 기준)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.7";

    /** 테스트에서 시간을 앞으로 돌리는 시계 */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(clock);
    }

    @Test
    void 처음에는_막혀_있지_않다() {
        assertThat(service.isBlocked(IP)).isFalse();
        assertThat(service.lockRemaining(IP)).isEmpty();
    }

    @Test
    void 아홉_번_실패까지는_괜찮고_열_번째에_15분_막힌다() {
        실패(9, IP);

        assertThat(service.isBlocked(IP)).isFalse();

        service.loginFailed(IP);

        assertThat(service.isBlocked(IP)).isTrue();
        assertThat(service.lockRemaining(IP)).hasValueSatisfying(d ->
                assertThat(d).isGreaterThan(Duration.ofMinutes(14)).isLessThanOrEqualTo(Duration.ofMinutes(15)));
    }

    @Test
    void 다른_PC의_실패는_서로_영향이_없다() {
        실패(10, "10.0.0.8");

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 성공하면_실패_횟수가_초기화된다() {
        실패(9, IP);

        service.loginSucceeded(IP);
        service.loginFailed(IP);

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 막힌_뒤에도_성공_처리하면_풀린다() {
        실패(10, IP);

        service.loginSucceeded(IP);

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void IP_앞뒤_공백은_무시한다() {
        실패(5, " 10.0.0.7");
        실패(5, "10.0.0.7 ");

        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 시간이_지나면_풀리고_실패_기록도_지워진다() {
        실패(10, IP);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1)); // 차단 시간이 막 지남

        assertThat(service.isBlocked(IP)).isFalse();

        service.loginFailed(IP); // 지워졌으므로 다시 1번째
        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 마지막_실패_뒤_15분_동안_더_틀리지_않으면_실패_횟수를_잊는다() {
        실패(9, IP);
        clock.advance(Duration.ofMinutes(16));

        service.loginFailed(IP); // 예전 9번은 잊고 1번째

        assertThat(service.isBlocked(IP)).isFalse();
        실패(8, IP);
        assertThat(service.isBlocked(IP)).isFalse();
        service.loginFailed(IP);
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 짧은_간격으로_계속_틀리면_잊지_않고_센다() {
        for (int i = 0; i < 10; i++) {
            service.loginFailed(IP);
            clock.advance(Duration.ofMinutes(10));
        }

        // 10분 간격이면 매번 이어서 세므로 10번째에 막히고, 그 뒤 10분이 지나도 아직 막혀 있다
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 기록이_많이_쌓이면_잊어도_되는_기록을_정리한다() {
        for (int i = 0; i < LoginAttemptService.CLEANUP_THRESHOLD; i++) {
            service.loginFailed("10.1." + (i / 250) + "." + (i % 250));
        }
        clock.advance(Duration.ofMinutes(16));

        service.loginFailed(IP);

        Map<?, ?> attempts = (Map<?, ?>) ReflectionTestUtils.getField(service, "attempts");
        assertThat(attempts).hasSize(1);
    }

    @Test
    void IP가_없어도_예외_없이_센다() {
        실패(10, null);

        assertThat(service.isBlocked(null)).isTrue();
    }

    private void 실패(int times, String ip) {
        for (int i = 0; i < times; i++) {
            service.loginFailed(ip);
        }
    }

}
