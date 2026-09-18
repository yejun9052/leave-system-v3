package com.leavesystem.leave.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JPA Auditing 설정.
 *
 * <p>{@link com.leavesystem.leave.common.entity.BaseTimeEntity} 의
 * {@code @CreatedDate}·{@code @LastModifiedDate} 를 동작시킨다.
 *
 * <p>시각은 {@link Clock} 빈을 통해 주입하므로 테스트에서 {@link Clock#fixed} 로 고정할 수 있다.
 * 연차 부여·소멸처럼 날짜에 의존하는 로직을 검증할 때 필요하다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
