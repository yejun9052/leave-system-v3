package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인 연속 실패 잠금: 5번 실패하면 15분 잠금, 성공하면 초기화, 잠금 시간이 지나면 풀린다.
 * 이메일은 앞뒤 공백·대소문자를 무시하고 같은 사람으로 센다.
 */
@DisplayName("로그인 연속 실패 잠금")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void 처음에는_잠겨_있지_않다() {
        assertThat(service.isBlocked("user@company.com")).isFalse();
    }

    @Test
    void 네_번_실패까지는_잠기지_않고_다섯_번째에_잠긴다() {
        실패(4, "user@company.com");

        assertThat(service.isBlocked("user@company.com")).isFalse();

        service.loginFailed("user@company.com");

        assertThat(service.isBlocked("user@company.com")).isTrue();
    }

    @Test
    void 성공하면_실패_횟수가_초기화된다() {
        실패(4, "user@company.com");

        service.loginSucceeded("user@company.com");
        service.loginFailed("user@company.com");

        assertThat(service.isBlocked("user@company.com")).isFalse();
    }

    @Test
    void 잠긴_뒤에도_성공_처리하면_풀린다() {
        실패(5, "user@company.com");

        service.loginSucceeded("user@company.com");

        assertThat(service.isBlocked("user@company.com")).isFalse();
    }

    @Test
    void 이메일은_대소문자와_앞뒤_공백을_무시하고_같은_사람으로_센다() {
        service.loginFailed("User@Company.com");
        service.loginFailed(" user@company.com ");
        실패(3, "USER@COMPANY.COM");

        assertThat(service.isBlocked("user@company.com")).isTrue();
    }

    @Test
    void 다른_이메일의_실패는_서로_영향이_없다() {
        실패(5, "a@company.com");

        assertThat(service.isBlocked("b@company.com")).isFalse();
    }

    @Test
    void 잠금_시간이_지나면_풀리고_실패_기록도_지워진다() throws Exception {
        실패(5, "user@company.com");
        기록_바꾸기("user@company.com", 5, Instant.now().minusSeconds(1)); // 잠금 시간이 막 지남

        assertThat(service.isBlocked("user@company.com")).isFalse();

        service.loginFailed("user@company.com"); // 지워졌으므로 다시 1번째
        assertThat(service.isBlocked("user@company.com")).isFalse();
    }

    @Test
    void 이메일이_없어도_예외_없이_센다() {
        실패(5, null);

        assertThat(service.isBlocked(null)).isTrue();
    }

    private void 실패(int times, String email) {
        for (int i = 0; i < times; i++) {
            service.loginFailed(email);
        }
    }

    /** 잠금 만료 시각을 과거로 돌린다(시계를 주입받지 않아 내부 기록을 직접 바꾼다). */
    @SuppressWarnings("unchecked")
    private void 기록_바꾸기(String email, int count, Instant lockedUntil) throws Exception {
        Class<?> attempt = Class.forName(LoginAttemptService.class.getName() + "$Attempt");
        Constructor<?> ctor = attempt.getDeclaredConstructor(int.class, Instant.class);
        ctor.setAccessible(true);
        Map<String, Object> attempts = (Map<String, Object>) ReflectionTestUtils.getField(service, "attempts");
        attempts.put(email, ctor.newInstance(count, lockedUntil));
    }
}
