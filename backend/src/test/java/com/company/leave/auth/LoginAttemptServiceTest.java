package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인 연속 실패 차단: 접속한 PC(IP) 기준으로 10번 실패하면 15분 동안 그 IP의 로그인 시도를 막는다.
 * 시도는 비밀번호 비교 전에 실패로 먼저 세고(tryAcquire), 성공하면 그 아이디로 센 횟수만 지운다.
 * 시간이 지나면 풀린다. 계정은 잠그지 않는다.
 */
@DisplayName("로그인 연속 실패 차단(IP 기준)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.7";
    private static final String ID = "kim@company.com";

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

        service.tryAcquire(IP, ID);

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
    void 성공하면_그_아이디로_틀린_횟수를_지운다() {
        실패(9, IP);

        service.loginSucceeded(IP, ID);
        실패(9, IP);

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 다른_아이디로_틀린_횟수는_자기_계정으로_로그인해도_남는다() {
        실패(9, IP, "admin");

        service.loginSucceeded(IP, ID); // 남의 비밀번호를 9번 틀린 뒤 자기 계정으로 로그인
        service.tryAcquire(IP, "admin");

        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 여러_아이디가_섞여_있으면_성공한_아이디의_몫만_빠진다() {
        실패(5, IP, "admin");
        실패(4, IP);

        service.loginSucceeded(IP, ID);
        실패(4, IP, "admin"); // 남은 5 + 4 = 9

        assertThat(service.isBlocked(IP)).isFalse();
        service.tryAcquire(IP, "admin");
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 아이디는_앞뒤_공백과_대소문자를_무시하고_센다() {
        실패(9, IP, " Kim@Company.com");

        service.loginSucceeded(IP, "kim@company.com");
        service.tryAcquire(IP, ID);

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 열_번째_시도가_맞으면_막히지_않는다() {
        실패(9, IP);
        service.tryAcquire(IP, ID); // 10번째: 비교 전에 세므로 일단 막힘

        service.loginSucceeded(IP, ID); // 맞았으니 되돌림

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 다른_아이디로_막힌_것은_자기_계정_성공으로_풀리지_않는다() {
        실패(10, IP, "admin");

        service.loginSucceeded(IP, ID);

        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 막힌_동안의_시도는_세지_않고_처음_한_번만_기록하라고_알린다() {
        실패(10, IP);

        Optional<LoginAttemptService.Block> first = service.tryAcquire(IP, ID);
        Optional<LoginAttemptService.Block> second = service.tryAcquire(IP, "other");

        assertThat(first).hasValueSatisfying(b -> {
            assertThat(b.firstInLock()).isTrue();
            assertThat(b.remaining()).isGreaterThan(Duration.ofMinutes(14));
        });
        assertThat(second).hasValueSatisfying(b -> assertThat(b.firstInLock()).isFalse());
    }

    @Test
    void 차단이_풀린_뒤_다시_막히면_다시_처음_한_번_알린다() {
        실패(10, IP);
        service.tryAcquire(IP, ID);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        실패(10, IP);

        assertThat(service.tryAcquire(IP, ID)).hasValueSatisfying(b -> assertThat(b.firstInLock()).isTrue());
    }

    @Test
    void 한꺼번에_보내도_열_번까지만_받는다() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    if (service.tryAcquire(IP, ID).isEmpty()) {
                        accepted.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(accepted).hasValue(LoginAttemptService.MAX_ATTEMPTS);
    }

    @Test
    void 틀린_적이_없는_IP의_성공은_아무_일도_하지_않는다() {
        service.loginSucceeded(IP, ID);

        assertThat(service.isBlocked(IP)).isFalse();
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(service, "attempts")).isEmpty();
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

        service.tryAcquire(IP, ID); // 지워졌으므로 다시 1번째
        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void 마지막_실패_뒤_15분_동안_더_틀리지_않으면_실패_횟수를_잊는다() {
        실패(9, IP);
        clock.advance(Duration.ofMinutes(16));

        service.tryAcquire(IP, ID); // 예전 9번은 잊고 1번째

        assertThat(service.isBlocked(IP)).isFalse();
        실패(8, IP);
        assertThat(service.isBlocked(IP)).isFalse();
        service.tryAcquire(IP, ID);
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 짧은_간격으로_계속_틀리면_잊지_않고_센다() {
        for (int i = 0; i < 10; i++) {
            service.tryAcquire(IP, ID);
            clock.advance(Duration.ofMinutes(10));
        }

        // 10분 간격이면 매번 이어서 세므로 10번째에 막히고, 그 뒤 10분이 지나도 아직 막혀 있다
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void 기록이_많이_쌓이면_잊어도_되는_기록을_정리한다() {
        for (int i = 0; i < LoginAttemptService.CLEANUP_THRESHOLD; i++) {
            service.tryAcquire("10.1." + (i / 250) + "." + (i % 250), ID);
        }
        clock.advance(Duration.ofMinutes(16));

        service.tryAcquire(IP, ID);

        Map<?, ?> attempts = (Map<?, ?>) ReflectionTestUtils.getField(service, "attempts");
        assertThat(attempts).hasSize(1);
    }

    @Test
    void IP가_없어도_예외_없이_센다() {
        실패(10, null);

        assertThat(service.isBlocked(null)).isTrue();
    }

    private void 실패(int times, String ip) {
        실패(times, ip, ID);
    }

    private void 실패(int times, String ip, String loginId) {
        for (int i = 0; i < times; i++) {
            service.tryAcquire(ip, loginId);
        }
    }

}
