package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** employee 테이블의 단일 시스템 관리자 계정으로 세션 로그인한다. */
@Configuration(proxyBeanMethods = false)
public class DevSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(EmployeeRepository employees) {
        return username -> {
            var account = employees.findByLoginIdIgnoreCase(username.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("등록되지 않은 관리자 계정입니다."));
            if (account.getRole() != Role.SYS_ADMIN || account.getPasswordHash() == null) {
                throw new UsernameNotFoundException("등록되지 않은 관리자 계정입니다.");
            }
            return User.withUsername(account.getLoginId())
                    .password(account.getPasswordHash())
                    .roles("SYS_ADMIN")
                    .disabled(!account.isActive())
                    .build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/api/auth/csrf", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .defaultSuccessUrl("/api/auth/me", true)
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        (request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"error\":{\"status\":401,\"message\":\"로그인이 필요합니다.\"}}");
                        }, request -> request.getRequestURI().startsWith("/api/")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout"))
                .build();
    }
}
