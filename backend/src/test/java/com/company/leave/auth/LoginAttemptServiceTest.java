package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
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

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
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
    void 시간이_지나면_풀리고_실패_기록도_지워진다() throws Exception {
        실패(10, IP);
        기록_바꾸기(IP, 10, Instant.now().minusSeconds(1)); // 차단 시간이 막 지남

        assertThat(service.isBlocked(IP)).isFalse();

        service.loginFailed(IP); // 지워졌으므로 다시 1번째
        assertThat(service.isBlocked(IP)).isFalse();
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

    /** 차단 만료 시각을 과거로 돌린다(시계를 주입받지 않아 내부 기록을 직접 바꾼다). */
    @SuppressWarnings("unchecked")
    private void 기록_바꾸기(String ip, int count, Instant lockedUntil) throws Exception {
        Class<?> attempt = Class.forName(LoginAttemptService.class.getName() + "$Attempt");
        Constructor<?> ctor = attempt.getDeclaredConstructor(int.class, Instant.class);
        ctor.setAccessible(true);
        Map<String, Object> attempts = (Map<String, Object>) ReflectionTestUtils.getField(service, "attempts");
        attempts.put(ip, ctor.newInstance(count, lockedUntil));
    }
}
