package com.company.leave.security;

import com.company.leave.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 인증 사용자의 재직 상태·역할·비밀번호 변경 필요 여부를 매 요청 DB 에서 다시 확인한다.
 * 세션에 저장된 principal 은 로그인 시점 값이므로, 퇴사·휴직·역할 변경을 즉시 반영하려면
 * 요청마다 최신 principal 로 교체해야 한다(교체 결과는 세션에 저장하지 않음).
 * <ul>
 *   <li>재직 중이 아니면 세션을 삭제하고 401</li>
 *   <li>비밀번호 변경이 필요하면 허용 목록 외 요청은 403 PASSWORD_CHANGE_REQUIRED</li>
 * </ul>
 *
 * <p>SecurityConfig 에서 직접 생성해 보안 체인에만 등록한다(서블릿 필터로 중복 등록 방지 위해 @Component 아님).
 */
public class AccountStateFilter extends OncePerRequestFilter {

    /** 비밀번호 변경 전에도 허용하는 요청: "METHOD 경로". */
    static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE = Set.of(
            // 같은 브라우저에서 다른 계정으로 다시 로그인하는 경우를 막지 않도록 허용
            "POST /api/auth/login",
            "GET /api/auth/csrf",
            "GET /api/auth/me",
            "POST /api/auth/logout",
            "PATCH /api/employees/me/password",
            "GET /api/license");

    private final CustomUserDetailsService userDetailsService;
    private final RestAuthEntryPoints authEntryPoints;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AccountStateFilter(CustomUserDetailsService userDetailsService,
                              RestAuthEntryPoints authEntryPoints) {
        this.userDetailsService = userDetailsService;
        this.authEntryPoints = authEntryPoints;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 정적 자원(SPA)은 세션 조회가 필요 없음
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Authentication current = contextHolderStrategy.getContext().getAuthentication();
        if (current == null || !(current.getPrincipal() instanceof UserPrincipal sessionPrincipal)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal fresh = loadOrNull(sessionPrincipal.getId());
        if (fresh == null || !fresh.isEnabled()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            contextHolderStrategy.clearContext();
            authEntryPoints.authenticationEntryPoint().commence(request, response,
                    new InsufficientAuthenticationException("비활성화된 계정"));
            return;
        }

        if (fresh.isPasswordChangeRequired()
                && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(request.getMethod() + " " + request.getRequestURI())) {
            authEntryPoints.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }

        UsernamePasswordAuthenticationToken refreshed = UsernamePasswordAuthenticationToken.authenticated(
                fresh, null, fresh.getAuthorities());
        refreshed.setDetails(current.getDetails());
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(refreshed);
        contextHolderStrategy.setContext(context);

        filterChain.doFilter(request, response);
    }

    private UserPrincipal loadOrNull(Long employeeId) {
        try {
            return (UserPrincipal) userDetailsService.loadById(employeeId);
        } catch (UsernameNotFoundException ex) {
            return null;
        }
    }
}
