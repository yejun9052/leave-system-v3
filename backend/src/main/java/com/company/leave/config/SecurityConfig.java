package com.company.leave.config;

import com.company.leave.security.AccountStateFilter;
import com.company.leave.security.CustomUserDetailsService;
import com.company.leave.security.RestAuthEntryPoints;
import com.company.leave.security.SessionTolerantSecurityContextRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/csrf",
            "/api/auth/password-reset/**",
            "/api/license",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
    };

    private final RestAuthEntryPoints authEntryPoints;
    private final CustomUserDetailsService userDetailsService;
    private final Environment environment;

    public SecurityConfig(RestAuthEntryPoints authEntryPoints,
                          CustomUserDetailsService userDetailsService,
                          Environment environment) {
        this.authEntryPoints = authEntryPoints;
        this.userDetailsService = userDetailsService;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                // SPA 방식 CSRF: XSRF-TOKEN 쿠키로 발급 → 프론트가 X-XSRF-TOKEN 헤더로 전송.
                // 저장소는 로그인/로그아웃 시 토큰 교체에도 쓰도록 빈으로 공유(spa() 의 기본 저장소와 동일 설정).
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 로그인 API 가 세션에 명시 저장하는 저장소와 동일 인스턴스로 요청마다 인증을 복원
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .headers(headers -> headers
                        // 기본(X-Frame-Options:DENY, X-Content-Type-Options:nosniff, HSTS-over-HTTPS)에 더해
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                + "script-src 'self'; "
                                + "style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data:; "
                                + "font-src 'self' data:; "
                                + "connect-src 'self'; "
                                + "object-src 'none'; "
                                + "base-uri 'self'; "
                                + "frame-ancestors 'none'"))
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "geolocation=(), microphone=(), camera=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 정적 프론트엔드(SPA)·자원은 공개
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoints.authenticationEntryPoint())
                        .accessDeniedHandler(authEntryPoints.accessDeniedHandler()))
                // 세션 인증 사용자의 재직 상태·역할을 매 요청 DB 로 재확인 (인가 판단 전)
                .addFilterBefore(new AccountStateFilter(userDetailsService, authEntryPoints),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 요청 속성 + HttpSession(=Spring Session JDBC) 저장소(Spring Security 기본 구성과 동일)에,
     * 역직렬화할 수 없는 세션은 폐기하고 미인증 처리하는 보호막을 씌운다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new SessionTolerantSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()));
    }

    /** csrf.spa() 가 쓰는 것과 같은 쿠키 저장소(XSRF-TOKEN, JS 읽기 허용). */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String origins = environment.getProperty("app.cors.allowed-origins",
                "http://localhost:5173");
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        // 프론트는 같은 출처(운영: Caddy 뒤 동일 도메인, 개발: Vite 프록시)로만 호출하므로
        // 교차 출처 쿠키 자격증명을 허용할 필요 없음 → false (CWE-942 완화)
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
