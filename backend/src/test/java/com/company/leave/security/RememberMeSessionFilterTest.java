package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;

/**
 * 로그인 유지(30일): 로그인한 때부터 30일. 요청마다 남은 시간으로 만료를 맞추고, 지나면 세션을 끝낸다.
 */
@DisplayName("로그인 유지 세션")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RememberMeSessionFilterTest {

    private static final Instant LOGIN_AT = Instant.parse("2026-10-12T00:00:00Z");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("Asia/Seoul"));
    }

    /** LOGIN_AT 에 로그인 유지로 로그인한 세션 */
    private static MockHttpSession 로그인_유지_세션() {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");
        RememberMeSessionFilter.start(login, at(LOGIN_AT));
        return (MockHttpSession) login.getSession(false);
    }

    private static MockHttpServletRequest 요청(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.setRequestURI("/api/dashboard");
        request.setSession(session);
        return request;
    }

    @Test
    void 로그인하면_30일_뒤에_끝나도록_적고_쿠키가_남게_한다() {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");

        RememberMeSessionFilter.start(login, at(LOGIN_AT));

        assertThat(login.getSession(false).getAttribute(RememberMeSessionFilter.UNTIL_ATTR))
                .isEqualTo(LOGIN_AT.plus(Duration.ofDays(30)).toEpochMilli());
        assertThat(login.getSession(false).getMaxInactiveInterval()).isEqualTo(30 * 24 * 3600);
        assertThat(login.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR)).isEqualTo(true);
    }

    @Test
    void 사용할_때마다_만료를_남은_시간으로_맞춰_30일을_넘기지_않는다() throws Exception {
        MockHttpSession session = 로그인_유지_세션();
        MockHttpServletRequest request = 요청(session);
        MockFilterChain chain = new MockFilterChain();

        new RememberMeSessionFilter(at(LOGIN_AT.plus(Duration.ofDays(29))))
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(24 * 3600);
        // 세션 ID 가 바뀌어(비밀번호 변경 등) 쿠키를 다시 써도 브라우저 종료로 지워지지 않게
        assertThat(request.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR)).isEqualTo(true);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void 로그인한_지_30일이_지나면_세션을_끝내고_인증_없이_넘긴다() throws Exception {
        MockHttpSession session = 로그인_유지_세션();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, java.util.List.of()));

        new RememberMeSessionFilter(at(LOGIN_AT.plus(Duration.ofDays(30)).plusSeconds(1)))
                .doFilter(요청(session), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 로그인_유지가_아닌_세션은_건드리지_않는다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(7200);
        MockHttpServletRequest request = 요청(session);

        new RememberMeSessionFilter(at(LOGIN_AT))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(7200);
        assertThat(request.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR)).isNull();
    }
}
