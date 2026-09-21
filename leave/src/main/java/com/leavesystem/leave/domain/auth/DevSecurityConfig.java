package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.EmployeeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** 개발 환경에서만 사용하는 폼 로그인. 비밀번호는 DB에 저장하지 않는다. */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class DevSecurityConfig {

    @Bean
    PasswordEncoder devPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService devUserDetailsService(EmployeeRepository employees, PasswordEncoder encoder) {
        Map<String, String> passwords = Map.of(
                "leaveadmin@company.com", "leaveAdmin",
                "admin@company.com", "admin",
                "manager@company.com", "manager",
                "employee@company.com", "employee"
        ).entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> encoder.encode(entry.getValue())));

        return username -> {
            String email = username.trim().toLowerCase(Locale.ROOT);
            if (!email.contains("@")) {
                email += "@company.com";
            }
            String password = passwords.get(email);
            if (password == null) {
                throw new UsernameNotFoundException("등록되지 않은 개발 계정입니다.");
            }
            var employee = employees.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new UsernameNotFoundException("사원 정보가 없습니다."));

            return User.withUsername(employee.getEmail())
                    .password(password)
                    .roles(employee.getRole().name())
                    .disabled(!employee.isActive())
                    .build();
        };
    }

    @Bean
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error").permitAll()
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
