package com.company.leave.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 유지(30일). 로그인할 때 체크하면 그 세션은 로그인한 때부터 30일 동안 살아 있다(쓸수록 늘어나지 않음).
 * <ul>
 *   <li>로그인: {@link #start} 가 끝나는 시각을 세션에 적고, 세션 쿠키를 브라우저를 닫아도 남게 한다.</li>
 *   <li>요청마다: 남은 시간을 세션 만료 시간으로 다시 맞춘다 → 쓰지 않아도, 계속 써도 정확히 30일째에 끝난다.
 *       비밀번호 변경처럼 세션 ID 가 바뀌어 쿠키를 다시 쓸 때도 쿠키가 브라우저 종료로 지워지지 않게 한다.</li>
 * </ul>
 * 체크하지 않은 세션은 지금처럼 마지막 사용 후 spring.session.timeout(기본 2시간) + 브라우저를 닫으면 끝.
 * 퇴사·비밀번호 변경·권한 변경 때의 강제 로그아웃은 같은 세션이라 그대로 적용된다.
 *
 * <p>SecurityConfig 에서 직접 생성해 보안 체인에만 등록한다.
 */
public class RememberMeSessionFilter extends OncePerRequestFilter {

    public static final Duration VALIDITY = Duration.ofDays(30);
    /** 세션 속성: 로그인 유지가 끝나는 시각(epoch 밀리초) */
    static final String UNTIL_ATTR = "rememberMeUntil";

    private final Clock clock;

    public RememberMeSessionFilter() {
        this(Clock.systemDefaultZone());
    }

    RememberMeSessionFilter(Clock clock) {
        this.clock = clock;
    }

    /** 로그인 직후 호출: 이 세션을 지금부터 30일 동안 유지한다. */
    public static void start(HttpServletRequest request, Clock clock) {
        HttpSession session = request.getSession();
        session.setAttribute(UNTIL_ATTR, clock.millis() + VALIDITY.toMillis());
        session.setMaxInactiveInterval((int) VALIDITY.toSeconds());
        // Spring Boot 가 세션 쿠키 직렬화기에 이 속성을 등록해 두었다 → 이 응답의 세션 쿠키에 만료일(Max-Age)이 붙는다
        request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UNTIL_ATTR) instanceof Long until) {
            long remainingSeconds = Duration.between(clock.instant(), Instant.ofEpochMilli(until)).toSeconds();
            if (remainingSeconds <= 0) {
                session.invalidate();
                // 이 요청은 이미 읽어 둔 인증 정보로도 통과하지 못하게 → 401
                SecurityContextHolder.getContextHolderStrategy().clearContext();
            } else {
                session.setMaxInactiveInterval((int) remainingSeconds);
                request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, true);
            }
        }
        chain.doFilter(request, response);
    }
}
