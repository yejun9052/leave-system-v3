package com.company.leave.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("비밀번호 재설정 요청 횟수 제한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResetRequestLimiterTest {

    private static final String EMAIL = "user@company.com";

    private final 조정_가능한_시계 clock = new 조정_가능한_시계(Instant.parse("2027-01-04T00:00:00Z"));
    private final ResetRequestLimiter limiter = new ResetRequestLimiter(clock);

    @Test
    void 같은_이메일은_한_시간에_3번까지_허용하고_4번째는_거부한다() {
        assertThat(limiter.tryAcquire(EMAIL)).isTrue();
        assertThat(limiter.tryAcquire(EMAIL)).isTrue();
        assertThat(limiter.tryAcquire(EMAIL)).isTrue();

        assertThat(limiter.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    void 첫_요청에서_한_시간이_지나면_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(EMAIL);
        }

        clock.advance(Duration.ofMinutes(59));
        assertThat(limiter.tryAcquire(EMAIL)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire(EMAIL)).isTrue();
    }

    @Test
    void 대소문자와_앞뒤_공백이_달라도_같은_이메일로_센다() {
        limiter.tryAcquire("User@Company.com");
        limiter.tryAcquire(" user@company.com ");
        limiter.tryAcquire("USER@COMPANY.COM");

        assertThat(limiter.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    void 다른_이메일은_따로_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(EMAIL);
        }

        assertThat(limiter.tryAcquire("other@company.com")).isTrue();
    }

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계. */
    static final class 조정_가능한_시계 extends Clock {

        private Instant now;

        조정_가능한_시계(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
